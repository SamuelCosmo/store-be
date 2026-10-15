package com.store.platform.client;

import com.store.platform.client.dto.ClientAdminRequest;
import com.store.platform.client.dto.ClientAdminResponse;
import com.store.platform.client.dto.ClientRequest;
import com.store.platform.client.dto.ClientResponse;
import com.store.platform.client.dto.ClientSettingsRequest;
import com.store.platform.client.dto.ClientSettingsResponse;
import com.store.platform.security.AuthContext;
import com.store.platform.store.StoreService;
import com.store.platform.store.dto.StoreResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/clients")
@RequiredArgsConstructor
public class ClientController {

    private final ClientService clientService;
    private final StoreService storeService;

    /** Plataforma (superadmin): alta de tenant — con admin opcional en el request. */
    @PostMapping
    @PreAuthorize("hasRole('SUPERADMIN')")
    public ResponseEntity<ClientResponse> create(@Valid @RequestBody ClientRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(clientService.create(request));
    }

    /** Plataforma (superadmin): lista todos los tenants. */
    @GetMapping
    @PreAuthorize("hasRole('SUPERADMIN')")
    public List<ClientResponse> list() {
        return clientService.list();
    }

    /** Plataforma (superadmin): edita nombre + límites del plan. */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('SUPERADMIN')")
    public ClientResponse update(@PathVariable Long id, @Valid @RequestBody ClientRequest request) {
        return clientService.update(id, request);
    }

    /** Plataforma (superadmin): datos del admin del tenant — prefill del modal. */
    @GetMapping("/{id}/admin")
    @PreAuthorize("hasRole('SUPERADMIN')")
    public ClientAdminResponse getAdmin(@PathVariable Long id) {
        return clientService.getAdmin(id);
    }

    /** Plataforma (superadmin): edita el admin del tenant — requiere la
     *  contraseña del superadmin en `superadminPassword`. */
    @PutMapping("/{id}/admin")
    @PreAuthorize("hasRole('SUPERADMIN')")
    public ClientAdminResponse updateAdmin(@PathVariable Long id,
                                           @Valid @RequestBody ClientAdminRequest request) {
        return clientService.updateAdmin(id, request);
    }

    @GetMapping("/{id}")
    public ClientResponse getById(@PathVariable Long id) {
        requireOwnClient(id);
        return clientService.getById(id);
    }

    /** Sin page/size → array completo (selects/terminales); con ellos → Page.
     *  Se decide por presencia explícita: el fallback de Boot pagina hasta sin params. */
    @GetMapping("/{id}/stores")
    public Object listStores(@PathVariable Long id,
                             @RequestParam(required = false) String search,
                             @RequestParam(required = false) Boolean deleted,
                             @RequestParam(required = false) Integer page,
                             @RequestParam(required = false) Integer size,
                             Pageable pageable) {
        requireOwnClient(id);
        if (Boolean.TRUE.equals(deleted)) {
            return storeService.listDeletedByClient(id);
        }
        return page != null || size != null
                ? storeService.pageByClient(id, search, pageable)
                : storeService.listByClient(id);
    }

    @GetMapping("/{id}/settings")
    public ClientSettingsResponse getSettings(@PathVariable Long id) {
        requireOwnClient(id);
        return clientService.getSettings(id);
    }

    @PutMapping("/{id}/settings")
    @PreAuthorize("hasRole('ADMIN')")
    public ClientSettingsResponse updateSettings(@PathVariable Long id,
                                                 @RequestBody ClientSettingsRequest request) {
        requireOwnClient(id);
        return clientService.updateSettings(id, request);
    }

    private void requireOwnClient(Long id) {
        // SUPERADMIN no tiene clientId — cae en AccessDenied igual que un tenant ajeno
        Long clientId = AuthContext.current().clientId();
        if (clientId == null || !clientId.equals(id)) {
            throw new AccessDeniedException("Not your client");
        }
    }
}
