package com.store.platform.auth.dto;

import com.store.platform.user.User;

import java.util.List;

public record AuthResponse(
        String token,
        Long userId,
        String name,
        String email,
        User.Role role,
        Long clientId,
        /** Branding del tenant — null para SUPERADMIN. */
        String clientName,
        String clientLogoUrl,
        Long storeId,
        /** Módulos efectivos del usuario (user ∩ client) — el front oculta
         *  pantallas sin módulo. Vacío para sesiones sin tenant. */
        List<String> modules) {
}
