package com.store.platform.client;

import com.store.platform.audit.AuditService;
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
import com.store.platform.storage.StorageService;
import com.store.platform.user.User;
import com.store.platform.user.UserRepository;
import com.store.platform.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ClientService {

    private final ClientRepository clientRepository;
    private final UserRepository userRepository;
    private final StorageService storageService;
    private final AuditService auditService;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    private static Map<String, Object> planSnapshot(Client c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", c.getName());
        m.put("maxStores", c.getMaxStores());
        m.put("maxPosPerStore", c.getMaxPosPerStore());
        m.put("maxKiosksPerStore", c.getMaxKiosksPerStore());
        return m;
    }

    private static Map<String, Object> settingsSnapshot(Client c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", c.getName());
        m.put("currency", c.getCurrency());
        m.put("taxRate", c.getTaxRate());
        m.put("tokenPrice", c.getTokenPrice());
        m.put("kioskReceiptSeconds", c.getKioskReceiptSeconds());
        m.put("timezone", c.getTimezone());
        m.put("sessionHours", c.getSessionHours());
        return m;
    }

    @Transactional
    public ClientResponse create(ClientRequest request) {
        Client client = Client.builder()
                .name(request.name())
                .maxStores(request.maxStores())
                .maxPosPerStore(request.maxPosPerStore())
                .maxKiosksPerStore(request.maxKiosksPerStore())
                .modules(java.util.EnumSet.allOf(com.store.platform.module.Module.class))
                .build();
        Client saved = clientRepository.save(client);
        if (request.adminEmail() != null && !request.adminEmail().isBlank()) {
            createAdmin(saved, request);
        }
        auditService.recordFor(saved.getId(), null, "client", "create",
                saved.getId(), saved.getName(), planSnapshot(saved));
        return ClientResponse.from(saved);
    }

    /** Cuenta ADMIN del tenant — se crea junto al cliente en el mismo
     *  commit (si falla, no queda un cliente sin forma de entrar). */
    private void createAdmin(Client client, ClientRequest request) {
        if (request.adminPassword() == null || request.adminPassword().isBlank()) {
            throw new BusinessException("ADMIN_PASSWORD_REQUIRED", "adminPassword required to create the client's admin");
        }
        String email = UserService.normalizeEmail(request.adminEmail());
        if (userRepository.existsByEmailIgnoreCaseAndDeletedAtIsNull(email)) {
            throw new DuplicateResourceException("EMAIL_TAKEN", "Email already registered: " + email,
                    java.util.Map.of("email", email));
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

    @Transactional(readOnly = true)
    public ClientResponse getById(Long id) {
        return ClientResponse.from(findOrThrow(id));
    }

    /** Plataforma: todos los tenants. */
    @Transactional(readOnly = true)
    public List<ClientResponse> list() {
        return clientRepository.findAll().stream().map(ClientResponse::from).toList();
    }

    /** Plataforma: nombre + límites del plan. */
    @Transactional
    public ClientResponse update(Long id, ClientRequest request) {
        Client client = findOrThrow(id);
        Map<String, Object> before = planSnapshot(client);
        client.setName(request.name());
        client.setMaxStores(request.maxStores());
        client.setMaxPosPerStore(request.maxPosPerStore());
        client.setMaxKiosksPerStore(request.maxKiosksPerStore());
        Client saved = clientRepository.save(client);
        auditService.recordFor(id, null, "client", "update",
                saved.getId(), saved.getName(), AuditService.diff(before, planSnapshot(saved)));
        return ClientResponse.from(saved);
    }

    public ClientSettingsResponse getSettings(Long id) {
        return ClientSettingsResponse.from(findOrThrow(id));
    }

    @Transactional
    public ClientSettingsResponse updateSettings(Long id, ClientSettingsRequest request) {
        Client client = findOrThrow(id);
        Map<String, Object> before = settingsSnapshot(client);
        if (request.name() != null && !request.name().isBlank()) {
            client.setName(request.name().trim());
        }
        if (request.logoUrl() != null) {
            String old = client.getLogoUrl();
            String next = request.logoUrl().isBlank() ? null : request.logoUrl();
            client.setLogoUrl(next);
            // el logo reemplazado/quitrado queda huérfano en el bucket
            if (old != null && !old.equals(next)) storageService.deleteByUrl(old);
        }
        if (request.currency() != null) {
            // una moneda que Intl.NumberFormat no conoce tumba el POS completo
            try {
                java.util.Currency.getInstance(request.currency());
            } catch (IllegalArgumentException e) {
                throw new BusinessException("INVALID_CURRENCY", "Unknown currency: " + request.currency(),
                        java.util.Map.of("currency", request.currency()));
            }
            client.setCurrency(request.currency());
        }
        if (request.taxRate() != null) client.setTaxRate(request.taxRate());
        if (request.tokenPrice() != null) client.setTokenPrice(request.tokenPrice());
        if (request.kioskReceiptSeconds() != null) client.setKioskReceiptSeconds(request.kioskReceiptSeconds());
        if (request.sessionHours() != null) {
            request.sessionHours().forEach((role, hours) -> {
                if (!TENANT_ROLES.contains(role) || hours == null) {
                    throw new BusinessException("INVALID_SESSION_ROLE", "Invalid session hours for role: " + role);
                }
            });
            client.setSessionHours(request.sessionHours());
        }
        if (request.timezone() != null) {
            try {
                java.time.ZoneId.of(request.timezone());
            } catch (java.time.DateTimeException e) {
                throw new BusinessException("INVALID_TIMEZONE", "Unknown timezone: " + request.timezone());
            }
            client.setTimezone(request.timezone());
        }
        Client saved = clientRepository.save(client);
        auditService.recordFor(id, null, "client", "settings",
                saved.getId(), saved.getName(), AuditService.diff(before, settingsSnapshot(saved)));
        return ClientSettingsResponse.from(saved);
    }

    private static final java.util.Set<String> TENANT_ROLES =
            java.util.Set.of("ADMIN", "MANAGER", "EMPLOYEE", "KITCHEN", "CUSTOMER");

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
        if (userRepository.existsByEmailIgnoreCaseAndIdNotAndDeletedAtIsNull(email, admin.getId())) {
            throw new DuplicateResourceException("EMAIL_TAKEN", "Email already registered: " + email,
                    java.util.Map.of("email", email));
        }
        Map<String, Object> before = new LinkedHashMap<>();
        before.put("name", admin.getName());
        before.put("email", admin.getEmail());
        admin.setName(request.adminName().trim());
        admin.setEmail(email);
        boolean passwordChanged = request.adminPassword() != null && !request.adminPassword().isBlank();
        if (passwordChanged) {
            admin.setPassword(passwordEncoder.encode(request.adminPassword()));
            admin.revokeTokens();
        }
        User saved = userRepository.save(admin);
        Map<String, Object> changes = AuditService.diff(before, Map.of(
                "name", saved.getName(), "email", saved.getEmail()));
        changes.put("passwordReset", passwordChanged);
        auditService.recordFor(id, null, "user", "update",
                saved.getId(), saved.getName(), changes);
        return ClientAdminResponse.from(saved);
    }

    /** El admin "principal" del tenant (el primero creado). */
    private User adminOf(Long clientId) {
        return userRepository
                .findFirstByClientIdAndRoleAndDeletedAtIsNullOrderByIdAsc(clientId, User.Role.ADMIN)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Client has no admin user: " + clientId));
    }

    public Client findOrThrow(Long id) {
        return clientRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Client not found: " + id));
    }
}
