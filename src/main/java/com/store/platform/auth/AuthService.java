package com.store.platform.auth;

import com.store.platform.auth.dto.AuthResponse;
import com.store.platform.auth.dto.LoginRequest;
import com.store.platform.auth.dto.RegisterRequest;
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
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
        if (userRepository.existsByEmail(request.email())) {
            throw new DuplicateResourceException("Email already registered: " + request.email());
        }
        User user = User.builder()
                .client(client)
                .name(request.name())
                .email(request.email())
                .password(passwordEncoder.encode(request.password()))
                .role(User.Role.ADMIN)
                .build();
        userRepository.save(user);
        return toResponse(user, null);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .filter(User::isActive)
                .filter(u -> passwordEncoder.matches(request.password(), u.getPassword()))
                .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));
        Long storeId = resolveStore(user, request.storeId());
        return toResponse(user, storeId);
    }

    private Long resolveStore(User user, Long requestedStoreId) {
        if (requestedStoreId == null) {
            if (user.getStores().size() == 1) {
                return user.getStores().iterator().next().getId();
            }
            if (user.getStores().isEmpty() && user.getRole() == User.Role.ADMIN) {
                return null; // admin without stores yet — picks one after creating it
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
        return new AuthResponse(
                jwtService.generate(user, storeId),
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getRole(),
                user.getClient().getId(),
                storeId);
    }
}
