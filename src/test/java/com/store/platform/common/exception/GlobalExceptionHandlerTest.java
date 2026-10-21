package com.store.platform.common.exception;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.BadCredentialsException;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private final MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/orders");

    @Test
    void businessErrorCarriesCodeAndParams() {
        var res = handler.handleBusiness(new BusinessException("PRODUCT_UNAVAILABLE",
                "Product is not available: Ramen", Map.of("product", "Ramen")), request);
        assertEquals(400, res.getStatusCode().value());
        assertEquals("PRODUCT_UNAVAILABLE", res.getBody().code());
        assertEquals("Ramen", res.getBody().params().get("product"));
    }

    @Test
    void conflictKeepsCode() {
        var res = handler.handleConflict(new DuplicateResourceException("EMAIL_TAKEN",
                "Email already registered: a@b.com", Map.of("email", "a@b.com")), request);
        assertEquals(409, res.getStatusCode().value());
        assertEquals("EMAIL_TAKEN", res.getBody().code());
    }

    @Test
    void wrongPasswordIsDistinguishableFromExpiredSession() {
        var res = handler.handleAuth(new BadCredentialsException("x"), request);
        assertEquals(401, res.getStatusCode().value());
        assertEquals("INVALID_CREDENTIALS", res.getBody().code());
    }
}
