package com.store.platform.order;

import com.store.platform.common.exception.BusinessException;
import com.store.platform.common.exception.ResourceNotFoundException;
import com.store.platform.inventory.InventoryService;
import com.store.platform.order.dto.OrderItemRequest;
import com.store.platform.order.dto.OrderRequest;
import com.store.platform.order.dto.OrderResponse;
import com.store.platform.product.Product;
import com.store.platform.product.ProductRepository;
import com.store.platform.security.AuthContext;
import com.store.platform.security.AuthUser;
import com.store.platform.store.StoreRepository;
import com.store.platform.store.StoreService;
import com.store.platform.user.User;
import com.store.platform.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class OrderService {

    private static final Map<Order.Status, Set<Order.Status>> TRANSITIONS = Map.of(
            Order.Status.PENDING, Set.of(Order.Status.CONFIRMED, Order.Status.CANCELLED),
            Order.Status.CONFIRMED, Set.of(Order.Status.PREPARING, Order.Status.CANCELLED),
            Order.Status.PREPARING, Set.of(Order.Status.READY, Order.Status.CANCELLED),
            Order.Status.READY, Set.of(Order.Status.COMPLETED),
            Order.Status.COMPLETED, Set.of(),
            Order.Status.CANCELLED, Set.of());

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final StoreRepository storeRepository;
    private final UserRepository userRepository;
    private final InventoryService inventoryService;
    private final StoreService storeService;

    @Transactional
    public OrderResponse create(OrderRequest request) {
        AuthUser auth = AuthContext.current();
        Long storeId = AuthContext.requireStoreId();

        if (auth.role() == User.Role.CUSTOMER && request.channel() != Order.Channel.KIOSK) {
            throw new BusinessException("Customer accounts can only create KIOSK orders");
        }
        if (request.channel() == Order.Channel.KIOSK
                && request.paymentMethod() != Order.PaymentMethod.CARD) {
            throw new BusinessException("Kiosk orders only accept CARD payment");
        }

        Map<Long, Integer> quantities = new LinkedHashMap<>();
        for (OrderItemRequest item : request.items()) {
            quantities.merge(item.productId(), item.quantity(), Integer::sum);
        }

        List<Product> products = productRepository.findAllById(quantities.keySet());
        if (products.size() != quantities.size()) {
            throw new ResourceNotFoundException("One or more products not found");
        }

        Order order = Order.builder()
                .store(storeRepository.getReferenceById(storeId))
                .user(userRepository.getReferenceById(auth.userId()))
                .channel(request.channel())
                .orderType(request.orderType())
                .paymentMethod(request.paymentMethod())
                .status(Order.Status.PENDING)
                .refundStatus(Order.RefundStatus.NONE)
                .total(BigDecimal.ZERO)
                .build();

        BigDecimal total = BigDecimal.ZERO;
        for (Product product : products) {
            if (!product.getCategory().getStore().getId().equals(storeId)) {
                throw new BusinessException("Product does not belong to this store: " + product.getName());
            }
            if (!product.isActive()) {
                throw new BusinessException("Product is not available: " + product.getName());
            }
            int quantity = quantities.get(product.getId());
            inventoryService.decrease(product, quantity);
            order.getItems().add(OrderItem.builder()
                    .order(order)
                    .product(product)
                    .quantity(quantity)
                    .unitPrice(product.getPrice())
                    .build());
            total = total.add(product.getPrice().multiply(BigDecimal.valueOf(quantity)));
        }
        order.setTotal(total);
        return OrderResponse.from(orderRepository.save(order));
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> list(Long storeId, Order.Status status) {
        Long scope = storeService.resolveScope(storeId);
        List<Order> orders = status == null
                ? orderRepository.findByStoreIdOrderByCreatedAtDesc(scope)
                : orderRepository.findByStoreIdAndStatusOrderByCreatedAtDesc(scope, status);
        return orders.stream().map(OrderResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public OrderResponse getById(Long id) {
        return OrderResponse.from(findScopedOrThrow(id));
    }

    @Transactional
    public OrderResponse updateStatus(Long id, Order.Status target) {
        Order order = findScopedOrThrow(id);
        if (!TRANSITIONS.get(order.getStatus()).contains(target)) {
            throw new BusinessException(
                    "Cannot move order from " + order.getStatus() + " to " + target);
        }
        if (target == Order.Status.CANCELLED) {
            // paid orders are refunded manually by staff — mark them for the report
            order.setRefundStatus(Order.RefundStatus.REFUNDED);
            order.getItems().forEach(item ->
                    inventoryService.increase(item.getProduct(), item.getQuantity()));
        }
        order.setStatus(target);
        return OrderResponse.from(orderRepository.save(order));
    }

    /** Order must belong to a store the caller may operate. */
    public Order findScopedOrThrow(Long id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + id));
        storeService.resolveScope(order.getStore().getId());
        return order;
    }
}
