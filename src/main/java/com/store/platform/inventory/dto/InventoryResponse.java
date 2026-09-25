package com.store.platform.inventory.dto;

import com.store.platform.inventory.Inventory;

public record InventoryResponse(Long productId, int quantity) {

    public static InventoryResponse from(Inventory inventory) {
        return new InventoryResponse(inventory.getProduct().getId(), inventory.getQuantity());
    }
}
