package com.store.platform.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RateLimiterTest {

    private final RateLimiter limiter = new RateLimiter();

    @Test
    void allowsUpToMaxThenBlocks() {
        for (int i = 0; i < 5; i++) {
            assertTrue(limiter.tryAcquire("k", 5, 60_000));
        }
        assertFalse(limiter.tryAcquire("k", 5, 60_000));
    }

    @Test
    void blockedOnlyWithinWindow() throws InterruptedException {
        limiter.hit("k", 20);
        limiter.hit("k", 20);
        assertTrue(limiter.blocked("k", 2));
        Thread.sleep(30);
        // ventana vencida → ya no bloquea y el siguiente hit abre una nueva
        assertFalse(limiter.blocked("k", 2));
    }

    @Test
    void resetClearsTheCounter() {
        limiter.hit("k", 60_000);
        limiter.hit("k", 60_000);
        limiter.reset("k");
        assertFalse(limiter.blocked("k", 2));
    }

    @Test
    void keysAreIndependent() {
        limiter.hit("a", 60_000);
        limiter.hit("a", 60_000);
        assertTrue(limiter.blocked("a", 2));
        assertFalse(limiter.blocked("b", 2));
    }
}
