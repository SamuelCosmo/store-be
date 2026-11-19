package com.store.platform.category;

import com.store.platform.category.dto.CategoryRequest;
import com.store.platform.category.dto.CategoryResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER') and hasAuthority('MODULE_CATALOG')")
    public ResponseEntity<CategoryResponse> create(@Valid @RequestBody CategoryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(categoryService.create(request));
    }

    /** Sin page/size → array completo (terminales/selects); con ellos → Page.
     *  Se decide por presencia explícita: el fallback de Boot pagina hasta sin params. */
    @GetMapping
    public Object list(@RequestParam(required = false) Long storeId,
                       @RequestParam(required = false) String search,
                       @RequestParam(required = false) Boolean deleted,
                       @RequestParam(required = false) Integer page,
                       @RequestParam(required = false) Integer size,
                       Pageable pageable) {
        if (Boolean.TRUE.equals(deleted)) {
            return categoryService.listDeleted();
        }
        return page != null || size != null
                ? categoryService.listPage(storeId, search, pageable)
                : categoryService.list(storeId);
    }

    @GetMapping("/{id}")
    public CategoryResponse getById(@PathVariable Long id) {
        return categoryService.getById(id);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER') and hasAuthority('MODULE_CATALOG')")
    public CategoryResponse update(@PathVariable Long id, @Valid @RequestBody CategoryRequest request) {
        return categoryService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER') and hasAuthority('MODULE_CATALOG')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        categoryService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/restore")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER') and hasAuthority('MODULE_CATALOG')")
    public CategoryResponse restore(@PathVariable Long id) {
        return categoryService.restore(id);
    }
}
