package com.store.platform.security;

import com.store.platform.auth.AuthCookie;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Set;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS");

    private final JwtService jwtService;
    private final List<String> corsOrigins;

    public JwtAuthFilter(JwtService jwtService,
                         @Value("${app.cors.origins:http://localhost:3000}") List<String> corsOrigins) {
        this.jwtService = jwtService;
        this.corsOrigins = corsOrigins;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String token = cookieToken(request);
        boolean fromCookie = token != null;
        // fallback Bearer: compat con clientes no-browser durante la migración
        if (token == null) {
            String header = request.getHeader("Authorization");
            if (header != null && header.startsWith("Bearer ")) {
                token = header.substring(7);
            }
        }
        if (token != null && (!fromCookie || trustedOrigin(request))) {
            try {
                AuthUser user = jwtService.parse(token);
                var auth = new UsernamePasswordAuthenticationToken(
                        user, null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + user.role().name())));
                SecurityContextHolder.getContext().setAuthentication(auth);
            } catch (JwtException | IllegalArgumentException ignored) {
                // invalid token → request stays unauthenticated
            }
        }
        chain.doFilter(request, response);
    }

    private String cookieToken(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;
        for (Cookie cookie : cookies) {
            if (AuthCookie.NAME.equals(cookie.getName())) return cookie.getValue();
        }
        return null;
    }

    /** CSRF: con cookie, un POST cross-site la enviaría sin preguntar. Las
     *  mutaciones autenticadas por cookie exigen Origin/Referer conocido —
     *  SameSite=Lax ya cubre navegación cross-site, esto cubre el caso
     *  SameSite=None (prod: front y back en dominios Railway distintos). */
    private boolean trustedOrigin(HttpServletRequest request) {
        if (SAFE_METHODS.contains(request.getMethod())) return true;
        String origin = request.getHeader("Origin");
        if (origin == null) origin = request.getHeader("Referer");
        if (origin == null) return false;
        return corsOrigins.stream().anyMatch(origin::startsWith);
    }
}
