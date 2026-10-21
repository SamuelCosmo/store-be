package com.store.platform.common.exception;

import java.util.Map;

/** 409 — no hay inventario suficiente. */
public class InsufficientStockException extends BusinessException {

    public InsufficientStockException(String code, String message, Map<String, Object> params) {
        super(code, message, params);
    }
}
