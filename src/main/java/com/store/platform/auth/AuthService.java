package com.store.platform.auth;

import com.store.platform.auth.dto.AuthResponse;
import com.store.platform.auth.dto.LoginRequest;
import com.store.platform.auth.dto.RegisterRequest;
import com.store.platform.auth.dto.StoreOption;
import com.store.platform.client.Client;
import com.store.platform.client.ClientRepository;
import com.store.platform.common.exception.BusinessException;
import com.store.platform.common.exception.DuplicateResourceException;
import com.store.platform.common.exception.ResourceNotFoundException;
import com.store.platform.security.JwtService;
import com.store.platform.store.Store;
import com.store.platform.store.StoreRepository;
import com.store.platform.user.User;
import com.store.platform.user.UserRepository;
import com.store.platform.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final ClientRepository clientRepository;
    private final StoreRepository storeRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    /** Self-serve onboarding: creates the first ADMIN of a client. Further users via POST /api/users. */
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        Client client = clientRepository.findById(request.clientId())
                .orElseThrow(() -> new ResourceNotFoundException("Client not found: " + request.clientId()));
        if (userRepository.existsByClientId(client.getId())) {
            throw new BusinessException("Client already has users — ask your ADMIN to create your account");
        }
        String email = UserService.normalizeEmail(request.email());
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new DuplicateResourceException("Email already registered: " + email);
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
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmailIgnoreCase(request.email().trim())
                .filter(User::isActive)
                .filter(u -> passwordEncoder.matches(request.password(), u.getPassword()))
                .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));
        // SUPERADMIN: cuenta de plataforma — sin tienda ni tenant
        Long storeId = user.getRole() == User.Role.SUPERADMIN
                ? null
                : resolveStore(user, request.storeId());
        return toResponse(user, storeId);
    }

    /** Tiendas elegibles de la cuenta — el front las pide cuando login
     *  responde "storeId required" para mostrar el selector. */
    @Transactional(readOnly = true)
    public List<StoreOption> storesForLogin(LoginRequest request) {
        User user = userRepository.findByEmailIgnoreCase(request.email().trim())
                .filter(User::isActive)
                .filter(u -> passwordEncoder.matches(request.password(), u.getPassword()))
                .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));
        return user.getStores().stream()
                .map(s -> new StoreOption(s.getId(), s.getName()))
                .toList();
    }

    private Long resolveStore(User user, Long requestedStoreId) {
        if (requestedStoreId == null) {
            // ADMIN opera todo el cliente desde el panel — no elige tienda en
            // el login; con 1 asignada se resuelve sola como siempre
            if (user.getRole() == User.Role.ADMIN) {
                return user.getStores().size() == 1
                        ? user.getStores().iterator().next().getId()
                        : null;
            }
            if (user.getStores().size() == 1) {
                return user.getStores().iterator().next().getId();
            }
            throw new BusinessException("storeId required — your account operates multiple stores");
        }
        Store store = storeRepository.findById(requestedStoreId)
                .orElseThrow(() -> new ResourceNotFoundException("Store not found: " + requestedStoreId));
        if (!store.getClient().getId().equals(user.getClient().getId())) {
            throw new AccessDeniedException("Store does not belong to your client");
        }
        boolean assigned = user.getRole() == User.Role.ADMIN
                || user.getStores().stream().anyMatch(s -> s.getId().equals(requestedStoreId));
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
                storeId);
    }
}
