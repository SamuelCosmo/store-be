package com.store.platform.size;

import com.store.platform.client.Client;
import com.store.platform.client.ClientRepository;
import com.store.platform.common.exception.ResourceNotFoundException;
import com.store.platform.product.Product;
import com.store.platform.product.ProductRepository;
import com.store.platform.security.AuthContext;
import com.store.platform.size.dto.SizeRequest;
import com.store.platform.size.dto.SizeResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SizeService {

    private final SizeRepository sizeRepository;
    private final ClientRepository clientRepository;
    private final ProductRepository productRepository;

    @Transactional
    public SizeResponse create(SizeRequest request) {
        Long clientId = AuthContext.current().clientId();
        Client client = clientRepository.findById(clientId)
                .orElseThrow(() -> new ResourceNotFoundException("Client not found"));
        Size size = Size.builder()
                .client(client)
                .name(request.name())
                .price(request.price())
                .active(request.active() == null || request.active())
                .build();
        size = sizeRepository.save(size);
        syncProducts(size, request.productIds(), clientId);
        return SizeResponse.from(size, productIds(size.getId()));
    }

    @Transactional(readOnly = true)
    public List<SizeResponse> list() {
        return sizeRepository.findByClientIdOrderByName(AuthContext.current().clientId()).stream()
                .map(s -> SizeResponse.from(s, productIds(s.getId())))
                .toList();
    }

    @Transactional
    public SizeResponse update(Long id, SizeRequest request) {
        Size size = findOwnedOrThrow(id);
        size.setName(request.name());
        size.setPrice(request.price());
        if (request.active() != null) size.setActive(request.active());
        syncProducts(size, request.productIds(), AuthContext.current().clientId());
        return SizeResponse.from(sizeRepository.save(size), productIds(size.getId()));
    }

    @Transactional
    public void delete(Long id) {
        Size size = findOwnedOrThrow(id);
        productRepository.findDistinctBySizes_Id(id)
                .forEach(p -> p.getSizes().remove(size));
        sizeRepository.delete(size);
    }

    /** Size must belong to the caller's client — foreign sizes are invisible. */
    @Transactional(readOnly = true)
    public Size findOwnedOrThrow(Long id) {
        Size size = sizeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Size not found: " + id));
        if (!size.getClient().getId().equals(AuthContext.current().clientId())) {
            throw new ResourceNotFoundException("Size not found: " + id);
        }
        return size;
    }

    /** Reconcile which products offer this size (owner side is Product.sizes). */
    private void syncProducts(Size size, List<Long> productIds, Long clientId) {
        if (productIds == null) return;
        Set<Product> wanted = productIds.stream()
                .map(pid -> resolveProduct(pid, clientId))
                .collect(Collectors.toSet());
        for (Product p : productRepository.findDistinctBySizes_Id(size.getId())) {
            if (!wanted.contains(p)) p.getSizes().remove(size);
        }
        for (Product p : wanted) p.getSizes().add(size);
    }

    private Product resolveProduct(Long id, Long clientId) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + id));
        boolean owned = product.getCategory().getStores().stream()
                .anyMatch(s -> s.getClient().getId().equals(clientId));
        if (!owned) throw new ResourceNotFoundException("Product not found: " + id);
        return product;
    }

    private List<Long> productIds(Long sizeId) {
        return productRepository.findDistinctBySizes_Id(sizeId).stream()
                .map(Product::getId)
                .toList();
    }
}
