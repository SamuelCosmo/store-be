package com.store.platform.category;

import com.store.platform.category.dto.CategoryRequest;
import com.store.platform.category.dto.CategoryResponse;
import com.store.platform.common.exception.DuplicateResourceException;
import com.store.platform.common.exception.ResourceNotFoundException;
import com.store.platform.store.StoreService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final StoreService storeService;

    @Transactional
    public CategoryResponse create(CategoryRequest request) {
        Long storeId = storeService.resolveScope(request.storeId());
        if (categoryRepository.existsByStoreIdAndNameIgnoreCase(storeId, request.name())) {
            throw new DuplicateResourceException("Category already exists: " + request.name());
        }
        Category category = Category.builder()
                .store(storeService.findOrThrow(storeId))
                .name(request.name())
                .description(request.description())
                .build();
        return CategoryResponse.from(categoryRepository.save(category));
    }

    public List<CategoryResponse> list(Long storeId) {
        Long scope = storeService.resolveScope(storeId);
        return categoryRepository.findByStoreIdOrderByName(scope).stream()
                .map(CategoryResponse::from)
                .toList();
    }

    public CategoryResponse getById(Long id) {
        Category category = findScopedOrThrow(id);
        return CategoryResponse.from(category);
    }

    @Transactional
    public CategoryResponse update(Long id, CategoryRequest request) {
        Category category = findScopedOrThrow(id);
        Long storeId = category.getStore().getId();
        if (categoryRepository.existsByStoreIdAndNameIgnoreCaseAndIdNot(storeId, request.name(), id)) {
            throw new DuplicateResourceException("Category already exists: " + request.name());
        }
        category.setName(request.name());
        category.setDescription(request.description());
        return CategoryResponse.from(categoryRepository.save(category));
    }

    @Transactional
    public void delete(Long id) {
        categoryRepository.delete(findScopedOrThrow(id));
    }

    /** Category must belong to a store the caller may operate. */
    public Category findScopedOrThrow(Long id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found: " + id));
        storeService.resolveScope(category.getStore().getId());
        return category;
    }
}
