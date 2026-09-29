package com.store.platform.product;

import com.store.platform.category.Category;
import com.store.platform.category.CategoryService;
import com.store.platform.common.exception.ResourceNotFoundException;
import com.store.platform.extra.Extra;
import com.store.platform.extra.ExtraService;
import com.store.platform.size.Size;
import com.store.platform.size.SizeService;
import com.store.platform.product.dto.ProductRequest;
import com.store.platform.product.dto.ProductResponse;
import com.store.platform.security.AuthContext;
import com.store.platform.security.AuthUser;
import com.store.platform.store.StoreService;
import com.store.platform.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryService categoryService;
    private final StoreService storeService;
    private final ExtraService extraService;
    private final SizeService sizeService;

    @Transactional
    public ProductResponse create(ProductRequest request) {
        Category category = categoryService.findScopedOrThrow(request.categoryId());
        Product product = Product.builder()
                .category(category)
                .name(request.name())
                .description(request.description())
                .sku(request.sku())
                .image(request.image())
                .price(request.price())
                .active(true)
                .tokenCost(request.tokenCost() == null ? 0 : request.tokenCost())
                .extras(resolveExtras(request.extraIds()))
                .sizes(resolveSizes(request.sizeIds()))
                .build();
        return ProductResponse.from(productRepository.save(product));
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> list(Long storeId, Long categoryId, Boolean activeOnly) {
        // kiosk/customer sessions only ever see active products
        AuthUser auth = AuthContext.current();
        boolean onlyActive = Boolean.TRUE.equals(activeOnly)
                || auth.role() == User.Role.CUSTOMER;
        if (categoryId != null) {
            categoryService.findScopedOrThrow(categoryId);
            return map(onlyActive
                    ? productRepository.findByCategoryIdAndActiveTrue(categoryId)
                    : productRepository.findByCategoryId(categoryId));
        }
        if (storeId == null && auth.storeId() == null && auth.role() == User.Role.ADMIN) {
            return map(onlyActive
                    ? productRepository.findDistinctByCategory_Stores_Client_IdAndActiveTrue(auth.clientId())
                    : productRepository.findDistinctByCategory_Stores_Client_Id(auth.clientId()));
        }
        Long scope = storeService.resolveScope(storeId);
        return map(onlyActive
                ? productRepository.findByCategoryStores_IdAndActiveTrue(scope)
                : productRepository.findByCategoryStores_Id(scope));
    }

    @Transactional(readOnly = true)
    public ProductResponse getById(Long id) {
        return ProductResponse.from(findScopedOrThrow(id));
    }

    @Transactional
    public ProductResponse update(Long id, ProductRequest request) {
        Product product = findScopedOrThrow(id);
        Category category = categoryService.findScopedOrThrow(request.categoryId());
        product.setCategory(category);
        product.setName(request.name());
        product.setDescription(request.description());
        product.setSku(request.sku());
        product.setImage(request.image());
        product.setPrice(request.price());
        product.setTokenCost(request.tokenCost() == null ? 0 : request.tokenCost());
        if (request.extraIds() != null) {
            product.setExtras(resolveExtras(request.extraIds()));
        }
        if (request.sizeIds() != null) {
            product.setSizes(resolveSizes(request.sizeIds()));
        }
        return ProductResponse.from(productRepository.save(product));
    }

    @Transactional
    public ProductResponse setActive(Long id, boolean active) {
        Product product = findScopedOrThrow(id);
        product.setActive(active);
        return ProductResponse.from(productRepository.save(product));
    }

    @Transactional
    public void delete(Long id) {
        productRepository.delete(findScopedOrThrow(id));
    }

    private Set<Extra> resolveExtras(List<Long> extraIds) {
        if (extraIds == null || extraIds.isEmpty()) return Set.of();
        return extraIds.stream()
                .map(extraService::findOwnedOrThrow)
                .collect(Collectors.toSet());
    }

    private Set<Size> resolveSizes(List<Long> sizeIds) {
        if (sizeIds == null || sizeIds.isEmpty()) return Set.of();
        return sizeIds.stream()
                .map(sizeService::findOwnedOrThrow)
                .collect(Collectors.toSet());
    }

    /** Product must live under a store the caller may operate. */
    @Transactional(readOnly = true)
    public Product findScopedOrThrow(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + id));
        storeService.resolveScope(product.getCategory().getStores().stream()
                .map(com.store.platform.store.Store::getId)
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Category has no stores: " + id)));
        return product;
    }

    private List<ProductResponse> map(List<Product> products) {
        return products.stream().map(ProductResponse::from).toList();
    }
}
