package com.store.platform.client.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** Edición del admin del tenant desde la plataforma. `adminPassword`
 *  opcional (vacío/null = conserva la actual); `superadminPassword`
 *  confirma la identidad del superadmin que hace el cambio. */
public record ClientAdminRequest(
        @NotBlank String adminName,
        @NotBlank @Email String adminEmail,
        @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).{8,}$",
                message = "must be 8+ chars with upper, lower and digit") String adminPassword,
        @NotBlank String superadminPassword) {
}
