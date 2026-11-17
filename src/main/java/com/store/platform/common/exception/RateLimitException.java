package com.store.platform.common.exception;

/** Demasiados intentos → 429 (lo mapea GlobalExceptionHandler). */
public class RateLimitException extends BusinessException {

    public RateLimitException() {
        super("RATE_LIMITED", "Too many attempts — try again in 10 minutes");
    }
}
