package com.store.platform.auth;

import com.store.platform.auth.dto.AuthResponse;
import com.store.platform.auth.dto.LoginRequest;
import com.store.platform.auth.dto.RegisterRequest;
import com.store.platform.auth.dto.StoreOption;
import com.store.platform.client.Client;
import com.store.platform.client.ClientRepository;
import com.store.platform.common.RateLimiter;
import com.store.platform.common.exception.BusinessException;
import com.store.platform.common.exception.DuplicateResourceException;
import com.store.platform.common.exception.RateLimitException;
import com.store.platform.common.exception.ResourceNotFoundException;
import com.store.platform.module.ModuleService;
import com.store.platform.security.JwtService;
import com.store.platform.store.Store;
import com.store.platform.store.StoreRepository;
import com.store.platform.user.User;
import com.store.platform.user.UserRepository;
import com.store.platform.user.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final ClientRepository clientRepository;
    private final StoreRepository storeRepository;
    private final JwtService jwtService;
    private final RateLimiter rateLimiter;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    // fuerza bruta: 5 fallos por correo o 30 por IP en 10 min → 429
    private static final int MAX_FAILS_EMAIL = 5;
    private static final int MAX_FAILS_IP = 30;
    private static final long LOGIN_WINDOW_MS = 10 * 60_000;

    /** Self-serve onboarding: creates the first ADMIN of a client. Further users via POST /api/users. */
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        Client client = clientRepository.findById(request.clientId())
                .orElseThrow(() -> new ResourceNotFoundException("Client not found: " + request.clientId()));
        if (!client.isActive()) {
            throw new BusinessException("CLIENT_SUSPENDED", "Client account is suspended");
        }
        if (userRepository.existsByClientIdAndDeletedAtIsNull(client.getId())) {
            throw new BusinessException("CLIENT_HAS_USERS", "Client already has users — ask your ADMIN to create your account");
        }
        String email = UserService.normalizeEmail(request.email());
        if (userRepository.existsByEmailIgnoreCaseAndDeletedAtIsNull(email)) {
            throw new DuplicateResourceException("EMAIL_TAKEN", "Email already registered: " + email,
                    java.util.Map.of("email", email));
        }
        User user = User.builder()
                .client(client)
                .name(request.name())
                .email(email)
                .password(passwordEncoder.encode(request.password()))
                .role(User.Role.ADMIN)
                .build();
        userRepository.save(user);
        return toResponse(user, null);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request, String ip) {
        User user = authenticate(request, ip);
        // La cuenta de plataforma no entra por el login de tenants — mismo
        // error genérico para no filtrar qué correo es superadmin
        if (user.getRole() == User.Role.SUPERADMIN) {
            log.warn("Login rejected: superadmin on tenant login userId={}", user.getId());
            throw new BadCredentialsException("Invalid credentials");
        }
        requireActiveClient(user);
        Long storeId = resolveStore(user, request.storeId());
        log.info("Login ok userId={} role={} clientId={} storeId={}",
                user.getId(), user.getRole(), user.getClient().getId(), storeId);
        return toResponse(user, storeId);
    }

    /** Solo cuentas de plataforma — el front lo usa en /superadmin. */
    @Transactional(readOnly = true)
    public AuthResponse loginSuperadmin(LoginRequest request, String ip) {
        User user = authenticate(request, ip);
        // cuenta de plataforma = rol SUPERADMIN *y* sin tenant (inyectada en DB)
        if (user.getRole() != User.Role.SUPERADMIN || user.getClient() != null) {
            log.warn("Superadmin login rejected: not superadmin userId={}", user.getId());
            throw new BadCredentialsException("Invalid credentials");
        }
        log.info("Superadmin login ok userId={}", user.getId());
        return toResponse(user, null);
    }

    private User authenticate(LoginRequest request, String ip) {
        String email = request.email().trim();
        String emailKey = "login:" + email.toLowerCase(Locale.ROOT);
        String ipKey = "login:ip:" + ip;
        if (rateLimiter.blocked(emailKey, MAX_FAILS_EMAIL)
                || rateLimiter.blocked(ipKey, MAX_FAILS_IP)) {
            log.warn("Login rejected: rate limited email={} ip={}", email, ip);
            throw new RateLimitException();
        }
        var user = userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(email);
        if (user.isEmpty()) {
            log.warn("Login failed: unknown email={}", email);
        } else if (!user.get().isActive()) {
            log.warn("Login failed: inactive userId={}", user.get().getId());
        } else if (!passwordEncoder.matches(request.password(), user.get().getPassword())) {
            log.warn("Login failed: wrong password userId={}", user.get().getId());
        } else {
            rateLimiter.reset(emailKey);
            return user.get();
        }
        rateLimiter.hit(emailKey, LOGIN_WINDOW_MS);
        rateLimiter.hit(ipKey, LOGIN_WINDOW_MS);
        throw new BadCredentialsException("Invalid credentials");
    }

    /** Tiendas elegibles de la cuenta — el front las pide cuando login
     *  responde "storeId required" para mostrar el selector. */
    @Transactional(readOnly = true)
    public List<StoreOption> storesForLogin(LoginRequest request, String ip) {
        User user = authenticate(request, ip);
        requireActiveClient(user);
        return user.getStores().stream()
                .filter(s -> s.getDeletedAt() == null)
                .map(StoreOption::from)
                .toList();
    }

    private void requireActiveClient(User user) {
        if (user.getClient() != null && !user.getClient().isActive()) {
            log.warn("Login rejected: client suspended userId={} clientId={}",
                    user.getId(), user.getClient().getId());
            throw new BusinessException("CLIENT_SUSPENDED", "Client account is suspended");
        }
    }

    private Long resolveStore(User user, Long requestedStoreId) {
        if (requestedStoreId == null) {
            var liveStores = user.getStores().stream()
                    .filter(s -> s.getDeletedAt() == null).toList();
            // ADMIN opera todo el cliente desde el panel — no elige tienda en
            // el login; con 1 asignada se resuelve sola como siempre
            if (user.getRole() == User.Role.ADMIN) {
                return liveStores.size() == 1 ? liveStores.get(0).getId() : null;
            }
            if (liveStores.size() == 1) {
                return liveStores.get(0).getId();
            }
            throw new BusinessException("STORE_REQUIRED", "storeId required — your account operates multiple stores");
        }
        Store store = storeRepository.findById(requestedStoreId)
                .filter(s -> s.getDeletedAt() == null)
                .orElseThrow(() -> new ResourceNotFoundException("Store not found: " + requestedStoreId));
        if (!store.getClient().getId().equals(user.getClient().getId())) {
            throw new AccessDeniedException("Store does not belong to your client");
        }
        boolean assigned = user.getRole() == User.Role.ADMIN
                || user.getStores().stream()
                        .anyMatch(s -> s.getId().equals(requestedStoreId) && s.getDeletedAt() == null);
        if (!assigned) {
            throw new AccessDeniedException("You are not assigned to this store");
        }
        return requestedStoreId;
    }

    private AuthResponse toResponse(User user, Long storeId) {
        Client client = user.getClient();
        return new AuthResponse(
                jwtService.generate(user, storeId),
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getRole(),
                client == null ? null : client.getId(),
                client == null ? null : client.getName(),
                client == null ? null : client.getLogoUrl(),
                storeId,
                ModuleService.names(ModuleService.effectiveModules(user)));
    }
}
