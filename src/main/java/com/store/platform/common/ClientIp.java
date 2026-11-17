package com.store.platform.common;

import jakarta.servlet.http.HttpServletRequest;

/** IP del cliente detrás del proxy de Railway (X-Forwarded-For) o directa. */
public final class ClientIp {

    private ClientIp() {
    }

    public static String from(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
