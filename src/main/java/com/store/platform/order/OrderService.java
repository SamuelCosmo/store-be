package com.store.platform.order;

import com.store.platform.audit.AuditService;
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
import org.springframework.data.jpa.domain.Specification;
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
import java.util.LinkedHashMap;
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
    private final AuditService auditService;

    @Transactional
    public OrderResponse create(OrderRequest request, String idempotencyKey) {
        com.store.platform.common.Idempotency.check(idempotencyKey);
        AuthUser auth = AuthContext.current();
        // tienda explícita (selector multi-tienda del POS) o la de sesión
        Long storeId = storeService.resolveScope(request.storeId());

        if (auth.role() == User.Role.CUSTOMER && request.channel() != Order.Channel.KIOSK) {
            throw new BusinessException("CUSTOMER_KIOSK_ONLY", "Customer accounts can only create KIOSK orders");
        }
        if (request.channel() == Order.Channel.KIOSK
                && request.paymentMethod() != Order.PaymentMethod.CARD) {
            throw new BusinessException("KIOSK_CARD_ONLY", "Kiosk orders only accept CARD payment");
        }
        // TODO(bancomer): hoy CARD se da por cobrado sin confirmación — la
        // orden entra directo a PENDING (cocina). Con la integración de la
        // terminal Bancomer: crear la orden en PENDING_PAYMENT, mandar el
        // cobro a la terminal y pasar a PENDING solo al recibir la
        // aprobación (guardar folio/autorización); rechazo → CANCELLED.

        // lock del store: serializa la asignación del folio entre cajas/kioscos
        Store store = storeRepository.findByIdForUpdate(storeId)
                .orElseThrow(() -> new ResourceNotFoundException("Store not found: " + storeId));
        // reintento del mismo cobro: con el lock del store ya tomado, dos
        // requests con el mismo key no pueden crear dos órdenes
        if (idempotencyKey != null) {
            var existing = orderRepository.findByStoreIdAndIdempotencyKey(storeId, idempotencyKey);
            if (existing.isPresent()) return OrderResponse.from(existing.get());
        }

        Set<Long> productIds = request.items().stream()
                .map(OrderItemRequest::productId)
                .collect(Collectors.toSet());
        List<Product> products = productRepository.findAllById(productIds);
        Map<Long, Product> byId = products.stream()
                .collect(Collectors.toMap(Product::getId, p -> p));
        if (products.size() != productIds.size()) {
            throw new ResourceNotFoundException("One or more products not found");
        }

        // TOKEN solo llega por caja: CUSTOMER es forzado a KIOSK y el kiosco
        // solo acepta CARD — ambos guards ya corren arriba.
        boolean tokenPayment = request.paymentMethod() == Order.PaymentMethod.TOKEN;

        Order order = Order.builder()
                .store(store)
                .user(userRepository.getReferenceById(auth.userId()))
                .channel(request.channel())
                .orderType(request.orderType())
                .paymentMethod(request.paymentMethod())
                .status(Order.Status.PENDING)
                .refundStatus(Order.RefundStatus.NONE)
                .idempotencyKey(idempotencyKey)
                .total(BigDecimal.ZERO)
                .build();

        BigDecimal total = BigDecimal.ZERO;
        int tokenAmount = 0;
        for (OrderItemRequest item : request.items()) {
            Product product = byId.get(item.productId());
            if (product.getCategory().getStores().stream().noneMatch(s -> s.getId().equals(storeId))) {
                throw new BusinessException("PRODUCT_WRONG_STORE",
                        "Product does not belong to this store: " + product.getName(),
                        Map.of("product", product.getName()));
            }
            if (!product.isActive() || product.isSoldOut() || product.getDeletedAt() != null) {
                throw new BusinessException("PRODUCT_UNAVAILABLE",
                        "Product is not available: " + product.getName(),
                        Map.of("product", product.getName()));
            }
            if (tokenPayment && product.getTokenCost() <= 0) {
                throw new BusinessException("PRODUCT_NOT_TOKENABLE",
                        "Not payable with tokens: " + product.getName(),
                        Map.of("product", product.getName()));
            }

            BigDecimal unit = product.getPrice();
            String sizeName = null;
            if (item.sizeId() != null) {
                var size = product.getSizes().stream()
                        .filter(s -> s.getId().equals(item.sizeId()) && s.getDeletedAt() == null)
                        .findFirst()
                        .orElseThrow(() -> new BusinessException("SIZE_UNAVAILABLE",
                                "Size not available for product: " + product.getName(),
                                Map.of("product", product.getName())));
                sizeName = size.getName();
                unit = unit.add(size.getPrice());
            }

            List<ItemExtraSnapshot> extras = new ArrayList<>();
            if (item.extras() != null) {
                for (OrderItemExtraRequest e : item.extras()) {
                    var extra = product.getExtras().stream()
                            .filter(x -> x.getId().equals(e.extraId()) && x.getDeletedAt() == null)
                            .findFirst()
                            .orElseThrow(() -> new BusinessException("EXTRA_UNAVAILABLE",
                                    "Extra not available for product: " + product.getName(),
                                    Map.of("product", product.getName())));
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
                    .tokenCost(product.getTokenCost())
                    .build());
            total = total.add(unit.multiply(BigDecimal.valueOf(item.quantity())));
            tokenAmount += product.getTokenCost() * item.quantity();
        }
        BigDecimal rate = order.getStore().getClient().getTaxRate();
        if (rate == null || rate.signum() <= 0) {
            rate = BigDecimal.ZERO;
        }
        order.setTaxRate(rate);
        if (tokenPayment) {
            // El dinero entró al vender las fichas (token_sales) — la orden
            // solo registra el canje: totales en $0 para no inflar ventas.
            order.setSubtotal(BigDecimal.ZERO);
            order.setTaxAmount(BigDecimal.ZERO);
            order.setTotal(BigDecimal.ZERO);
            order.setTokenAmount(tokenAmount);
        } else {
            // El impuesto se agrega sobre la suma de items — los precios del
            // catálogo no lo incluyen. taxRate queda como snapshot del cliente
            // (sobrevive cambios de settings).
            BigDecimal taxAmount = total.multiply(rate)
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            order.setSubtotal(total);
            order.setTaxAmount(taxAmount);
            order.setTotal(total.add(taxAmount));
        }
        // efectivo recibido → cambio calculado server-side (solo CASH)
        if (request.paymentMethod() == Order.PaymentMethod.CASH
                && request.cashReceived() != null) {
            if (request.cashReceived().compareTo(order.getTotal()) < 0) {
                throw new BusinessException("CASH_INSUFFICIENT", "Cash received is less than the total");
            }
            order.setCashReceived(request.cashReceived());
            order.setChangeGiven(request.cashReceived().subtract(order.getTotal()));
        }
        // folio semanal consecutivo — con el lock del store no hay duplicados
        order.setTicketNumber(
                orderRepository.maxTicketNumberSince(storeId, weekStart(store.getClient().zone())) + 1);
        Order saved = orderRepository.save(order);
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("channel", saved.getChannel().name());
        details.put("orderType", saved.getOrderType().name());
        details.put("paymentMethod", saved.getPaymentMethod().name());
        details.put("total", saved.getTotal());
        details.put("items", saved.getItems().size());
        auditService.recordFor(store.getClient().getId(), storeId, "order", "create",
                saved.getId(), ticketLabel(saved), details);
        return OrderResponse.from(saved);
    }

    private static String ticketLabel(Order order) {
        return "#" + (order.getTicketNumber() != null ? order.getTicketNumber() : order.getId());
    }

    /** Lunes 00:00 de la semana actual en la zona del negocio (no la del server). */
    private Instant weekStart(ZoneId zone) {
        return LocalDate.now(zone)
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                .atStartOfDay(zone)
                .toInstant();
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> list(Long storeId, List<Order.Status> statuses) {
        return orderRepository.findAll(scope(storeId, statuses), Sort.by("createdAt").descending())
                .stream().map(OrderResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public Page<OrderResponse> listPage(Long storeId, List<Order.Status> statuses, Pageable pageable) {
        // historial: más reciente primero salvo sort explícito
        Pageable sorted = pageable.getSort().isUnsorted()
                ? PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                        Sort.by("createdAt").descending())
                : pageable;
        return orderRepository.findAll(scope(storeId, statuses), sorted).map(OrderResponse::from);
    }

    /** Admin sin tienda activa → todo el cliente; si no, la tienda que
     *  resuelve resolveScope (403 si no le toca). Statuses opcional —
     *  el KDS pide los activos en un solo request: ?status=PENDING,... */
    private Specification<Order> scope(Long storeId, List<Order.Status> statuses) {
        AuthUser auth = AuthContext.current();
        boolean wholeClient = storeId == null && auth.storeId() == null
                && auth.role() == User.Role.ADMIN;
        Long store = wholeClient ? null : storeService.resolveScope(storeId);
        return (root, query, cb) -> {
            var owner = wholeClient
                    ? cb.equal(root.get("store").get("client").get("id"), auth.clientId())
                    : cb.equal(root.get("store").get("id"), store);
            return statuses == null || statuses.isEmpty()
                    ? owner
                    : cb.and(owner, root.get("status").in(statuses));
        };
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
    public OrderResponse updateStatus(Long id, Order.Status target, Boolean refunded) {
        Order order = findScopedOrThrow(id);
        Order.Status previous = order.getStatus();
        if (!TRANSITIONS.get(order.getStatus()).contains(target)) {
            throw new BusinessException("INVALID_STATUS_CHANGE",
                    "Cannot move order from " + order.getStatus() + " to " + target);
        }
        if (target == Order.Status.CANCELLED) {
            // el staff marca si el dinero ya se devolvió — un canje de fichas
            // ($0) nunca es reembolso, aunque manden el flag
            boolean moneyRefund = Boolean.TRUE.equals(refunded)
                    && order.getPaymentMethod() != Order.PaymentMethod.TOKEN;
            order.setRefundStatus(moneyRefund
                    ? Order.RefundStatus.REFUNDED : Order.RefundStatus.NONE);
            order.setCancelledBy(userRepository.getReferenceById(AuthContext.current().userId()));
            order.setCancelledAt(Instant.now());
            order.getItems().forEach(item ->
                    inventoryService.increase(item.getProduct(), item.getQuantity()));
        }
        if (target == Order.Status.READY) {
            order.setReadyAt(java.time.Instant.now());
        } else if (target == Order.Status.COMPLETED) {
            order.setCompletedAt(java.time.Instant.now());
        }
        order.setStatus(target);
        Order saved = orderRepository.save(order);
        if (target == Order.Status.CANCELLED) {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("from", previous.name());
            details.put("refunded", saved.getRefundStatus() == Order.RefundStatus.REFUNDED);
            details.put("total", saved.getTotal());
            auditService.recordFor(saved.getStore().getClient().getId(),
                    saved.getStore().getId(), "order", "cancel",
                    saved.getId(), ticketLabel(saved), details);
        }
        return OrderResponse.from(saved);
    }

    /** Order must belong to a store the caller may operate. */
    public Order findScopedOrThrow(Long id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + id));
        storeService.resolveScope(order.getStore().getId());
        return order;
    }
}
