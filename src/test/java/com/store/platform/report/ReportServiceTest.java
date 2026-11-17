package com.store.platform.report;

import com.store.platform.client.Client;
import com.store.platform.client.ClientService;
import com.store.platform.order.Order;
import com.store.platform.order.OrderRepository;
import com.store.platform.report.dto.DashboardResponse;
import com.store.platform.security.AuthUser;
import com.store.platform.store.StoreService;
import com.store.platform.token.TokenSaleRepository;
import com.store.platform.user.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    @Mock OrderRepository orderRepository;
    @Mock TokenSaleRepository tokenSaleRepository;
    @Mock StoreService storeService;
    @Mock ClientService clientService;
    @InjectMocks ReportService reportService;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void authenticate() {
        AuthUser auth = new AuthUser(7L, 1L, 1L, User.Role.EMPLOYEE);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(auth, null, List.of()));
    }

    @Test
    void dashboardComposesAggregates() {
        authenticate();
        when(clientService.findOrThrow(1L))
                .thenReturn(Client.builder().id(1L).timezone("UTC").build());
        when(storeService.resolveScope(1L)).thenReturn(1L);

        // 3 vendidas ($90) + 2 canceladas ($30, una reembolsada de $20)
        when(orderRepository.aggregateByStatus(eq(1L), eq(1L), any(), any()))
                .thenReturn(List.of(
                        new Object[] { Order.Status.COMPLETED, Order.RefundStatus.NONE, 3L,
                                new BigDecimal("90.00") },
                        new Object[] { Order.Status.CANCELLED, Order.RefundStatus.NONE, 1L,
                                new BigDecimal("10.00") },
                        new Object[] { Order.Status.CANCELLED, Order.RefundStatus.REFUNDED, 1L,
                                new BigDecimal("20.00") }));

        Instant t0 = Instant.parse("2025-01-06T10:00:00Z"); // lunes, hora 10 UTC
        when(orderRepository.dashboardRows(eq(1L), eq(1L), any(), any(), any()))
                .thenReturn(List.of(
                        // a tiempo: 10 min <= warnMin 15
                        new Object[] { t0, t0.plusSeconds(600), new BigDecimal("30.00"), 15 },
                        // tarde: 20 min > 15
                        new Object[] { t0, t0.plusSeconds(1200), new BigDecimal("60.00"), 15 },
                        // sin completar: no cuenta en onTime/late, sí en hourly
                        new Object[] { t0.plusSeconds(3600), null, new BigDecimal("15.00"), null }));

        when(orderRepository.aggregateByChannel(eq(1L), eq(1L), any(), any(), any()))
                .thenReturn(List.of(
                        new Object[] { Order.Channel.POS, 2L, new BigDecimal("60.00") },
                        new Object[] { Order.Channel.KIOSK, 1L, new BigDecimal("30.00") }));
        when(orderRepository.aggregateByOrderType(eq(1L), eq(1L), any(), any(), any()))
                .thenReturn(List.<Object[]>of());
        when(orderRepository.aggregateByPaymentMethod(eq(1L), eq(1L), any(), any(), any()))
                .thenReturn(List.<Object[]>of());
        when(orderRepository.topProducts(eq(1L), eq(1L), any(), any(), any()))
                .thenReturn(List.of(
                        new Object[] { 1L, "Espresso", 4L, new BigDecimal("14.00") },
                        new Object[] { 2L, "Latte", 2L, new BigDecimal("9.00") }));
        when(orderRepository.aggregateByUser(eq(1L), eq(1L), any(), any(), any()))
                .thenReturn(List.<Object[]>of(
                        new Object[] { 7L, "Ana", 3L, new BigDecimal("90.00") }));
        when(tokenSaleRepository.sumInRange(eq(1L), eq(1L), any(), any()))
                .thenReturn(List.<Object[]>of(new Object[] { new BigDecimal("50.00"), 5L }));

        DashboardResponse d = reportService.dashboard(
                1L, java.time.LocalDate.of(2025, 1, 6), java.time.LocalDate.of(2025, 1, 6), 5);

        assertEquals(new BigDecimal("90.00"), d.totalSales());
        assertEquals(3, d.orders());
        assertEquals(new BigDecimal("30.00"), d.avgTicket());
        assertEquals(2, d.cancelledOrders());
        assertEquals(new BigDecimal("20.00"), d.refundedTotal());
        assertEquals(new BigDecimal("50.00"), d.tokenSales());
        assertEquals(5, d.tokensSold());
        assertEquals(1, d.onTimeOrders());
        assertEquals(1, d.lateOrders());
        // hourly: $90 en la hora 10 y $15 en la 11 (UTC = timezone del test)
        assertEquals(2, d.hourly().size());
        assertEquals(new BigDecimal("90.00"), d.hourly().get(0).total());
        assertEquals(new BigDecimal("15.00"), d.hourly().get(1).total());
        assertEquals("POS", d.channels().get(0).key());
        assertEquals(2, d.channels().get(0).orders());
        assertEquals(2, d.topProducts().size());
        assertEquals("Espresso", d.topProducts().get(0).name());
        assertEquals("Ana", d.employees().get(0).name());
    }
}
