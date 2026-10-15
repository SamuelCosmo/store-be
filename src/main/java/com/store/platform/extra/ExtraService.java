package com.store.platform.extra;

import com.store.platform.client.Client;
import com.store.platform.client.ClientRepository;
import com.store.platform.common.exception.ResourceNotFoundException;
import com.store.platform.extra.dto.ExtraRequest;
import com.store.platform.extra.dto.ExtraResponse;
import com.store.platform.product.Product;
import com.store.platform.product.ProductRepository;
import com.store.platform.security.AuthContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ExtraService {

    private final ExtraRepository extraRepository;
    private final ClientRepository clientRepository;
    private final ProductRepository productRepository;

    @Transactional
    public ExtraResponse create(ExtraRequest request) {
        Long clientId = AuthContext.current().clientId();
        Client client = clientRepository.findById(clientId)
                .orElseThrow(() -> new ResourceNotFoundException("Client not found"));
        Extra extra = Extra.builder()
                .client(client)
                .name(request.name())
                .price(request.price())
                .active(request.active() == null || request.active())
                .build();
        extra = extraRepository.save(extra);
        syncProducts(extra, request.productIds(), clientId);
        return ExtraResponse.from(extra, productIds(extra.getId()));
    }

    @Transactional(readOnly = true)
    public List<ExtraResponse> list() {
        return extraRepository.findByClientIdAndDeletedAtIsNullOrderByName(AuthContext.current().clientId()).stream()
                .map(e -> ExtraResponse.from(e, productIds(e.getId())))
                .toList();
    }

    @Transactional
    public ExtraResponse update(Long id, ExtraRequest request) {
        Extra extra = findOwnedOrThrow(id);
        extra.setName(request.name());
        extra.setPrice(request.price());
        if (request.active() != null) extra.setActive(request.active());
        syncProducts(extra, request.productIds(), AuthContext.current().clientId());
        return ExtraResponse.from(extraRepository.save(extra), productIds(extra.getId()));
    }

    @Transactional
    public void delete(Long id) {
        Extra extra = findOwnedOrThrow(id);
        // se desvincula de los productos para que no quede ordenable; el join
        // histórico vive en el snapshot de la orden, no en esta tabla
        productRepository.findDistinctByExtras_IdAndDeletedAtIsNull(id)
                .forEach(p -> p.getExtras().remove(extra));
        extra.setDeletedAt(Instant.now());
        extraRepository.save(extra);
    }

    public List<ExtraResponse> listDeleted() {
        return extraRepository.findByClientIdAndDeletedAtIsNotNullOrderByName(AuthContext.current().clientId()).stream()
                .map(e -> ExtraResponse.from(e, productIds(e.getId())))
                .toList();
    }

    /** Revive el catálogo; los productos hay que reasignarlos (el unlink del
     *  delete no se deshace — la info de asignación ya no existe). */
    @Transactional
    public ExtraResponse restore(Long id) {
        Extra extra = extraRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Extra not found: " + id));
        if (!extra.getClient().getId().equals(AuthContext.current().clientId())) {
            throw new ResourceNotFoundException("Extra not found: " + id);
        }
        extra.setDeletedAt(null);
        return ExtraResponse.from(extraRepository.save(extra), productIds(extra.getId()));
    }

    /** Extra must belong to the caller's client — foreign extras are invisible. */
    @Transactional(readOnly = true)
    public Extra findOwnedOrThrow(Long id) {
        Extra extra = extraRepository.findById(id)
                .filter(e -> e.getDeletedAt() == null)
                .orElseThrow(() -> new ResourceNotFoundException("Extra not found: " + id));
        if (!extra.getClient().getId().equals(AuthContext.current().clientId())) {
            throw new ResourceNotFoundException("Extra not found: " + id);
        }
        return extra;
    }

    /** Reconcile which products offer this extra (owner side is Product.extras). */
    private void syncProducts(Extra extra, List<Long> productIds, Long clientId) {
        if (productIds == null) return;
        Set<Product> wanted = productIds.stream()
                .map(pid -> resolveProduct(pid, clientId))
                .collect(Collectors.toSet());
        for (Product p : productRepository.findDistinctByExtras_IdAndDeletedAtIsNull(extra.getId())) {
            if (!wanted.contains(p)) p.getExtras().remove(extra);
        }
        for (Product p : wanted) p.getExtras().add(extra);
    }

    private Product resolveProduct(Long id, Long clientId) {
        Product product = productRepository.findById(id)
                .filter(p -> p.getDeletedAt() == null)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + id));
        boolean owned = product.getCategory().getStores().stream()
                .anyMatch(s -> s.getClient().getId().equals(clientId));
        if (!owned) throw new ResourceNotFoundException("Product not found: " + id);
        return product;
    }

    private List<Long> productIds(Long extraId) {
        return productRepository.findDistinctByExtras_IdAndDeletedAtIsNull(extraId).stream()
                .map(Product::getId)
                .toList();
    }
}
