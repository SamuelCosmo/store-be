package com.store.platform.user;

import com.store.platform.client.Client;
import com.store.platform.client.ClientRepository;
import com.store.platform.common.exception.BusinessException;
import com.store.platform.common.exception.DuplicateResourceException;
import com.store.platform.common.exception.ResourceNotFoundException;
import com.store.platform.store.Store;
import com.store.platform.store.StoreRepository;
import com.store.platform.user.dto.UserRequest;
import com.store.platform.user.dto.UserResponse;
import lombok.RequiredArgsConstructor;
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

    @Transactional
    public UserResponse create(Long clientId, UserRequest request) {
        Client client = clientRepository.findById(clientId)
                .orElseThrow(() -> new ResourceNotFoundException("Client not found: " + clientId));
        if (userRepository.existsByEmail(request.email())) {
            throw new DuplicateResourceException("Email already registered: " + request.email());
        }
        User user = User.builder()
                .client(client)
                .name(request.name())
                .email(request.email())
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
