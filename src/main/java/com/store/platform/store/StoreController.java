package com.store.platform.store;

import com.store.platform.security.AuthContext;
import com.store.platform.store.dto.DeviceRequest;
import com.store.platform.store.dto.DeviceResponse;
import com.store.platform.store.dto.StoreRequest;
import com.store.platform.store.dto.StoreResponse;
import com.store.platform.store.dto.StoreSettingsRequest;
import com.store.platform.store.dto.StoreSettingsResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/stores")
@RequiredArgsConstructor
public class StoreController {

    private final StoreService storeService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<StoreResponse> create(@Valid @RequestBody StoreRequest request) {
        Long clientId = AuthContext.current().clientId();
        return ResponseEntity.status(HttpStatus.CREATED).body(storeService.create(clientId, request));
    }

    @GetMapping("/{id}")
    public StoreResponse getById(@PathVariable Long id) {
        return storeService.getById(id, AuthContext.current().clientId());
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public StoreResponse update(@PathVariable Long id, @Valid @RequestBody StoreRequest request) {
        return storeService.update(id, request, AuthContext.current().clientId());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        storeService.delete(id, AuthContext.current().clientId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/restore")
    @PreAuthorize("hasRole('ADMIN')")
    public StoreResponse restore(@PathVariable Long id) {
        return storeService.restore(id, AuthContext.current().clientId());
    }

    @GetMapping("/{id}/settings")
    public StoreSettingsResponse getSettings(@PathVariable Long id) {
        return storeService.getSettings(id, AuthContext.current().clientId());
    }

    @PutMapping("/{id}/settings")
    @PreAuthorize("hasRole('ADMIN')")
    public StoreSettingsResponse updateSettings(@PathVariable Long id,
                                                @RequestBody StoreSettingsRequest request) {
        return storeService.updateSettings(id, request, AuthContext.current().clientId());
    }

    @PostMapping("/{id}/devices")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<DeviceResponse> addDevice(@PathVariable Long id,
                                                    @Valid @RequestBody DeviceRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(storeService.addDevice(id, request, AuthContext.current().clientId()));
    }

    @GetMapping("/{id}/devices")
    public List<DeviceResponse> listDevices(@PathVariable Long id) {
        return storeService.listDevices(id, AuthContext.current().clientId());
    }
}
