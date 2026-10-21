package com.store.platform.common.dto;

import java.time.Instant;
import java.util.Map;

/** `code` estable para que el front muestre un mensaje para el usuario
 *  (`message` es técnico, en inglés, para logs). */
public record ErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String code,
        String message,
        Map<String, Object> params,
        String path,
        Map<String, String> fieldErrors) {

    public static ErrorResponse of(int status, String error, String code, String message,
                                   Map<String, Object> params, String path) {
        return new ErrorResponse(Instant.now(), status, error, code, message, params, path, null);
    }

    public static ErrorResponse of(int status, String error, String code, String message,
                                   String path, Map<String, String> fieldErrors) {
        return new ErrorResponse(Instant.now(), status, error, code, message, null, path, fieldErrors);
    }
}
