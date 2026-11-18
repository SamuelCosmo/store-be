package com.store.platform.user;

import com.store.platform.audit.AuditService;
import com.store.platform.auth.dto.StoreOption;
import com.store.platform.client.Client;
import com.store.platform.client.ClientRepository;
import com.store.platform.common.exception.BusinessException;
import com.store.platform.common.exception.DuplicateResourceException;
import com.store.platform.common.exception.ResourceNotFoundException;
import com.store.platform.security.AuthContext;
import com.store.platform.store.Store;
import com.store.platform.store.StoreRepository;
import com.store.platform.user.dto.PasswordRequest;
import com.store.platform.user.dto.UserRequest;
import com.store.platform.user.dto.UserResponse;
import com.store.platform.user.dto.UserUpdateRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.store.platform.security.AuthUser;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final ClientRepository clientRepository;
    private final StoreRepository storeRepository;
    private final AuditService auditService;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    /** Emails are stored lowercase — login and uniqueness are case-insensitive. */
    public static String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private static Map<String, Object> snapshot(User u) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", u.getName());
        m.put("email", u.getEmail());
        m.put("role", u.getRole().name());
        m.put("active", u.isActive());
        m.put("storeIds", u.getStores().stream().map(Store::getId).sorted().toList());
        return m;
    }

    @Transactional
    public UserResponse create(Long clientId, UserRequest request) {
        requireSelfPassword(request.currentPassword());
        rejectPlatformRole(request.role());
        Client client = clientRepository.findById(clientId)
                .orElseThrow(() -> new ResourceNotFoundException("Client not found: " + clientId));
        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmailIgnoreCaseAndDeletedAtIsNull(email)) {
            throw new DuplicateResourceException("EMAIL_TAKEN", "Email already registered: " + email,
                    java.util.Map.of("email", email));
        }
        User user = User.builder()
                .client(client)
                .name(request.name())
                .email(email)
                .password(passwordEncoder.encode(request.password()))
                .role(request.role())
                .stores(resolveStores(clientId, request.storeIds()))
                .build();
        User saved = userRepository.save(user);
        auditService.recordFor(clientId, null, "user", "create",
                saved.getId(), saved.getName(), snapshot(saved));
        return UserResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<UserResponse> listByClient(Long clientId) {
        return userRepository.findByClientIdAndDeletedAtIsNull(clientId).stream()
                .map(UserResponse::from)
                .toList();
    }

    /** Versión paginada para la tabla del admin. */
    @Transactional(readOnly = true)
    public Page<UserResponse> pageByClient(Long clientId, String search, Pageable pageable) {
        String q = search == null ? "" : search.trim();
        return userRepository.pageByClient(clientId, q, pageable).map(UserResponse::from);
    }

    @Transactional
    public UserResponse update(Long id, UserUpdateRequest request) {
        requireSelfPassword(request.currentPassword());
        rejectPlatformRole(request.role());
        User user = findOwnedOrThrow(id);
        if (request.role() == User.Role.ADMIN && user.getRole() != User.Role.ADMIN
                || user.getRole() == User.Role.ADMIN && request.role() != User.Role.ADMIN) {
            // changing admin status is allowed, but never on yourself
            if (user.getId().equals(AuthContext.current().userId())) {
                throw new BusinessException("SELF_ROLE_CHANGE", "You cannot change your own role");
            }
        }
        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmailIgnoreCaseAndIdNotAndDeletedAtIsNull(email, id)) {
            throw new DuplicateResourceException("EMAIL_TAKEN", "Email already registered: " + email,
                    java.util.Map.of("email", email));
        }
        Set<Store> stores = resolveStores(user.getClient().getId(), request.storeIds());
        boolean newPassword = request.password() != null && !request.password().isBlank();
        Map<String, Object> before = snapshot(user);
        // permisos cambiados → los tokens emitidos ya no los reflejan
        if (newPassword || user.getRole() != request.role() || !storeIds(user.getStores()).equals(storeIds(stores))) {
            user.revokeTokens();
        }
        user.setName(request.name());
        user.setEmail(email);
        if (newPassword) {
            user.setPassword(passwordEncoder.encode(request.password()));
        }
        user.setRole(request.role());
        user.getStores().clear();
        user.getStores().addAll(stores);
        User saved = userRepository.save(user);
        Map<String, Object> changes = AuditService.diff(before, snapshot(saved));
        // el password nunca va al audit — solo el hecho de que cambió
        if (newPassword) changes.put("passwordChanged", true);
        auditService.recordFor(user.getClient().getId(), null, "user", "update",
                saved.getId(), saved.getName(), changes);
        return UserResponse.from(saved);
    }

    @Transactional
    public UserResponse setActive(Long id, boolean active, PasswordRequest request) {
        requireSelfPassword(request.currentPassword());
        User user = findOwnedOrThrow(id);
        if (!active && user.getId().equals(AuthContext.current().userId())) {
            throw new BusinessException("SELF_DEACTIVATE", "You cannot deactivate your own account");
        }
        if (!active) user.revokeTokens();
        user.setActive(active);
        User saved = userRepository.save(user);
        auditService.recordFor(user.getClient().getId(), null, "user",
                active ? "activate" : "deactivate", saved.getId(), saved.getName(), null);
        return UserResponse.from(saved);
    }

    @Transactional
    public void delete(Long id, PasswordRequest request) {
        requireSelfPassword(request.currentPassword());
        User user = findOwnedOrThrow(id);
        if (user.getId().equals(AuthContext.current().userId())) {
            throw new BusinessException("SELF_DELETE", "You cannot delete your own account");
        }
        Long clientId = user.getClient().getId();
        String name = user.getName();
        user.setDeletedAt(Instant.now());
        user.revokeTokens();
        // email es UNIQUE a nivel DB: se reescribe para liberar el correo y que
        // pueda registrarse de nuevo (la fila queda para el historial de órdenes)
        user.setEmail(user.getEmail() + ".deleted-" + user.getId());
        userRepository.save(user);
        auditService.recordFor(clientId, null, "user", "delete", id, name, null);
    }

    @Transactional(readOnly = true)
    public List<UserResponse> listDeleted(Long clientId) {
        return userRepository.findByClientIdAndDeletedAtIsNotNullOrderByName(clientId).stream()
                .map(UserResponse::from)
                .toList();
    }

    /** Reversa de {@link #delete}: recupera el email original (sufijo
     *  `.deleted-{id}`) y falla 409 si alguien ya lo registró de nuevo. */
    @Transactional
    public UserResponse restore(Long id) {
        User user = userRepository.findById(id)
                .filter(u -> u.getDeletedAt() != null)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + id));
        if (!user.getClient().getId().equals(AuthContext.current().clientId())) {
            throw new ResourceNotFoundException("User not found: " + id);
        }
        String suffix = ".deleted-" + user.getId();
        String email = user.getEmail().endsWith(suffix)
                ? user.getEmail().substring(0, user.getEmail().length() - suffix.length())
                : user.getEmail();
        if (userRepository.existsByEmailIgnoreCaseAndDeletedAtIsNull(email)) {
            throw new DuplicateResourceException("EMAIL_TAKEN", "Email already registered: " + email,
                    java.util.Map.of("email", email));
        }
        user.setEmail(email);
        user.setDeletedAt(null);
        User saved = userRepository.save(user);
        auditService.recordFor(saved.getClient().getId(), null, "user", "restore",
                saved.getId(), saved.getName(), null);
        return UserResponse.from(saved);
    }

    /** Tiendas que el usuario puede ver/operar: ADMIN → todas las del
     *  cliente; el resto → sus asignadas (user_stores) no eliminadas. */
    @Transactional(readOnly = true)
    public List<StoreOption> viewableStores() {
        AuthUser auth = AuthContext.current();
        if (auth.role() == User.Role.ADMIN) {
            return storeRepository.findByClientIdAndDeletedAtIsNull(auth.clientId()).stream()
                    .map(StoreOption::from)
                    .toList();
        }
        User user = userRepository.findById(auth.userId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        return user.getStores().stream()
                .filter(s -> s.getDeletedAt() == null)
                .map(StoreOption::from)
                .toList();
    }

    /** Caller must re-enter their own password to mutate other users. */
    private void requireSelfPassword(String currentPassword) {
        User self = userRepository.findById(AuthContext.current().userId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (!passwordEncoder.matches(currentPassword, self.getPassword())) {
            throw new BadCredentialsException("Invalid credentials");
        }
    }

    /** SUPERADMIN nunca se crea ni se asigna por API — solo directo en la DB
     *  (users.client_id NULL). Un tenant que lo asignara tomaría la plataforma. */
    private static void rejectPlatformRole(User.Role role) {
        if (role == User.Role.SUPERADMIN) {
            throw new AccessDeniedException("SUPERADMIN role cannot be assigned");
        }
    }

    /** User must belong to the caller's client. */
    private User findOwnedOrThrow(Long id) {
        User user = userRepository.findById(id)
                .filter(u -> u.getDeletedAt() == null)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + id));
        if (!user.getClient().getId().equals(AuthContext.current().clientId())) {
            throw new ResourceNotFoundException("User not found: " + id);
        }
        return user;
    }

    private static Set<Long> storeIds(Set<Store> stores) {
        return stores.stream().map(Store::getId).collect(java.util.stream.Collectors.toSet());
    }

    private Set<Store> resolveStores(Long clientId, List<Long> storeIds) {
        if (storeIds == null || storeIds.isEmpty()) {
            return new HashSet<>();
        }
        Set<Store> stores = new HashSet<>(storeRepository.findAllById(storeIds));
        if (stores.size() != storeIds.size()) {
            throw new ResourceNotFoundException("One or more stores not found");
        }
        for (Store store : stores) {
            if (!store.getClient().getId().equals(clientId)) {
                throw new BusinessException("STORE_OTHER_CLIENT", "Store " + store.getId() + " does not belong to this client");
            }
        }
        return stores;
    }
}
