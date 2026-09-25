package com.store.platform.store.dto;

import com.store.platform.store.Device;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record DeviceRequest(
        @NotNull Device.Type type,
        @NotBlank String name) {
}
