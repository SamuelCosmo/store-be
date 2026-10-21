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
import com.store.platform.storage.StorageService;
import com.store.platform.store.StoreService;
import com.store.platform.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
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
    private final StorageService storageService;

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
                    ? productRepository.findByCategoryIdAndActiveTrueAndDeletedAtIsNull(categoryId)
                    : productRepository.findByCategoryIdAndDeletedAtIsNull(categoryId));
        }
        if (storeId == null && auth.storeId() == null && auth.role() == User.Role.ADMIN) {
            return map(onlyActive
                    ? productRepository.findDistinctByCategory_Stores_Client_IdAndActiveTrueAndDeletedAtIsNull(auth.clientId())
                    : productRepository.findDistinctByCategory_Stores_Client_IdAndDeletedAtIsNull(auth.clientId()));
        }
        Long scope = storeService.resolveScope(storeId);
        return map(onlyActive
                ? productRepository.findByCategoryStores_IdAndActiveTrue(scope)
                : productRepository.findByCategoryStores_Id(scope));
    }

    /** Versión paginada para la tabla del admin — mismo scoping/filtros que {@link #list}. */
    @Transactional(readOnly = true)
    public Page<ProductResponse> listPage(Long storeId, Long categoryId, Boolean activeOnly,
                                          String search, Pageable pageable) {
        AuthUser auth = AuthContext.current();
        boolean onlyActive = Boolean.TRUE.equals(activeOnly)
                || auth.role() == User.Role.CUSTOMER;
        String q = search == null ? "" : search.trim();
        Page<Product> page;
        if (categoryId != null) {
            categoryService.findScopedOrThrow(categoryId);
            page = productRepository.pageByCategory(categoryId, q, onlyActive, pageable);
        } else if (storeId == null && auth.storeId() == null && auth.role() == User.Role.ADMIN) {
            page = productRepository.pageByClient(auth.clientId(), q, onlyActive, pageable);
        } else {
            page = productRepository.pageByStore(storeService.resolveScope(storeId), q, onlyActive, pageable);
        }
        return page.map(ProductResponse::from);
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
        String oldImage = product.getImage();
        product.setImage(request.image());
        product.setPrice(request.price());
        product.setTokenCost(request.tokenCost() == null ? 0 : request.tokenCost());
        // mutar la colección gestionada — reemplazarla con un set inmutable
        // rompe el sync del join en flush
        if (request.extraIds() != null) {
            product.getExtras().clear();
            product.getExtras().addAll(resolveExtras(request.extraIds()));
        }
        if (request.sizeIds() != null) {
            product.getSizes().clear();
            product.getSizes().addAll(resolveSizes(request.sizeIds()));
        }
        Product saved = productRepository.save(product);
        // la imagen reemplazada queda huérfana en el bucket
        if (oldImage != null && !oldImage.equals(saved.getImage())) {
            storageService.deleteByUrl(oldImage);
        }
        return ProductResponse.from(saved);
    }

    @Transactional
    public ProductResponse setActive(Long id, boolean active) {
        Product product = findScopedOrThrow(id);
        product.setActive(active);
        return ProductResponse.from(productRepository.save(product));
    }

    @Transactional
    public ProductResponse setSoldOut(Long id, boolean soldOut) {
        Product product = findScopedOrThrow(id);
        product.setSoldOut(soldOut);
        return ProductResponse.from(productRepository.save(product));
    }

    @Transactional
    public void delete(Long id) {
        // la imagen se conserva: la fila sigue apuntando a ella para historial/restore
        Product product = findScopedOrThrow(id);
        product.setDeletedAt(Instant.now());
        productRepository.save(product);
    }

    public List<ProductResponse> listDeleted() {
        return productRepository
                .findDistinctByCategory_Stores_Client_IdAndDeletedAtIsNotNull(AuthContext.current().clientId())
                .stream().map(ProductResponse::from).toList();
    }

    @Transactional
    public ProductResponse restore(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + id));
        boolean owned = product.getCategory().getStores().stream()
                .anyMatch(s -> s.getClient().getId().equals(AuthContext.current().clientId()));
        if (!owned) {
            throw new ResourceNotFoundException("Product not found: " + id);
        }
        product.setDeletedAt(null);
        return ProductResponse.from(productRepository.save(product));
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
                .filter(p -> p.getDeletedAt() == null)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + id));
        // la categoría puede estar en varias tiendas — basta que el caller
        // pueda operar alguna (un gerente de Norte también toca productos
        // compartidos con Centro)
        boolean scoped = product.getCategory().getStores().stream()
                .anyMatch(s -> storeService.canScope(s.getId()));
        if (!scoped) {
            throw new ResourceNotFoundException("Product not found: " + id);
        }
        return product;
    }

    private List<ProductResponse> map(List<Product> products) {
        return products.stream().map(ProductResponse::from).toList();
    }
}
