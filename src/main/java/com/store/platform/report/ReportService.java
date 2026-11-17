package com.store.platform.report;

import com.store.platform.client.ClientService;
import com.store.platform.order.Order;
import com.store.platform.order.OrderRepository;
import com.store.platform.report.dto.DashboardResponse;
import com.store.platform.report.dto.DashboardResponse.EmployeeSales;
import com.store.platform.report.dto.DashboardResponse.HourlyPoint;
import com.store.platform.report.dto.DashboardResponse.Slice;
import com.store.platform.report.dto.DashboardResponse.TopProduct;
import com.store.platform.security.AuthContext;
import com.store.platform.security.AuthUser;
import com.store.platform.store.StoreService;
import com.store.platform.token.TokenSaleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Agregados por SQL — el dashboard ya no carga entidades ni el grafo de
 *  items (ver OrderRepository); solo hourly/onTime traen filas ligeras
 *  porque el corte por hora depende de la zona del negocio. */
@Service
@RequiredArgsConstructor
public class ReportService {

    private final OrderRepository orderRepository;
    private final TokenSaleRepository tokenSaleRepository;
    private final StoreService storeService;
    private final ClientService clientService;

    @Transactional(readOnly = true)
    public DashboardResponse dashboard(Long storeId, LocalDate from, LocalDate to, int top) {
        AuthUser auth = AuthContext.current();
        // "hoy" y los cortes de día/hora en la zona del negocio, no la del server
        ZoneId zone = clientService.findOrThrow(auth.clientId()).zone();
        LocalDate toDate = to == null ? LocalDate.now(zone) : to;
        LocalDate fromDate = from == null ? toDate : from;
        Instant start = fromDate.atStartOfDay(zone).toInstant();
        Instant end = toDate.plusDays(1).atStartOfDay(zone).toInstant();

        // storeId explícito o el de sesión; null → consolida todo el cliente
        Long scope = storeId != null || auth.storeId() != null
                ? storeService.resolveScope(storeId) : null;
        Long clientId = auth.clientId();

        long soldCount = 0, cancelledCount = 0;
        BigDecimal totalSales = BigDecimal.ZERO, refunded = BigDecimal.ZERO;
        for (Object[] r : orderRepository.aggregateByStatus(scope, clientId, start, end)) {
            if (r[0] == Order.Status.CANCELLED) {
                cancelledCount += (long) r[2];
                if (r[1] == Order.RefundStatus.REFUNDED) {
                    refunded = refunded.add((BigDecimal) r[3]);
                }
            } else {
                soldCount += (long) r[2];
                totalSales = totalSales.add((BigDecimal) r[3]);
            }
        }

        // filas ligeras: bucket por hora (en la zona del negocio) + onTime/late
        Map<Integer, BigDecimal> byHour = new TreeMap<>();
        long onTime = 0, late = 0;
        Order.Status cancelled = Order.Status.CANCELLED;
        for (Object[] r : orderRepository.dashboardRows(scope, clientId, cancelled, start, end)) {
            Instant createdAt = (Instant) r[0];
            byHour.merge(createdAt.atZone(zone).getHour(), (BigDecimal) r[2], BigDecimal::add);
            Instant completedAt = (Instant) r[1];
            if (completedAt != null) {
                int warnMin = r[3] != null ? (int) r[3] : 15;
                if (Duration.between(createdAt, completedAt).toMinutes() <= warnMin) onTime++;
                else late++;
            }
        }

        // la venta de fichas también es ingreso de caja — el canje va en $0
        Object[] tokens = tokenSaleRepository.sumInRange(scope, clientId, start, end).get(0);

        return new DashboardResponse(
                totalSales,
                (BigDecimal) tokens[0],
                ((Number) tokens[1]).longValue(),
                soldCount,
                soldCount == 0 ? BigDecimal.ZERO
                        : totalSales.divide(BigDecimal.valueOf(soldCount), 2, RoundingMode.HALF_UP),
                cancelledCount,
                refunded,
                slices(orderRepository.aggregateByChannel(scope, clientId, cancelled, start, end)),
                slices(orderRepository.aggregateByOrderType(scope, clientId, cancelled, start, end)),
                slices(orderRepository.aggregateByPaymentMethod(scope, clientId, cancelled, start, end)),
                byHour.entrySet().stream()
                        .map(e -> new HourlyPoint(e.getKey(), e.getValue())).toList(),
                orderRepository.topProducts(scope, clientId, cancelled, start, end).stream()
                        .map(r -> new TopProduct((Long) r[0], (String) r[1],
                                ((Number) r[2]).longValue(), (BigDecimal) r[3]))
                        .limit(top).toList(),
                orderRepository.aggregateByUser(scope, clientId, cancelled, start, end).stream()
                        .map(r -> new EmployeeSales((Long) r[0], (String) r[1],
                                (long) r[2], (BigDecimal) r[3]))
                        .sorted(Comparator.comparing(EmployeeSales::total).reversed()).toList(),
                onTime,
                late);
    }

    private List<Slice> slices(List<Object[]> rows) {
        return rows.stream()
                .map(r -> new Slice(r[0].toString(), (long) r[1], (BigDecimal) r[2]))
                .sorted(Comparator.comparing(Slice::orders).reversed())
                .toList();
    }
}
