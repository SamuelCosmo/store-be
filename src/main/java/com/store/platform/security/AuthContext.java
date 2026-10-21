package com.store.platform.security;

import com.store.platform.common.exception.BusinessException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class AuthContext {

    private AuthContext() {
    }

    public static AuthUser current() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof AuthUser user)) {
            throw new AccessDeniedException("Not authenticated");
        }
        return user;
    }

    /** Store scope for catalog/order operations — fails if the session has no active store. */
    public static Long requireStoreId() {
        Long storeId = current().storeId();
        if (storeId == null) {
            throw new BusinessException("NO_ACTIVE_STORE", "No active store — log in selecting a storeId");
        }
        return storeId;
    }
}
