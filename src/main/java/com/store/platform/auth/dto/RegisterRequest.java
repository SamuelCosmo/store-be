package com.store.platform.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotNull Long clientId,
        @NotBlank String name,
        @NotBlank @Email String email,
        @NotBlank @Size(min = 6) String password) {
}
