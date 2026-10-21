package com.store.platform.common;

import com.store.platform.common.exception.BusinessException;

import java.util.regex.Pattern;

/** Header `Idempotency-Key` de los cobros (órdenes y venta de fichas):
 *  un reintento con el mismo key devuelve lo ya creado en vez de duplicarlo.
 *  El caller debe buscar el existente bajo el lock del store. */
public final class Idempotency {

    private static final Pattern KEY = Pattern.compile("[A-Za-z0-9-]{8,64}");

    private Idempotency() {
    }

    /** null = sin key (no idempotente); inválido → 400. */
    public static String check(String key) {
        if (key != null && !KEY.matcher(key).matches()) {
            throw new BusinessException("Invalid Idempotency-Key");
        }
        return key;
    }
}
