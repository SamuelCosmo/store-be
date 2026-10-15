package com.store.platform.client.dto;

import java.math.BigDecimal;
import java.util.Map;

public record ClientSettingsRequest(
        /** Nombre del negocio — editable por el ADMIN del tenant. */
        String name,
        /** URL del logo (subida via /api/uploads/brand); "" lo quita. */
        String logoUrl,
        String currency,
        BigDecimal taxRate,
        /** Precio de venta de una ficha — 0/null desactiva el módulo. */
        BigDecimal tokenPrice,
        Integer kioskReceiptSeconds,
        Map<String, Integer> sessionHours) {
}
