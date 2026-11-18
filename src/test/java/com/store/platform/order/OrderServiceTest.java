package com.store.platform.order;

import com.store.platform.category.Category;
import com.store.platform.audit.AuditService;
import com.store.platform.client.Client;
import com.store.platform.common.exception.BusinessException;
import com.store.platform.inventory.InventoryService;
import com.store.platform.order.dto.OrderItemRequest;
import com.store.platform.order.dto.OrderRequest;
import com.store.platform.order.dto.OrderResponse;
import com.store.platform.product.Product;
import com.store.platform.product.ProductRepository;
import com.store.platform.security.AuthUser;
import com.store.platform.store.Store;
import com.store.platform.store.StoreRepository;
import com.store.platform.store.StoreService;
import com.store.platform.user.User;
import com.store.platform.user.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock OrderRepository orderRepository;
    @Mock ProductRepository productRepository;
    @Mock StoreRepository storeRepository;
    @Mock UserRepository userRepository;
    @Mock InventoryService inventoryService;
    @Mock StoreService storeService;
    @Mock AuditService auditService;
    @InjectMocks OrderService orderService;

    private Product product;
    private Store store;

    @BeforeEach
    void setUp() {
        Client client = Client.builder().id(1L).build();
        store = Store.builder().id(1L).client(client).build();
        Category category = Category.builder().id(1L).stores(Set.of(store)).build();
        product = Product.builder()
                .id(1L).category(category).name("Espresso")
                .price(new BigDecimal("3.50")).active(true).build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void authenticate(User.Role role) {
        AuthUser auth = new AuthUser(7L, 1L, 1L, role);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(auth, null, List.of()));
    }

    @Test
    void kioskRejectsCash() {
        authenticate(User.Role.CUSTOMER);
        OrderRequest request = new OrderRequest(Order.Channel.KIOSK, Order.OrderType.DINE_IN,
                Order.PaymentMethod.CASH, null, null,
                List.of(new OrderItemRequest(1L, 1, null, null, null)));
        BusinessException ex = assertThrows(BusinessException.class, () -> orderService.create(request, null));
        assertEquals("KIOSK_CARD_ONLY", ex.getCode());
        verifyNoInteractions(orderRepository);
    }

    @Test
    void customerCannotUsePosChannel() {
        authenticate(User.Role.CUSTOMER);
        OrderRequest request = new OrderRequest(Order.Channel.POS, Order.OrderType.TAKEAWAY,
                Order.PaymentMethod.CARD, null, null,
                List.of(new OrderItemRequest(1L, 1, null, null, null)));
        assertThrows(BusinessException.class, () -> orderService.create(request, null));
        verifyNoInteractions(orderRepository);
    }

    @Test
    void createComputesTotalFromServerSideUnitPrice() {
        authenticate(User.Role.EMPLOYEE);
        when(storeService.resolveScope(null)).thenReturn(1L);
        when(productRepository.findAllById(any())).thenReturn(List.of(product));
        when(storeRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(store));
        when(userRepository.getReferenceById(7L)).thenReturn(User.builder().id(7L).build());
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // cada línea del request es una línea de orden — no hay merge
        OrderRequest request = new OrderRequest(Order.Channel.POS, Order.OrderType.DINE_IN,
                Order.PaymentMethod.CASH, new BigDecimal("20.00"), null,
                List.of(new OrderItemRequest(1L, 1, null, null, null),
                        new OrderItemRequest(1L, 2, null, null, null)));

        OrderResponse response = orderService.create(request, null);

        assertEquals(new BigDecimal("10.50"), response.subtotal());
        assertEquals(new BigDecimal("10.50"), response.total());
        assertEquals(new BigDecimal("9.50"), response.changeGiven());
        assertEquals(2, response.items().size());
        verify(inventoryService).decrease(product, 1);
        verify(inventoryService).decrease(product, 2);
    }

    @Test
    void retryWithSameIdempotencyKeyReturnsExistingOrder() {
        authenticate(User.Role.EMPLOYEE);
        Order existing = Order.builder()
                .id(40L).store(store).user(User.builder().id(7L).build())
                .channel(Order.Channel.POS).orderType(Order.OrderType.DINE_IN)
                .paymentMethod(Order.PaymentMethod.CASH).status(Order.Status.PENDING)
                .refundStatus(Order.RefundStatus.NONE).total(BigDecimal.TEN)
                .idempotencyKey("pay-key-123").build();
        when(storeService.resolveScope(null)).thenReturn(1L);
        when(storeRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(store));
        when(orderRepository.findByStoreIdAndIdempotencyKey(1L, "pay-key-123"))
                .thenReturn(Optional.of(existing));

        OrderRequest request = new OrderRequest(Order.Channel.POS, Order.OrderType.DINE_IN,
                Order.PaymentMethod.CASH, null, null,
                List.of(new OrderItemRequest(1L, 1, null, null, null)));
        OrderResponse response = orderService.create(request, "pay-key-123");

        assertEquals(40L, response.id());
        verify(orderRepository, never()).save(any());
        verifyNoInteractions(inventoryService);
    }

    @Test
    void invalidIdempotencyKeyIsRejected() {
        authenticate(User.Role.EMPLOYEE);
        OrderRequest request = new OrderRequest(Order.Channel.POS, Order.OrderType.DINE_IN,
                Order.PaymentMethod.CASH, null, null,
                List.of(new OrderItemRequest(1L, 1, null, null, null)));
        assertThrows(BusinessException.class, () -> orderService.create(request, "bad key!"));
        verifyNoInteractions(orderRepository);
    }

    @Test
    void listIsScopedThroughResolveScope() {
        authenticate(User.Role.EMPLOYEE);
        when(storeService.resolveScope(2L))
                .thenThrow(new org.springframework.security.access.AccessDeniedException("no"));
        // tienda ajena → 403 antes de consultar
        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> orderService.list(2L, null));
        verifyNoInteractions(orderRepository);
    }

    @Test
    void cancelRestoresInventoryAndMarksRefund() {
        authenticate(User.Role.EMPLOYEE);
        Store store = Store.builder().id(1L).client(Client.builder().id(1L).build()).build();
        Order order = Order.builder()
                .id(5L).store(store).user(User.builder().id(7L).build())
                .status(Order.Status.PREPARING).refundStatus(Order.RefundStatus.NONE)
                .total(BigDecimal.TEN)
                .items(List.of(OrderItem.builder().product(product).quantity(2)
                        .unitPrice(BigDecimal.ONE).build()))
                .build();
        when(orderRepository.findById(5L)).thenReturn(Optional.of(order));
        when(storeService.resolveScope(1L)).thenReturn(1L);
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        OrderResponse response = orderService.updateStatus(5L, Order.Status.CANCELLED, true);

        assertEquals(Order.Status.CANCELLED, response.status());
        assertEquals(Order.RefundStatus.REFUNDED, response.refundStatus());
        verify(inventoryService).increase(product, 2);
    }

    @Test
    void cancelWithoutRefundFlagStaysNone() {
        authenticate(User.Role.EMPLOYEE);
        Store store = Store.builder().id(1L).client(Client.builder().id(1L).build()).build();
        Order order = Order.builder()
                .id(5L).store(store).user(User.builder().id(7L).build())
                .status(Order.Status.PREPARING).refundStatus(Order.RefundStatus.NONE)
                .total(BigDecimal.TEN)
                .items(List.of(OrderItem.builder().product(product).quantity(1)
                        .unitPrice(BigDecimal.ONE).build()))
                .build();
        when(orderRepository.findById(5L)).thenReturn(Optional.of(order));
        when(storeService.resolveScope(1L)).thenReturn(1L);
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        OrderResponse response = orderService.updateStatus(5L, Order.Status.CANCELLED, null);

        assertEquals(Order.RefundStatus.NONE, response.refundStatus());
    }

    @Test
    void tokenOrderCancelNeverCountsAsRefund() {
        authenticate(User.Role.EMPLOYEE);
        Store store = Store.builder().id(1L).client(Client.builder().id(1L).build()).build();
        Order order = Order.builder()
                .id(5L).store(store).user(User.builder().id(7L).build())
                .status(Order.Status.PENDING).refundStatus(Order.RefundStatus.NONE)
                .paymentMethod(Order.PaymentMethod.TOKEN).total(BigDecimal.ZERO)
                .items(List.of(OrderItem.builder().product(product).quantity(1)
                        .unitPrice(BigDecimal.ONE).build()))
                .build();
        when(orderRepository.findById(5L)).thenReturn(Optional.of(order));
        when(storeService.resolveScope(1L)).thenReturn(1L);
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // aunque el cajero marque "devuelto", un canje de $0 no es reembolso
        OrderResponse response = orderService.updateStatus(5L, Order.Status.CANCELLED, true);

        assertEquals(Order.RefundStatus.NONE, response.refundStatus());
    }

    @Test
    void cannotCancelOnceReady() {
        authenticate(User.Role.EMPLOYEE);
        Store store = Store.builder().id(1L).client(Client.builder().id(1L).build()).build();
        Order order = Order.builder().id(5L).store(store).status(Order.Status.READY).build();
        when(orderRepository.findById(5L)).thenReturn(Optional.of(order));
        when(storeService.resolveScope(1L)).thenReturn(1L);

        assertThrows(BusinessException.class,
                () -> orderService.updateStatus(5L, Order.Status.CANCELLED, null));
        verifyNoInteractions(inventoryService);
    }
}
