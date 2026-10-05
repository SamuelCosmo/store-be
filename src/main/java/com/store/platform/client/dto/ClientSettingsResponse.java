package com.store.platform.client.dto;

import com.store.platform.client.Client;

import java.math.BigDecimal;
import java.util.Map;

public record ClientSettingsResponse(
        String currency,
        BigDecimal taxRate,
        int kioskReceiptSeconds,
        Map<String, Integer> sessionHours) {

    private static final Map<String, Integer> DEFAULT_HOURS = Map.of(
            "ADMIN", 12,
            "MANAGER", 12,
            "EMPLOYEE", 8,
            "KITCHEN", 12,
            "CUSTOMER", 24);

    public static ClientSettingsResponse from(Client client) {
        return new ClientSettingsResponse(
                client.getCurrency() != null ? client.getCurrency() : "MXN",
                client.getTaxRate() != null ? client.getTaxRate() : BigDecimal.ZERO,
                client.getKioskReceiptSeconds() != null ? client.getKioskReceiptSeconds() : 10,
                client.getSessionHours() != null ? client.getSessionHours() : DEFAULT_HOURS);
    }
}
