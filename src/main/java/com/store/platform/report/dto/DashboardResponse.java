package com.store.platform.report.dto;

import java.math.BigDecimal;
import java.util.List;

/** Todo lo que alimenta /dashboard en una sola llamada. */
public record DashboardResponse(
        BigDecimal totalSales,
        long orders,
        BigDecimal avgTicket,
        long cancelledOrders,
        BigDecimal refundedTotal,
        List<Slice> channels,
        List<Slice> orderTypes,
        List<Slice> payments,
        List<HourlyPoint> hourly,
        List<TopProduct> topProducts,
        List<EmployeeSales> employees) {

    public record Slice(String key, long orders, BigDecimal total) {
    }

    public record HourlyPoint(int hour, BigDecimal total) {
    }

    public record TopProduct(Long productId, String name, long quantity, BigDecimal revenue) {
    }

    public record EmployeeSales(Long userId, String name, long orders, BigDecimal total) {
    }
}
