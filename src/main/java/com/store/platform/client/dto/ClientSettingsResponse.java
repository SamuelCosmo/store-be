package com.store.platform.client.dto;

import com.store.platform.client.Client;

import java.math.BigDecimal;
import java.util.Map;

public record ClientSettingsResponse(
        String name,
        String logoUrl,
        String currency,
        BigDecimal taxRate,
        /** Precio de venta de una ficha — 0 = módulo fichas desactivado. */
        BigDecimal tokenPrice,
        int kioskReceiptSeconds,
        Map<String, Integer> sessionHours,
        /** Zona IANA del negocio — default America/Mexico_City. */
        String timezone) {

    private static final Map<String, Integer> DEFAULT_HOURS = Map.of(
            "ADMIN", 12,
            "MANAGER", 12,
            "EMPLOYEE", 8,
            "KITCHEN", 12,
            "CUSTOMER", 24);

    /** Horas de sesión del rol (0 = sin límite) — el valor del tenant o el default. */
    public static int sessionHoursFor(Client client, String role) {
        Map<String, Integer> custom = client.getSessionHours();
        Integer hours = custom != null && custom.get(role) != null
                ? custom.get(role)
                : DEFAULT_HOURS.get(role);
        return hours == null ? 0 : hours;
    }

    public static ClientSettingsResponse from(Client client) {
        return new ClientSettingsResponse(
                client.getName(),
                client.getLogoUrl(),
                client.getCurrency() != null ? client.getCurrency() : "MXN",
                client.getTaxRate() != null ? client.getTaxRate() : BigDecimal.ZERO,
                client.getTokenPrice() != null ? client.getTokenPrice() : BigDecimal.ZERO,
                client.getKioskReceiptSeconds() != null ? client.getKioskReceiptSeconds() : 10,
                client.getSessionHours() != null ? client.getSessionHours() : DEFAULT_HOURS,
                client.zone().getId());
    }
}
