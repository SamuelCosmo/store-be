package com.store.platform.client;

import com.store.platform.client.dto.ClientAdminRequest;
import com.store.platform.client.dto.ClientAdminResponse;
import com.store.platform.client.dto.ClientRequest;
import com.store.platform.client.dto.ClientResponse;
import com.store.platform.client.dto.ClientSettingsRequest;
import com.store.platform.client.dto.ClientSettingsResponse;
import com.store.platform.common.exception.BusinessException;
import com.store.platform.common.exception.DuplicateResourceException;
import com.store.platform.common.exception.ResourceNotFoundException;
import com.store.platform.security.AuthContext;
import com.store.platform.user.User;
import com.store.platform.user.UserRepository;
import com.store.platform.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ClientService {

    private final ClientRepository clientRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Transactional
    public ClientResponse create(ClientRequest request) {
        Client client = Client.builder()
                .name(request.name())
                .maxStores(request.maxStores())
                .maxPosPerStore(request.maxPosPerStore())
                .maxKiosksPerStore(request.maxKiosksPerStore())
                .build();
        Client saved = clientRepository.save(client);
        if (request.adminEmail() != null && !request.adminEmail().isBlank()) {
            createAdmin(saved, request);
        }
        return ClientResponse.from(saved);
    }

    /** Cuenta ADMIN del tenant — se crea junto al cliente en el mismo
     *  commit (si falla, no queda un cliente sin forma de entrar). */
    private void createAdmin(Client client, ClientRequest request) {
        if (request.adminPassword() == null || request.adminPassword().isBlank()) {
            throw new BusinessException("adminPassword required to create the client's admin");
        }
        String email = UserService.normalizeEmail(request.adminEmail());
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new DuplicateResourceException("Email already registered: " + email);
        }
        userRepository.save(User.builder()
                .client(client)
                .name(request.adminName() == null || request.adminName().isBlank()
                        ? "Administrador"
                        : request.adminName().trim())
                .email(email)
                .password(passwordEncoder.encode(request.adminPassword()))
                .role(User.Role.ADMIN)
                .build());
    }

    public ClientResponse getById(Long id) {
        return ClientResponse.from(findOrThrow(id));
    }

    /** Plataforma: todos los tenants. */
    public List<ClientResponse> list() {
        return clientRepository.findAll().stream().map(ClientResponse::from).toList();
    }

    /** Plataforma: nombre + límites del plan. */
    @Transactional
    public ClientResponse update(Long id, ClientRequest request) {
        Client client = findOrThrow(id);
        client.setName(request.name());
        client.setMaxStores(request.maxStores());
        client.setMaxPosPerStore(request.maxPosPerStore());
        client.setMaxKiosksPerStore(request.maxKiosksPerStore());
        return ClientResponse.from(clientRepository.save(client));
    }

    public ClientSettingsResponse getSettings(Long id) {
        return ClientSettingsResponse.from(findOrThrow(id));
    }

    @Transactional
    public ClientSettingsResponse updateSettings(Long id, ClientSettingsRequest request) {
        Client client = findOrThrow(id);
        if (request.currency() != null) client.setCurrency(request.currency());
        if (request.taxRate() != null) client.setTaxRate(request.taxRate());
        if (request.kioskReceiptSeconds() != null) client.setKioskReceiptSeconds(request.kioskReceiptSeconds());
        if (request.sessionHours() != null) client.setSessionHours(request.sessionHours());
        return ClientSettingsResponse.from(clientRepository.save(client));
    }

    /** Plataforma: datos del admin del tenant (para el modal de edición). */
    public ClientAdminResponse getAdmin(Long id) {
        return ClientAdminResponse.from(adminOf(id));
    }

    /** Plataforma: editar nombre/correo del admin y opcionalmente resetear
     *  su contraseña. Requiere la contraseña del superadmin logueado. */
    @Transactional
    public ClientAdminResponse updateAdmin(Long id, ClientAdminRequest request) {
        User self = userRepository.findById(AuthContext.current().userId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (!passwordEncoder.matches(request.superadminPassword(), self.getPassword())) {
            throw new BadCredentialsException("Invalid credentials");
        }
        User admin = adminOf(id);
        String email = UserService.normalizeEmail(request.adminEmail());
        if (userRepository.existsByEmailIgnoreCaseAndIdNot(email, admin.getId())) {
            throw new DuplicateResourceException("Email already registered: " + email);
        }
        admin.setName(request.adminName().trim());
        admin.setEmail(email);
        if (request.adminPassword() != null && !request.adminPassword().isBlank()) {
            admin.setPassword(passwordEncoder.encode(request.adminPassword()));
        }
        return ClientAdminResponse.from(userRepository.save(admin));
    }

    /** El admin "principal" del tenant (el primero creado). */
    private User adminOf(Long clientId) {
        return userRepository
                .findFirstByClientIdAndRoleOrderByIdAsc(clientId, User.Role.ADMIN)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Client has no admin user: " + clientId));
    }

    public Client findOrThrow(Long id) {
        return clientRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Client not found: " + id));
    }
}
