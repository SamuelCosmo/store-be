package com.store.platform.inventory;

import com.store.platform.audit.AuditService;
import com.store.platform.common.exception.InsufficientStockException;
import com.store.platform.inventory.dto.InventoryRequest;
import com.store.platform.inventory.dto.InventoryResponse;
import com.store.platform.product.Product;
import com.store.platform.product.ProductService;
import com.store.platform.security.AuthContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class InventoryService {

    private final InventoryRepository inventoryRepository;
    private final ProductService productService;
    private final AuditService auditService;

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
        int before = inventory.getQuantity();
        inventory.setQuantity(request.quantity());
        Inventory saved = inventoryRepository.save(inventory);
        auditService.recordFor(AuthContext.current().clientId(), null, "inventory", "set",
                productId, product.getName(),
                AuditService.diff(Map.of("quantity", before), Map.of("quantity", request.quantity())));
        return InventoryResponse.from(saved);
    }

    /**
     * Deducts stock when the product has inventory configured; throws if
     * insufficient. Products without an inventory record are treated as
     * unlimited — ponytail: until the real inventory module lands.
     * Call inside the caller's transaction.
     */
    public void decrease(Product product, int quantity) {
        inventoryRepository.findByProductIdForUpdate(product.getId()).ifPresent(inventory -> {
            if (inventory.getQuantity() < quantity) {
                throw new InsufficientStockException("OUT_OF_STOCK",
                        "Insufficient stock for product: " + product.getName(),
                        java.util.Map.of("product", product.getName()));
            }
            inventory.setQuantity(inventory.getQuantity() - quantity);
            inventoryRepository.save(inventory);
        });
    }

    /** Restores stock — used when an order is cancelled. */
    public void increase(Product product, int quantity) {
        inventoryRepository.findByProductIdForUpdate(product.getId()).ifPresent(inventory -> {
            inventory.setQuantity(inventory.getQuantity() + quantity);
            inventoryRepository.save(inventory);
        });
    }
}
