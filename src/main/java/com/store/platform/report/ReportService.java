package com.store.platform.report;

import com.store.platform.order.Order;
import com.store.platform.order.OrderItem;
import com.store.platform.order.OrderRepository;
import com.store.platform.report.dto.DashboardResponse;
import com.store.platform.report.dto.DashboardResponse.EmployeeSales;
import com.store.platform.report.dto.DashboardResponse.HourlyPoint;
import com.store.platform.report.dto.DashboardResponse.Slice;
import com.store.platform.report.dto.DashboardResponse.TopProduct;
import com.store.platform.security.AuthContext;
import com.store.platform.security.AuthUser;
import com.store.platform.store.StoreService;
import com.store.platform.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReportService {

    private final OrderRepository orderRepository;
    private final StoreService storeService;

    @Transactional(readOnly = true)
    public DashboardResponse dashboard(Long storeId, LocalDate from, LocalDate to, int top) {
        AuthUser auth = AuthContext.current();
        LocalDate toDate = to == null ? LocalDate.now() : to;
        LocalDate fromDate = from == null ? toDate : from;
        Instant start = fromDate.atStartOfDay(ZoneId.systemDefault()).toInstant();
        Instant end = toDate.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant();

        List<Order> orders;
        if (storeId != null || auth.storeId() != null) {
            Long scope = storeService.resolveScope(storeId);
            orders = orderRepository.findByStoreIdAndCreatedAtBetween(scope, start, end);
        } else {
            // ADMIN sin tienda activa: consolida todo el cliente
            orders = orderRepository.findByStoreClientIdAndCreatedAtBetween(auth.clientId(), start, end);
        }

        List<Order> sold = orders.stream()
                .filter(o -> o.getStatus() != Order.Status.CANCELLED)
                .toList();
        List<Order> cancelled = orders.stream()
                .filter(o -> o.getStatus() == Order.Status.CANCELLED)
                .toList();

        BigDecimal totalSales = sumTotal(sold);
        BigDecimal refunded = cancelled.stream()
                .filter(o -> o.getRefundStatus() == Order.RefundStatus.REFUNDED)
                .map(Order::getTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // TODO: métrica de pedidos a tiempo vs atrasados (requiere timestamps de
        //  preparación/entrega en Order o un umbral de tiempo por tienda)
        return new DashboardResponse(
                totalSales,
                sold.size(),
                sold.isEmpty() ? BigDecimal.ZERO
                        : totalSales.divide(BigDecimal.valueOf(sold.size()), 2, RoundingMode.HALF_UP),
                cancelled.size(),
                refunded,
                slices(sold, Order::getChannel),
                slices(sold, Order::getOrderType),
                slices(sold, Order::getPaymentMethod),
                hourly(sold),
                topProducts(sold, top),
                byEmployee(sold));
    }

    private List<Slice> slices(List<Order> orders, java.util.function.Function<Order, ?> key) {
        Map<Object, List<Order>> grouped = orders.stream()
                .collect(Collectors.groupingBy(key, LinkedHashMap::new, Collectors.toList()));
        return grouped.entrySet().stream()
                .map(e -> new Slice(e.getKey().toString(), e.getValue().size(), sumTotal(e.getValue())))
                .sorted(Comparator.comparing(Slice::orders).reversed())
                .toList();
    }

    private List<HourlyPoint> hourly(List<Order> orders) {
        Map<Integer, BigDecimal> byHour = new TreeMap<>();
        for (Order o : orders) {
            int hour = o.getCreatedAt().atZone(ZoneId.systemDefault()).getHour();
            byHour.merge(hour, o.getTotal(), BigDecimal::add);
        }
        return byHour.entrySet().stream()
                .map(e -> new HourlyPoint(e.getKey(), e.getValue()))
                .toList();
    }

    private List<TopProduct> topProducts(List<Order> orders, int top) {
        Map<Long, long[]> qty = new LinkedHashMap<>();
        Map<Long, BigDecimal> revenue = new LinkedHashMap<>();
        Map<Long, String> names = new LinkedHashMap<>();
        for (Order o : orders) {
            for (OrderItem item : o.getItems()) {
                Long pid = item.getProduct().getId();
                qty.merge(pid, new long[] { item.getQuantity() }, (a, b) -> new long[] { a[0] + b[0] });
                revenue.merge(pid,
                        item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())),
                        BigDecimal::add);
                names.putIfAbsent(pid, item.getProduct().getName());
            }
        }
        List<TopProduct> list = new ArrayList<>(revenue.entrySet().stream()
                .map(e -> new TopProduct(e.getKey(), names.get(e.getKey()), qty.get(e.getKey())[0], e.getValue()))
                .sorted(Comparator.comparing(TopProduct::revenue).reversed())
                .toList());
        return list.size() > top ? list.subList(0, top) : list;
    }

    private List<EmployeeSales> byEmployee(List<Order> orders) {
        Map<User, List<Order>> grouped = orders.stream()
                .collect(Collectors.groupingBy(Order::getUser, LinkedHashMap::new, Collectors.toList()));
        return grouped.entrySet().stream()
                .map(e -> new EmployeeSales(e.getKey().getId(), e.getKey().getName(),
                        e.getValue().size(), sumTotal(e.getValue())))
                .sorted(Comparator.comparing(EmployeeSales::total).reversed())
                .toList();
    }

    private BigDecimal sumTotal(List<Order> orders) {
        return orders.stream().map(Order::getTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
