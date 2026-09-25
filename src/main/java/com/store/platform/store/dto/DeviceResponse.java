package com.store.platform.store.dto;

import com.store.platform.store.Device;

public record DeviceResponse(
        Long id,
        Long storeId,
        Device.Type type,
        String name,
        boolean active) {

    public static DeviceResponse from(Device device) {
        return new DeviceResponse(
                device.getId(),
                device.getStore().getId(),
                device.getType(),
                device.getName(),
                device.isActive());
    }
}
