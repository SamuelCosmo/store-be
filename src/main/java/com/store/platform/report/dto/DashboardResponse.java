package com.store.platform.report.dto;

import java.math.BigDecimal;
import java.util.List;

/** Todo lo que alimenta /dashboard en una sola llamada. */
public record DashboardResponse(
        BigDecimal totalSales,
        /** Dinero por venta de fichas en el rango (entra a caja, no es canje). */
        BigDecimal tokenSales,
        /** Fichas vendidas (piezas) en el rango. */
        long tokensSold,
        long orders,
        BigDecimal avgTicket,
        long cancelledOrders,
        BigDecimal refundedTotal,
        List<Slice> channels,
        List<Slice> orderTypes,
        List<Slice> payments,
        List<HourlyPoint> hourly,
        List<TopProduct> topProducts,
        List<EmployeeSales> employees,
        // entregados dentro del umbral warnMin de su tienda vs después
        long onTimeOrders,
        long lateOrders) {

    public record Slice(String key, long orders, BigDecimal total) {
    }

    public record HourlyPoint(int hour, BigDecimal total) {
    }

    public record TopProduct(Long productId, String name, long quantity, BigDecimal revenue) {
    }

    public record EmployeeSales(Long userId, String name, long orders, BigDecimal total) {
    }
}
