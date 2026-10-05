package com.store.platform.client;

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

@RestController
@RequestMapping("/api/clients")
@RequiredArgsConstructor
public class ClientController {

    private final ClientService clientService;
    private final StoreService storeService;

    // ponytail: open endpoint for dev onboarding; becomes platform-admin only when that role exists
    @PostMapping
    public ResponseEntity<ClientResponse> create(@Valid @RequestBody ClientRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(clientService.create(request));
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
                             @RequestParam(required = false) Integer page,
                             @RequestParam(required = false) Integer size,
                             Pageable pageable) {
        requireOwnClient(id);
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
        if (!AuthContext.current().clientId().equals(id)) {
            throw new AccessDeniedException("Not your client");
        }
    }
}
