package com.store.platform.inventory;

import com.store.platform.inventory.dto.InventoryRequest;
import com.store.platform.inventory.dto.InventoryResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryService inventoryService;

    @GetMapping("/{productId}")
    public InventoryResponse get(@PathVariable Long productId) {
        return inventoryService.get(productId);
    }

    @PutMapping("/{productId}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public InventoryResponse set(@PathVariable Long productId,
                                 @Valid @RequestBody InventoryRequest request) {
        return inventoryService.set(productId, request);
    }
}
