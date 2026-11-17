package com.store.platform.common;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Ventanas fijas en memoria: un contador por llave que se reinicia al
 * vencer la ventana. Con una sola instancia alcanza; si el servicio crece a
 * varias réplicas hay que mover los contadores a la base/Redis.
 */
@Component
public class RateLimiter {

    private record Window(AtomicInteger count, long resetAt) {
    }

    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    /** Cuenta el intento; devuelve true si sigue dentro del máximo. */
    public boolean tryAcquire(String key, int max, long windowMs) {
        return bucket(key, windowMs).count().incrementAndGet() <= max;
    }

    /** ¿La llave ya llegó al máximo dentro de su ventana vigente? */
    public boolean blocked(String key, int max) {
        Window w = windows.get(key);
        return w != null && w.count().get() >= max
                && System.currentTimeMillis() < w.resetAt();
    }

    /** Suma un fallo a la ventana vigente de la llave. */
    public void hit(String key, long windowMs) {
        bucket(key, windowMs).count().incrementAndGet();
    }

    /** Borra la llave (ej. login exitoso). */
    public void reset(String key) {
        windows.remove(key);
    }

    private Window bucket(String key, long windowMs) {
        long now = System.currentTimeMillis();
        // barreno solo cuando crece demasiado — no en cada llamada
        if (windows.size() > 10_000) {
            windows.values().removeIf(w -> now >= w.resetAt());
        }
        return windows.compute(key, (k, old) -> old == null || now >= old.resetAt()
                ? new Window(new AtomicInteger(0), now + windowMs)
                : old);
    }
}
