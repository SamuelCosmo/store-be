package com.store.platform.category;

import com.store.platform.category.dto.CategoryRequest;
import com.store.platform.category.dto.CategoryResponse;
import com.store.platform.common.exception.BusinessException;
import com.store.platform.common.exception.DuplicateResourceException;
import com.store.platform.common.exception.ResourceNotFoundException;
import com.store.platform.product.ProductRepository;
import com.store.platform.security.AuthContext;
import com.store.platform.security.AuthUser;
import com.store.platform.store.Store;
import com.store.platform.store.StoreService;
import com.store.platform.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final StoreService storeService;

    private CategoryResponse toResponse(Category c) {
        return CategoryResponse.from(c, productRepository.countByCategoryId(c.getId()));
    }

    @Transactional
    public CategoryResponse create(CategoryRequest request) {
        AuthUser auth = AuthContext.current();
        Set<Store> stores = resolveStores(request.storeIds(), auth);
        if (categoryRepository.existsByStores_Client_IdAndNameIgnoreCase(auth.clientId(), request.name())) {
            throw new DuplicateResourceException("Category already exists: " + request.name());
        }
        Category category = Category.builder()
                .stores(stores)
                .name(request.name())
                .description(request.description())
                .icon(request.icon())
                .active(request.active() == null || request.active())
                .build();
        return toResponse(categoryRepository.save(category));
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> list(Long storeId) {
        AuthUser auth = AuthContext.current();
        if (storeId != null) {
            Long scope = storeService.resolveScope(storeId);
            return categoryRepository.findDistinctByStores_IdOrderByName(scope).stream()
                    .map(this::toResponse)
                    .toList();
        }
        if (auth.storeId() != null) {
            return categoryRepository.findDistinctByStores_IdOrderByName(auth.storeId()).stream()
                    .map(this::toResponse)
                    .toList();
        }
        // Admin without an active store sees the whole client catalog
        return categoryRepository.findDistinctByStores_Client_IdOrderByName(auth.clientId()).stream()
                .map(this::toResponse)
                .toList();
    }

    /** Versión paginada para la tabla del admin — mismo scoping que {@link #list}. */
    @Transactional(readOnly = true)
    public Page<CategoryResponse> listPage(Long storeId, String search, Pageable pageable) {
        AuthUser auth = AuthContext.current();
        String q = search == null ? "" : search.trim();
        Long scopeStore = storeId != null ? storeService.resolveScope(storeId) : auth.storeId();
        // sort por "products" no es property de la entidad → queries dedicados
        Sort.Order countSort = pageable.getSort().getOrderFor("products");
        Page<Category> page;
        if (countSort != null) {
            Pageable unsorted = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
            boolean asc = countSort.isAscending();
            page = scopeStore != null
                    ? asc ? categoryRepository.pageByStoreOrderByProductsAsc(scopeStore, q, unsorted)
                          : categoryRepository.pageByStoreOrderByProductsDesc(scopeStore, q, unsorted)
                    : asc ? categoryRepository.pageByClientOrderByProductsAsc(auth.clientId(), q, unsorted)
                          : categoryRepository.pageByClientOrderByProductsDesc(auth.clientId(), q, unsorted);
        } else {
            page = scopeStore != null
                    ? categoryRepository.pageByStore(scopeStore, q, pageable)
                    : categoryRepository.pageByClient(auth.clientId(), q, pageable);
        }
        return page.map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public CategoryResponse getById(Long id) {
        return toResponse(findScopedOrThrow(id));
    }

    @Transactional
    public CategoryResponse update(Long id, CategoryRequest request) {
        AuthUser auth = AuthContext.current();
        Category category = findScopedOrThrow(id);
        if (categoryRepository.existsByStores_Client_IdAndNameIgnoreCaseAndIdNot(auth.clientId(), request.name(), id)) {
            throw new DuplicateResourceException("Category already exists: " + request.name());
        }
        category.setName(request.name());
        category.setDescription(request.description());
        category.setIcon(request.icon());
        if (request.active() != null) category.setActive(request.active());
        if (request.storeIds() != null) {
            category.setStores(resolveStores(request.storeIds(), auth));
        }
        return toResponse(categoryRepository.save(category));
    }

    @Transactional
    public void delete(Long id) {
        categoryRepository.delete(findScopedOrThrow(id));
    }

    /** Category must be assigned to a store the caller may operate. */
    @Transactional(readOnly = true)
    public Category findScopedOrThrow(Long id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found: " + id));
        AuthUser auth = AuthContext.current();
        boolean visible = auth.role() == User.Role.ADMIN
                ? category.getStores().stream().anyMatch(s -> s.getClient().getId().equals(auth.clientId()))
                : category.getStores().stream().anyMatch(s -> s.getId().equals(auth.storeId()));
        if (!visible) {
            throw new ResourceNotFoundException("Category not found: " + id);
        }
        return category;
    }

    private Set<Store> resolveStores(Set<Long> storeIds, AuthUser auth) {
        if (storeIds == null || storeIds.isEmpty()) {
            if (auth.role() == User.Role.ADMIN) {
                throw new BusinessException("storeIds required — pick the stores this category applies to");
            }
            return Set.of(storeService.findOrThrow(AuthContext.requireStoreId()));
        }
        return storeIds.stream()
                .map(id -> storeService.findOrThrow(storeService.resolveScope(id)))
                .collect(Collectors.toSet());
    }
}
