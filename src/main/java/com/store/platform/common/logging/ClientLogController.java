package com.store.platform.common.logging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.store.platform.common.exception.BusinessException;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * POST: eventos del frontend (errores JS, fallos de API, crashes de React,
 * login/logout/expiración) → logger "client". Público: también hay errores
 * en la pantalla de login, antes de tener token.
 * GET: consulta de app_logs para la pantalla de SUPERADMIN.
 */
// ponytail: sin rate limit — tope de tamaño por evento; agregar límite por IP si alguien lo abusa
@RestController
@RequestMapping("/api/logs")
@RequiredArgsConstructor
public class ClientLogController {

    private static final Logger log = LoggerFactory.getLogger("client");
    private static final int MAX_CHARS = 32_768;

    private final ObjectMapper mapper;
    private final AppLogRepository repository;

    @PostMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void log(@RequestBody String raw) {
        if (raw.length() > MAX_CHARS) throw new BusinessException("Log payload too large");
        JsonNode event;
        try {
            event = mapper.readTree(raw);
        } catch (JsonProcessingException e) {
            throw new BusinessException("Malformed log event");
        }
        // el evento hereda el ref del request que reporta (api.error/api.network)
        // → filtrar por requestId trae la línea del back y la del navegador juntas
        String ref = event.path("data").path("requestId").asText("");
        if (ref.matches("[A-Za-z0-9-]{1,64}")) MDC.put("requestId", ref);
        String line = "client " + event.path("type").asText("?") + " "
                + RequestLogFilter.redact(raw).replaceAll("\\s+", " ");
        switch (event.path("level").asText("info")) {
            case "error" -> log.error(line);
            case "warn" -> log.warn(line);
            default -> log.info(line);
        }
    }

    @GetMapping
    @PreAuthorize("hasRole('SUPERADMIN')")
    public Page<AppLog> list(@RequestParam(required = false) String level,
                             @RequestParam(required = false) String q,
                             @RequestParam(required = false) String requestId,
                             @RequestParam(required = false) Long userId,
                             @RequestParam(required = false) Long clientId,
                             @RequestParam(required = false) Instant from,
                             @RequestParam(required = false) Instant to,
                             @RequestParam(defaultValue = "0") int page,
                             @RequestParam(defaultValue = "25") int size) {
        Specification<AppLog> spec = (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (level != null && !level.isBlank()) p.add(cb.equal(root.get("level"), level));
            if (requestId != null && !requestId.isBlank()) p.add(cb.equal(root.get("requestId"), requestId.trim()));
            if (userId != null) p.add(cb.equal(root.get("userId"), userId));
            if (clientId != null) p.add(cb.equal(root.get("clientId"), clientId));
            if (from != null) p.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));
            if (to != null) p.add(cb.lessThan(root.get("createdAt"), to));
            if (q != null && !q.isBlank()) {
                p.add(cb.like(cb.lower(root.get("message")), "%" + q.trim().toLowerCase() + "%"));
            }
            return cb.and(p.toArray(Predicate[]::new));
        };
        return repository.findAll(spec,
                PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, 100), Sort.by("id").descending()));
    }
}
