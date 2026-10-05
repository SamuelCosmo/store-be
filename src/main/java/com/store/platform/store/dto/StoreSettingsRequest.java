package com.store.platform.store.dto;

public record StoreSettingsRequest(
        String menuName,
        Integer kitchenOkMin,
        Integer kitchenWarnMin,
        Boolean kitchenSound) {
}
