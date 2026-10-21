package com.store.platform.store.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/** Campos null = conservar. warnMin > okMin se valida en StoreService
 *  (contra el valor resultante, por si solo llega uno de los dos). */
public record StoreSettingsRequest(
        @Size(max = 60) String menuName,
        @Min(1) @Max(240) Integer kitchenOkMin,
        @Min(1) @Max(240) Integer kitchenWarnMin,
        Boolean kitchenSound) {
}
