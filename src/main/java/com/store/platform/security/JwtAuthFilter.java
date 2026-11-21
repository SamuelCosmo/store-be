package com.store.platform.security;

import com.store.platform.client.dto.ClientSettingsResponse;
import com.store.platform.module.Module;
import com.store.platform.module.ModuleService;
import com.store.platform.user.User;
import com.store.platform.user.UserRepository;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserRepository userRepository;

    public JwtAuthFilter(JwtService jwtService, UserRepository userRepository) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            try {
                JwtService.Parsed parsed = jwtService.parse(header.substring(7));
                AuthUser user = parsed.user();
                User entity = userRepository.findWithClientById(user.userId()).orElse(null);
                String rejected = rejectReason(parsed, entity);
                if (rejected != null) {
                    // sin autenticación → 401 → el front cierra la sesión
                    log.info("Token rejected userId={}: {}", user.userId(), rejected);
                } else {
                    List<SimpleGrantedAuthority> authorities = new ArrayList<>();
                    authorities.add(new SimpleGrantedAuthority("ROLE_" + user.role().name()));
                    // módulos efectivos por request — frescos, sin claims stale
                    var effective = ModuleService.effectiveModules(entity);
                    for (Module m : effective) {
                        authorities.add(new SimpleGrantedAuthority("MODULE_" + m.name()));
                    }
                    response.setHeader("X-Effective-Modules",
                            String.join(",", ModuleService.names(effective)));
                    var auth = new UsernamePasswordAuthenticationToken(user, null, authorities);
                    SecurityContextHolder.getContext().setAuthentication(auth);
                    // contexto para cada línea de log del request (RequestLogFilter limpia el MDC)
                    MDC.put("userId", String.valueOf(user.userId()));
                    MDC.put("clientId", String.valueOf(user.clientId()));
                    MDC.put("storeId", String.valueOf(user.storeId()));
                    MDC.put("role", user.role().name());
                }
            } catch (JwtException | IllegalArgumentException ignored) {
                // invalid token → request stays unauthenticated
            }
        }
        chain.doFilter(request, response);
    }

    /** null = token vigente. La firma ya es válida; aquí se revisa el estado
     *  actual del usuario: revocación (tokenVersion) y TTL por rol del tenant. */
    private String rejectReason(JwtService.Parsed parsed, User user) {
        if (user == null || user.getDeletedAt() != null) return "user deleted";
        if (!user.isActive()) return "user inactive";
        if (user.getTokenVersion() != parsed.version()) return "token revoked";
        if (user.getRole() != parsed.user().role()) return "role changed";
        // SUPERADMIN no tiene tenant → solo aplica el `exp` del JWT
        if (user.getClient() != null) {
            if (!user.getClient().isActive()) return "client suspended";
            int hours = ClientSettingsResponse.sessionHoursFor(user.getClient(), user.getRole().name());
            if (hours > 0 && parsed.issuedAt().plus(Duration.ofHours(hours)).isBefore(Instant.now())) {
                return "session expired (" + hours + "h for " + user.getRole() + ")";
            }
        }
        return null;
    }
}
