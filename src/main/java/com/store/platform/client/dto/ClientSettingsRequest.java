package com.store.platform.client.dto;

import java.math.BigDecimal;
import java.util.Map;

public record ClientSettingsRequest(
        String currency,
        BigDecimal taxRate,
        Integer kioskReceiptSeconds,
        Map<String, Integer> sessionHours) {
}
