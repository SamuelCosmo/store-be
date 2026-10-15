package com.store.platform.common.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Una línea por request: método, ruta, status, duración, IP + MDC
 * (requestId, userId, clientId, storeId, role — los pone JwtAuthFilter).
 * Errores (4xx/5xx) incluyen el body JSON con secretos enmascarados.
 * GET exitosos van a DEBUG (polling del KDS/POS) — LOG_LEVEL_REQUESTS=DEBUG
 * para verlos. El requestId lo genera el front (X-Request-Id) para que el
 * "ref" del toast coincida con el log.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestLogFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestLogFilter.class);
    private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9-]{1,64}");
    private static final Pattern SECRETS = Pattern.compile(
            "(\"[^\"]*(?:password|token|secret)[^\"]*\"\\s*:\\s*)\"(?:[^\"\\\\]|\\\\.)*\"",
            Pattern.CASE_INSENSITIVE);
    private static final int MAX_BODY = 4000;

    /** Enmascara valores string de claves *password*, *token*, *secret*. */
    static String redact(String json) {
        return SECRETS.matcher(json).replaceAll("$1\"***\"");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String id = request.getHeader("X-Request-Id");
        if (id == null || !SAFE_ID.matcher(id).matches()) id = UUID.randomUUID().toString();
        MDC.put("requestId", id);
        response.setHeader("X-Request-Id", id);
        var wrapped = new ContentCachingRequestWrapper(request, MAX_BODY);
        long start = System.nanoTime();
        try {
            chain.doFilter(wrapped, response);
        } finally {
            write(wrapped, response.getStatus(), (System.nanoTime() - start) / 1_000_000);
            MDC.clear();
        }
    }

    private void write(ContentCachingRequestWrapper req, int status, long ms) {
        String method = req.getMethod();
        if ("OPTIONS".equals(method)) return;
        // el reporte del front ya es su propia línea ("client …") — no duplicar
        if ("/api/logs".equals(req.getRequestURI()) && status < 400) return;
        String query = req.getQueryString();
        String line = method + " " + req.getRequestURI() + (query == null ? "" : "?" + query)
                + " -> " + status + " (" + ms + "ms) ip=" + clientIp(req);
        if (status >= 400) {
            String body = body(req);
            if (!body.isEmpty()) line += " body=" + body;
            if (status >= 500) log.error(line);
            else log.warn(line);
        } else if ("GET".equals(method)) {
            log.debug(line);
        } else {
            log.info(line);
        }
    }

    private static String body(ContentCachingRequestWrapper req) {
        String type = req.getContentType();
        if (type == null || !type.contains("json")) return "";
        String raw = new String(req.getContentAsByteArray(), StandardCharsets.UTF_8);
        return redact(raw).replaceAll("\\s+", " ");
    }

    private static String clientIp(HttpServletRequest req) {
        String forwarded = req.getHeader("X-Forwarded-For");
        return forwarded != null ? forwarded.split(",")[0].trim() : req.getRemoteAddr();
    }
}
