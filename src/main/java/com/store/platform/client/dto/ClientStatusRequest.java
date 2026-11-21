package com.store.platform.client.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ClientStatusRequest(
        @NotNull Boolean active,
        @NotBlank String superadminPassword) {
}
