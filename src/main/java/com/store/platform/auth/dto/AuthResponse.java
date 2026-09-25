package com.store.platform.auth.dto;

import com.store.platform.user.User;

public record AuthResponse(
        String token,
        Long userId,
        String name,
        String email,
        User.Role role,
        Long clientId,
        Long storeId) {
}
