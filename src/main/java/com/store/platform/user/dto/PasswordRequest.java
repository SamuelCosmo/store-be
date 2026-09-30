package com.store.platform.user.dto;

import jakarta.validation.constraints.NotBlank;

/** Confirmación de identidad: contraseña del admin que tiene la sesión. */
public record PasswordRequest(
        @NotBlank String currentPassword) {
}
