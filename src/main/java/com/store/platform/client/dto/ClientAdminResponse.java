package com.store.platform.client.dto;

import com.store.platform.user.User;

/** Datos del admin del tenant para el modal de la plataforma. */
public record ClientAdminResponse(Long id, String name, String email) {

    public static ClientAdminResponse from(User user) {
        return new ClientAdminResponse(user.getId(), user.getName(), user.getEmail());
    }
}
