package com.store.platform.security;

import com.store.platform.client.Client;
import com.store.platform.module.Module;
import com.store.platform.user.User;
import com.store.platform.user.UserRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JwtAuthFilterTest {

    private static final String SECRET = "test-secret-key-for-unit-tests-only-0123456789";

    private final UserRepository userRepository = mock(UserRepository.class);
    private final JwtService jwtService = new JwtService(SECRET, 3_600_000);
    private final JwtAuthFilter filter = new JwtAuthFilter(jwtService, userRepository);
    private User user;

    @BeforeEach
    void setUp() {
        Client client = Client.builder().id(1L).sessionHours(Map.of("EMPLOYEE", 8)).build();
        user = User.builder().id(5L).client(client).email("e@x.com").role(User.Role.EMPLOYEE).build();
        when(userRepository.findWithClientById(5L)).thenReturn(Optional.of(user));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private boolean authenticates(String token) throws Exception {
        SecurityContextHolder.clearContext();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
        return SecurityContextHolder.getContext().getAuthentication() != null;
    }

    @Test
    void validTokenAuthenticates() throws Exception {
        assertTrue(authenticates(jwtService.generate(user, 1L)));
    }

    @Test
    void revokedTokenIsRejected() throws Exception {
        String token = jwtService.generate(user, 1L);
        user.revokeTokens();
        assertFalse(authenticates(token));
    }

    @Test
    void inactiveUserIsRejected() throws Exception {
        String token = jwtService.generate(user, 1L);
        user.setActive(false);
        assertFalse(authenticates(token));
    }

    @Test
    void tokenOlderThanRoleSessionHoursIsRejected() throws Exception {
        // emitido hace 9h, EMPLOYEE tiene 8h configuradas en el tenant
        Instant issued = Instant.now().minus(9, ChronoUnit.HOURS);
        String token = Jwts.builder()
                .subject("5")
                .claim("role", "EMPLOYEE")
                .claim("clientId", 1L)
                .claim("ver", 0)
                .issuedAt(Date.from(issued))
                .expiration(Date.from(Instant.now().plus(1, ChronoUnit.DAYS)))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();
        assertFalse(authenticates(token));
    }

    @Test
    void responseExposesFreshEffectiveModulesHeader() throws Exception {
        String token = jwtService.generate(user, 1L);
        user.setModules(EnumSet.of(Module.POS, Module.TOKENS));

        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse res = new MockHttpServletResponse();
        filter.doFilter(req, res, new MockFilterChain());

        assertEquals("POS,TOKENS", res.getHeader("X-Effective-Modules"));

        // Revocación en caliente con el mismo JWT sin re-login
        user.setModules(EnumSet.of(Module.POS));
        MockHttpServletResponse res2 = new MockHttpServletResponse();
        filter.doFilter(req, res2, new MockFilterChain());

        assertEquals("POS", res2.getHeader("X-Effective-Modules"));
    }
}
