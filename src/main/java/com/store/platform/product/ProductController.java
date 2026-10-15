package com.store.platform.product;

import com.store.platform.product.dto.ActiveRequest;
import com.store.platform.product.dto.ProductRequest;
import com.store.platform.product.dto.ProductResponse;
import com.store.platform.product.dto.SoldOutRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<ProductResponse> create(@Valid @RequestBody ProductRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(productService.create(request));
    }

    /** Sin page/size → array completo (terminales/selects); con ellos → Page.
     *  Se decide por presencia explícita: el fallback de Boot pagina hasta sin params. */
    @GetMapping
    public Object list(@RequestParam(required = false) Long storeId,
                       @RequestParam(required = false) Long categoryId,
                       @RequestParam(required = false) Boolean activeOnly,
                       @RequestParam(required = false) Boolean deleted,
                       @RequestParam(required = false) String search,
                       @RequestParam(required = false) Integer page,
                       @RequestParam(required = false) Integer size,
                       Pageable pageable) {
        if (Boolean.TRUE.equals(deleted)) {
            return productService.listDeleted();
        }
        return page != null || size != null
                ? productService.listPage(storeId, categoryId, activeOnly, search, pageable)
                : productService.list(storeId, categoryId, activeOnly);
    }

    @GetMapping("/{id}")
    public ProductResponse getById(@PathVariable Long id) {
        return productService.getById(id);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ProductResponse update(@PathVariable Long id, @Valid @RequestBody ProductRequest request) {
        return productService.update(id, request);
    }

    @PatchMapping("/{id}/active")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ProductResponse setActive(@PathVariable Long id, @Valid @RequestBody ActiveRequest request) {
        return productService.setActive(id, request.active());
    }

    /** "86'd" toggle — POS staff included: a cashier can mark an item as gone mid-shift. */
    @PatchMapping("/{id}/sold-out")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','EMPLOYEE')")
    public ProductResponse setSoldOut(@PathVariable Long id, @Valid @RequestBody SoldOutRequest request) {
        return productService.setSoldOut(id, request.soldOut());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        productService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/restore")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ProductResponse restore(@PathVariable Long id) {
        return productService.restore(id);
    }
}
