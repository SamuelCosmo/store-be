package com.store.platform.store.dto;

import com.store.platform.store.Store;

public record StoreSettingsResponse(
        String menuName,
        int kitchenOkMin,
        int kitchenWarnMin,
        boolean kitchenSound) {

    public static StoreSettingsResponse from(Store store) {
        return new StoreSettingsResponse(
                store.getMenuName() != null ? store.getMenuName() : store.getName(),
                store.getKitchenOkMin() != null ? store.getKitchenOkMin() : 8,
                store.getKitchenWarnMin() != null ? store.getKitchenWarnMin() : 15,
                store.getKitchenSound() == null || store.getKitchenSound());
    }
}
