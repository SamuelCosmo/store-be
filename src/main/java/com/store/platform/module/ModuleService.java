package com.store.platform.module;

import com.store.platform.client.Client;
import com.store.platform.audit.AuditService;
import com.store.platform.client.ClientRepository;
import com.store.platform.common.exception.BusinessException;
import com.store.platform.common.exception.ResourceNotFoundException;
import com.store.platform.user.User;
import com.store.platform.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/** Módulos efectivos por request — se resuelven en JwtAuthFilter (frescos,
 *  sin claims stale en el JWT). Reglas (docs/13):
 *  SUPERADMIN → todos (plataforma); ADMIN → los del cliente;
 *  demás roles → user_modules ∩ client_modules.
 *
 *  Convención legacy: una colección VACÍA significa "nunca configurado"
 *  (filas anteriores a los módulos) → equivale a todo el catálogo. Por eso
 *  los setters rechazan conjuntos vacíos: el vacío explícito no existe y no
 *  hay ambigüedad que resolver con backfill. */
@Service
@RequiredArgsConstructor
public class ModuleService {

    private final ClientRepository clientRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;

    /** Módulos que el cliente tiene contratados — colección vacía (legacy)
     *  se interpreta como catálogo completo. */
    public static Set<Module> purchasedModules(Client client) {
        Set<Module> modules = client.getModules();
        return modules.isEmpty() ? EnumSet.allOf(Module.class) : modules;
    }

    /** Módulos efectivos del usuario para guards y respuestas. */
    public static Set<Module> effectiveModules(User user) {
        if (user.getRole() == User.Role.SUPERADMIN) {
            return EnumSet.allOf(Module.class);
        }
        Client client = user.getClient();
        if (client == null) {
            return Set.of();
        }
        Set<Module> purchased = purchasedModules(client);
        if (user.getRole() == User.Role.ADMIN) {
            return purchased;
        }
        // legacy: usuario sin asignación explícita → todos los del cliente
        if (user.getModules().isEmpty()) {
            return purchased;
        }
        Set<Module> result = EnumSet.noneOf(Module.class);
        for (Module m : user.getModules()) {
            if (purchased.contains(m)) {
                result.add(m);
            }
        }
        return result;
    }

    /** Módulos contratados por el cliente — lo que el ADMIN puede asignar. */
    @Transactional(readOnly = true)
    public Set<Module> clientModules(Long clientId) {
        Client client = clientRepository.findById(clientId)
                .orElseThrow(() -> new ResourceNotFoundException("Client not found: " + clientId));
        return purchasedModules(client);
    }

    /** SUPERADMIN fija los módulos contratados del tenant. */
    @Transactional
    public Set<Module> setClientModules(Long clientId, Set<Module> modules) {
        Client client = clientRepository.findById(clientId)
                .orElseThrow(() -> new ResourceNotFoundException("Client not found: " + clientId));
        requireNonEmpty(modules);
        client.setModules(EnumSet.copyOf(modules));
        clientRepository.save(client);
        auditService.recordFor(clientId, null, "client", "modules",
                clientId, client.getName(), java.util.Map.of("modules", names(client.getModules())));
        return client.getModules();
    }

    /** ADMIN asigna módulos a un usuario de su tenant — siempre ⊆ client_modules. */
    @Transactional
    public Set<Module> setUserModules(Long userId, Set<Module> requested, Long actorClientId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
        if (user.getClient() == null || !user.getClient().getId().equals(actorClientId)) {
            throw new ResourceNotFoundException("User not found: " + userId);
        }
        Set<Module> purchased = purchasedModules(user.getClient());
        requireNonEmpty(requested);
        Set<Module> modules = EnumSet.copyOf(requested);
        for (Module m : modules) {
            if (!purchased.contains(m)) {
                throw new BusinessException("MODULE_NOT_PURCHASED",
                        "Client has not purchased module " + m);
            }
        }
        user.setModules(modules);
        userRepository.save(user);
        auditService.recordFor(actorClientId, null, "user", "modules",
                userId, user.getName(), java.util.Map.of("modules", names(user.getModules())));
        return user.getModules();
    }

    private static void requireNonEmpty(Set<Module> modules) {
        if (modules == null || modules.isEmpty()) {
            throw new BusinessException("MODULES_REQUIRED", "Select at least one module");
        }
    }

    /** Nombres ordenados para DTOs — estable en respuestas y tests. */
    public static List<String> names(Set<Module> modules) {
        return modules.stream().map(Enum::name).sorted().toList();
    }
}
