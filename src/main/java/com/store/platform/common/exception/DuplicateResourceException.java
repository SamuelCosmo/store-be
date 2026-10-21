package com.store.platform.common.exception;

import java.util.Map;

/** 409 — ya existe (correo, categoría…). */
public class DuplicateResourceException extends BusinessException {

    public DuplicateResourceException(String code, String message, Map<String, Object> params) {
        super(code, message, params);
    }
}
