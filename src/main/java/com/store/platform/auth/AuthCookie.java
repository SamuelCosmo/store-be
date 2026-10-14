package com.store.platform.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/** Cookie httpOnly que porta el JWT — JavaScript nunca lo lee. */
@Component
public class AuthCookie {

    public static final String NAME = "store_token";

    private final boolean secure;
    private final String sameSite;
    private final long maxAgeSeconds;

    public AuthCookie(@Value("${app.cookie.secure:false}") boolean secure,
                      @Value("${app.cookie.same-site:Lax}") String sameSite,
                      @Value("${app.jwt.expiration-ms}") long expirationMs) {
        this.secure = secure;
        this.sameSite = sameSite;
        this.maxAgeSeconds = expirationMs / 1000;
    }

    public ResponseCookie forToken(String token) {
        return build(token, maxAgeSeconds);
    }

    /** Cookie httpOnly no se puede borrar desde JS — solo el server expirándola. */
    public ResponseCookie clear() {
        return build("", 0);
    }

    private ResponseCookie build(String value, long maxAge) {
        return ResponseCookie.from(NAME, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite(sameSite)
                .path("/")
                .maxAge(maxAge)
                .build();
    }
}
