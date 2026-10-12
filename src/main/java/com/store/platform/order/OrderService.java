package com.store.platform.order;

import com.store.platform.common.exception.BusinessException;
import com.store.platform.common.exception.ResourceNotFoundException;
import com.store.platform.inventory.InventoryService;
import com.store.platform.order.dto.OrderItemExtraRequest;
import com.store.platform.order.dto.OrderItemRequest;
import com.store.platform.order.dto.OrderRequest;
import com.store.platform.order.dto.OrderResponse;
import com.store.platform.order.dto.TicketResponse;
import com.store.platform.product.Product;
import com.store.platform.product.ProductRepository;
import com.store.platform.security.AuthContext;
import com.store.platform.security.AuthUser;
import com.store.platform.store.Store;
import com.store.platform.store.StoreRepository;
import com.store.platform.store.StoreService;
import com.store.platform.user.User;
import com.store.platform.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrderService {

    private static final Map<Order.Status, Set<Order.Status>> TRANSITIONS = Map.of(
            Order.Status.PENDING,
            Set.of(Order.Status.CONFIRMED, Order.Status.PREPARING, Order.Status.CANCELLED),
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

        // lock del store: serializa la asignación del folio entre cajas/kioscos
        Store store = storeRepository.findByIdForUpdate(storeId)
                .orElseThrow(() -> new ResourceNotFoundException("Store not found: " + storeId));

        Set<Long> productIds = request.items().stream()
                .map(OrderItemRequest::productId)
                .collect(Collectors.toSet());
        List<Product> products = productRepository.findAllById(productIds);
        Map<Long, Product> byId = products.stream()
                .collect(Collectors.toMap(Product::getId, p -> p));
        if (products.size() != productIds.size()) {
            throw new ResourceNotFoundException("One or more products not found");
        }

        Order order = Order.builder()
                .store(store)
                .user(userRepository.getReferenceById(auth.userId()))
                .channel(request.channel())
                .orderType(request.orderType())
                .paymentMethod(request.paymentMethod())
                .status(Order.Status.PENDING)
                .refundStatus(Order.RefundStatus.NONE)
                .total(BigDecimal.ZERO)
                .build();

        BigDecimal total = BigDecimal.ZERO;
        for (OrderItemRequest item : request.items()) {
            Product product = byId.get(item.productId());
            if (product.getCategory().getStores().stream().noneMatch(s -> s.getId().equals(storeId))) {
                throw new BusinessException("Product does not belong to this store: " + product.getName());
            }
            if (!product.isActive() || product.isSoldOut()) {
                throw new BusinessException("Product is not available: " + product.getName());
            }

            BigDecimal unit = product.getPrice();
            String sizeName = null;
            if (item.sizeId() != null) {
                var size = product.getSizes().stream()
                        .filter(s -> s.getId().equals(item.sizeId()))
                        .findFirst()
                        .orElseThrow(() -> new BusinessException(
                                "Size not available for product: " + product.getName()));
                sizeName = size.getName();
                unit = unit.add(size.getPrice());
            }

            List<ItemExtraSnapshot> extras = new ArrayList<>();
            if (item.extras() != null) {
                for (OrderItemExtraRequest e : item.extras()) {
                    var extra = product.getExtras().stream()
                            .filter(x -> x.getId().equals(e.extraId()))
                            .findFirst()
                            .orElseThrow(() -> new BusinessException(
                                    "Extra not available for product: " + product.getName()));
                    extras.add(new ItemExtraSnapshot(extra.getName(), extra.getPrice(), e.quantity()));
                    unit = unit.add(extra.getPrice().multiply(BigDecimal.valueOf(e.quantity())));
                }
            }

            inventoryService.decrease(product, item.quantity());
            order.getItems().add(OrderItem.builder()
                    .order(order)
                    .product(product)
                    .quantity(item.quantity())
                    .unitPrice(unit)
                    .sizeName(sizeName)
                    .extras(extras)
                    .notes(item.notes())
                    .build());
            total = total.add(unit.multiply(BigDecimal.valueOf(item.quantity())));
        }
        // El impuesto se agrega sobre la suma de items — los precios del
        // catálogo no lo incluyen. taxRate queda como snapshot del cliente
        // (sobrevive cambios de settings).
        BigDecimal rate = order.getStore().getClient().getTaxRate();
        if (rate == null || rate.signum() <= 0) {
            rate = BigDecimal.ZERO;
        }
        BigDecimal taxAmount = total.multiply(rate)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        order.setSubtotal(total);
        order.setTaxRate(rate);
        order.setTaxAmount(taxAmount);
        order.setTotal(total.add(taxAmount));
        // efectivo recibido → cambio calculado server-side (solo CASH)
        if (request.paymentMethod() == Order.PaymentMethod.CASH
                && request.cashReceived() != null) {
            if (request.cashReceived().compareTo(order.getTotal()) < 0) {
                throw new BusinessException("Cash received is less than the total");
            }
            order.setCashReceived(request.cashReceived());
            order.setChangeGiven(request.cashReceived().subtract(order.getTotal()));
        }
        // folio semanal consecutivo — con el lock del store no hay duplicados
        order.setTicketNumber(
                orderRepository.maxTicketNumberSince(storeId, weekStart()) + 1);
        return OrderResponse.from(orderRepository.save(order));
    }

    /** Lunes 00:00 de la semana actual (timezone del servidor). */
    private Instant weekStart() {
        return LocalDate.now()
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant();
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> list(Long storeId, Order.Status status) {
        AuthUser auth = AuthContext.current();
        // admin sin tienda activa → historial de todo el cliente
        if (storeId == null && auth.storeId() == null && auth.role() == User.Role.ADMIN) {
            List<Order> orders = status == null
                    ? orderRepository.findByStoreClientIdOrderByCreatedAtDesc(auth.clientId())
                    : orderRepository.findByStoreClientIdAndStatusOrderByCreatedAtDesc(
                            auth.clientId(), status);
            return orders.stream().map(OrderResponse::from).toList();
        }
        Long scope = storeService.resolveScope(storeId);
        List<Order> orders = status == null
                ? orderRepository.findByStoreIdOrderByCreatedAtDesc(scope)
                : orderRepository.findByStoreIdAndStatusOrderByCreatedAtDesc(scope, status);
        return orders.stream().map(OrderResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public Page<OrderResponse> listPage(Long storeId, Order.Status status, Pageable pageable) {
        AuthUser auth = AuthContext.current();
        // historial: más reciente primero salvo sort explícito
        Pageable sorted = pageable.getSort().isUnsorted()
                ? PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                        Sort.by("createdAt").descending())
                : pageable;
        // admin sin tienda activa → historial de todo el cliente
        if (storeId == null && auth.storeId() == null && auth.role() == User.Role.ADMIN) {
            Page<Order> orders = status == null
                    ? orderRepository.findByStoreClientId(auth.clientId(), sorted)
                    : orderRepository.findByStoreClientIdAndStatus(auth.clientId(), status, sorted);
            return orders.map(OrderResponse::from);
        }
        Long scope = storeService.resolveScope(storeId);
        Page<Order> orders = status == null
                ? orderRepository.findByStoreId(scope, sorted)
                : orderRepository.findByStoreIdAndStatus(scope, status, sorted);
        return orders.map(OrderResponse::from);
    }

    @Transactional(readOnly = true)
    public OrderResponse getById(Long id) {
        return OrderResponse.from(findScopedOrThrow(id));
    }

    @Transactional(readOnly = true)
    public TicketResponse getTicket(Long id) {
        return TicketResponse.from(findScopedOrThrow(id));
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
