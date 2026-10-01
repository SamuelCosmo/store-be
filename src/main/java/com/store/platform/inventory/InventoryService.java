package com.store.platform.inventory;

import com.store.platform.common.exception.InsufficientStockException;
import com.store.platform.inventory.dto.InventoryRequest;
import com.store.platform.inventory.dto.InventoryResponse;
import com.store.platform.product.Product;
import com.store.platform.product.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class InventoryService {

    private final InventoryRepository inventoryRepository;
    private final ProductService productService;

    public InventoryResponse get(Long productId) {
        Product product = productService.findScopedOrThrow(productId);
        return inventoryRepository.findByProductId(product.getId())
                .map(InventoryResponse::from)
                .orElse(new InventoryResponse(product.getId(), 0));
    }

    @Transactional
    public InventoryResponse set(Long productId, InventoryRequest request) {
        Product product = productService.findScopedOrThrow(productId);
        Inventory inventory = inventoryRepository.findByProductId(productId)
                .orElse(Inventory.builder().product(product).quantity(0).build());
        inventory.setQuantity(request.quantity());
        return InventoryResponse.from(inventoryRepository.save(inventory));
    }

    /**
     * Deducts stock when the product has inventory configured; throws if
     * insufficient. Products without an inventory record are treated as
     * unlimited — ponytail: until the real inventory module lands.
     * Call inside the caller's transaction.
     */
    public void decrease(Product product, int quantity) {
        inventoryRepository.findByProductId(product.getId()).ifPresent(inventory -> {
            if (inventory.getQuantity() < quantity) {
                throw new InsufficientStockException(
                        "Insufficient stock for product: " + product.getName());
            }
            inventory.setQuantity(inventory.getQuantity() - quantity);
            inventoryRepository.save(inventory);
        });
    }

    /** Restores stock — used when an order is cancelled. */
    public void increase(Product product, int quantity) {
        inventoryRepository.findByProductId(product.getId()).ifPresent(inventory -> {
            inventory.setQuantity(inventory.getQuantity() + quantity);
            inventoryRepository.save(inventory);
        });
    }
}
