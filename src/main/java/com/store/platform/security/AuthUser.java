package com.store.platform.security;

import com.store.platform.user.User;

/** Authenticated principal carried in the SecurityContext — populated from JWT claims. */
public record AuthUser(Long userId, Long clientId, Long storeId, User.Role role) {
}
