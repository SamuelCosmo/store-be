package com.store.platform.user.dto;

import com.store.platform.user.User;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.List;

public record UserRequest(
        @NotBlank String name,
        @NotBlank @Email String email,
        @NotBlank @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).{8,}$",
                message = "must be 8+ chars with upper, lower and digit") String password,
        @NotNull User.Role role,
        List<Long> storeIds,
        @NotBlank String currentPassword) {
}
