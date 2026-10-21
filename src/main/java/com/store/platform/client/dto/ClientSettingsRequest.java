package com.store.platform.client.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.Map;

/** Campos null = conservar. Moneda/zona/roles de sessionHours se validan
 *  además contra ISO 4217 / IANA / roles del tenant en ClientService. */
public record ClientSettingsRequest(
        /** Nombre del negocio — editable por el ADMIN del tenant. */
        @Size(max = 120) String name,
        /** URL del logo (subida via /api/uploads/brand); "" lo quita. */
        @Size(max = 500) String logoUrl,
        @Pattern(regexp = "^[A-Z]{3}$", message = "must be an ISO 4217 code") String currency,
        @DecimalMin("0") @DecimalMax("100") BigDecimal taxRate,
        /** Precio de venta de una ficha — 0/null desactiva el módulo. */
        @DecimalMin("0") BigDecimal tokenPrice,
        @Min(3) @Max(60) Integer kioskReceiptSeconds,
        Map<String, @Min(0) @Max(720) Integer> sessionHours,
        /** Zona IANA (ej. America/Mexico_City). */
        @Size(max = 64) String timezone) {
}
