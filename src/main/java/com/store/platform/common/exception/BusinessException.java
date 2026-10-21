package com.store.platform.common.exception;

import java.util.Map;

/**
 * Error de negocio → 400 (409 en las subclases de conflicto). `code` es
 * estable y el front lo traduce a un mensaje para el usuario (i18n
 * `err.<CODE>`, con `params` como variables); `message` queda en inglés
 * para logs/soporte. Sin code → el front muestra el genérico del status.
 */
public class BusinessException extends RuntimeException {

    private final String code;
    private final Map<String, Object> params;

    public BusinessException(String message) {
        this(null, message, Map.of());
    }

    public BusinessException(String code, String message) {
        this(code, message, Map.of());
    }

    public BusinessException(String code, String message, Map<String, Object> params) {
        super(message);
        this.code = code;
        this.params = params;
    }

    public String getCode() {
        return code;
    }

    public Map<String, Object> getParams() {
        return params;
    }
}
