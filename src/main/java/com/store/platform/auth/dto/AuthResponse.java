package com.store.platform.auth.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.store.platform.user.User;

public record AuthResponse(
        /** El JWT viaja solo en la cookie httpOnly — nunca en el body. */
        @JsonIgnore
        String token,
        Long userId,
        String name,
        String email,
        User.Role role,
        Long clientId,
        /** Branding del tenant — null para SUPERADMIN. */
        String clientName,
        String clientLogoUrl,
        Long storeId) {
}
