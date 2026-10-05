package com.store.platform.user;

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
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final ClientRepository clientRepository;
    private final StoreRepository storeRepository;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    /** Emails are stored lowercase — login and uniqueness are case-insensitive. */
    public static String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(java.util.Locale.ROOT);
    }

    @Transactional
    public UserResponse create(Long clientId, UserRequest request) {
        requireSelfPassword(request.currentPassword());
        return createUnchecked(clientId, request);
    }

    /** Internal bootstrap (DataSeeder) — no caller session exists yet. */
    @Transactional
    public UserResponse createUnchecked(Long clientId, UserRequest request) {
        Client client = clientRepository.findById(clientId)
                .orElseThrow(() -> new ResourceNotFoundException("Client not found: " + clientId));
        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new DuplicateResourceException("Email already registered: " + email);
        }
        User user = User.builder()
                .client(client)
                .name(request.name())
                .email(email)
                .password(passwordEncoder.encode(request.password()))
                .role(request.role())
                .stores(resolveStores(clientId, request.storeIds()))
                .build();
        return UserResponse.from(userRepository.save(user));
    }

    @Transactional(readOnly = true)
    public List<UserResponse> listByClient(Long clientId) {
        return userRepository.findByClientId(clientId).stream()
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
        User user = findOwnedOrThrow(id);
        if (request.role() == User.Role.ADMIN && user.getRole() != User.Role.ADMIN
                || user.getRole() == User.Role.ADMIN && request.role() != User.Role.ADMIN) {
            // changing admin status is allowed, but never on yourself
            if (user.getId().equals(AuthContext.current().userId())) {
                throw new BusinessException("You cannot change your own role");
            }
        }
        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmailIgnoreCaseAndIdNot(email, id)) {
            throw new DuplicateResourceException("Email already registered: " + email);
        }
        user.setName(request.name());
        user.setEmail(email);
        if (request.password() != null && !request.password().isBlank()) {
            user.setPassword(passwordEncoder.encode(request.password()));
        }
        user.setRole(request.role());
        user.setStores(resolveStores(user.getClient().getId(), request.storeIds()));
        return UserResponse.from(userRepository.save(user));
    }

    @Transactional
    public UserResponse setActive(Long id, boolean active, PasswordRequest request) {
        requireSelfPassword(request.currentPassword());
        User user = findOwnedOrThrow(id);
        if (!active && user.getId().equals(AuthContext.current().userId())) {
            throw new BusinessException("You cannot deactivate your own account");
        }
        user.setActive(active);
        return UserResponse.from(userRepository.save(user));
    }

    @Transactional
    public void delete(Long id, PasswordRequest request) {
        requireSelfPassword(request.currentPassword());
        User user = findOwnedOrThrow(id);
        if (user.getId().equals(AuthContext.current().userId())) {
            throw new BusinessException("You cannot delete your own account");
        }
        userRepository.delete(user);
    }

    /** Caller must re-enter their own password to mutate other users. */
    private void requireSelfPassword(String currentPassword) {
        User self = userRepository.findById(AuthContext.current().userId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (!passwordEncoder.matches(currentPassword, self.getPassword())) {
            throw new BadCredentialsException("Invalid credentials");
        }
    }

    /** User must belong to the caller's client. */
    private User findOwnedOrThrow(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + id));
        if (!user.getClient().getId().equals(AuthContext.current().clientId())) {
            throw new ResourceNotFoundException("User not found: " + id);
        }
        return user;
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
                throw new BusinessException("Store " + store.getId() + " does not belong to this client");
            }
        }
        return stores;
    }
}
