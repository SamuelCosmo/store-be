package com.store.platform.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ActiveRequest(
        @NotNull Boolean active,
        @NotBlank String currentPassword) {
}
