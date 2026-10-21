package com.store.platform.security;

import com.store.platform.user.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

@Service
public class JwtService {

    private final SecretKey key;
    private final long expirationMs;

    public JwtService(@Value("${app.jwt.secret}") String secret,
                      @Value("${app.jwt.expiration-ms}") long expirationMs) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
    }

    public String generate(User user, Long storeId) {
        var builder = Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .claim("email", user.getEmail())
                .claim("role", user.getRole().name())
                .claim("ver", user.getTokenVersion())
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plusMillis(expirationMs)))
                .signWith(key);
        // SUPERADMIN no tiene tenant — el claim queda ausente
        if (user.getClient() != null) {
            builder.claim("clientId", user.getClient().getId());
        }
        if (storeId != null) {
            builder.claim("storeId", storeId);
        }
        return builder.compact();
    }

    /** Firma válida + claims; si sigue vigente lo decide JwtAuthFilter contra la DB. */
    public record Parsed(AuthUser user, int version, Instant issuedAt) {
    }

    public Parsed parse(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        AuthUser user = new AuthUser(
                Long.valueOf(claims.getSubject()),
                claims.get("clientId", Long.class),
                claims.get("storeId", Long.class),
                User.Role.valueOf(claims.get("role", String.class)));
        Integer ver = claims.get("ver", Integer.class);
        return new Parsed(user, ver == null ? 0 : ver, claims.getIssuedAt().toInstant());
    }
}
