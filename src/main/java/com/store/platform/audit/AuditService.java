package com.store.platform.audit;

import com.store.platform.audit.dto.AuditEventResponse;
import com.store.platform.client.ClientRepository;
import com.store.platform.security.AuthContext;
import com.store.platform.security.AuthUser;
import com.store.platform.store.StoreRepository;
import com.store.platform.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Registra eventos del audit trail. Se llama desde los services mutantes
 *  dentro de la misma transacción — si la operación hace rollback, el evento
 *  también (nunca queda audit de algo que no pasó). Cubre datos maestros y
 *  acciones sensibles; las ventas/órdenes ya viven en sus propias tablas. */
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditEventRepository auditEventRepository;
    private final UserRepository userRepository;
    private final ClientRepository clientRepository;
    private final StoreRepository storeRepository;

    /** Evento con el scope del JWT (clientId/storeId del token). */
    public void record(String entityType, String action, Long entityId,
                       String entityLabel, Map<String, ?> details) {
        AuthUser auth = AuthContext.current();
        persist(auth, auth.clientId(), auth.storeId(), entityType, action, entityId, entityLabel, details);
    }

    /** Evento con scope explícito — SUPERADMIN operando un tenant (su JWT no
     *  trae clientId) o cualquier mutación donde la tienda/store del JWT no
     *  es la del recurso (ADMIN client-wide, MANAGER con ?storeId=). */
    public void recordFor(Long clientId, Long storeId, String entityType, String action,
                          Long entityId, String entityLabel, Map<String, ?> details) {
        persist(AuthContext.current(), clientId, storeId, entityType, action, entityId, entityLabel, details);
    }

    private void persist(AuthUser auth, Long clientId, Long storeId, String entityType, String action,
                         Long entityId, String entityLabel, Map<String, ?> details) {
        var actor = userRepository.findById(auth.userId()).orElse(null);
        auditEventRepository.save(AuditEvent.builder()
                .client(clientId != null ? clientRepository.getReferenceById(clientId) : null)
                .store(storeId != null ? storeRepository.getReferenceById(storeId) : null)
                .actorId(auth.userId())
                .actorName(actor != null ? actor.getName() : null)
                .actorEmail(actor != null ? actor.getEmail() : null)
                .actorRole(auth.role().name())
                .entityType(entityType)
                .action(action)
                .entityId(entityId)
                .entityLabel(entityLabel)
                .details(details == null ? null : new LinkedHashMap<>(details))
                .build());
    }

    /** {"campo": {"from": a, "to": b}} solo con los campos que cambiaron —
     *  details de un "update". Null-safe: from/to pueden ser null. */
    public static Map<String, Object> diff(Map<String, ?> before, Map<String, ?> after) {
        Map<String, Object> changes = new LinkedHashMap<>();
        after.forEach((field, now) -> {
            Object prev = before.get(field);
            if (!Objects.equals(prev, now)) {
                Map<String, Object> pair = new LinkedHashMap<>();
                pair.put("from", prev);
                pair.put("to", now);
                changes.put(field, pair);
            }
        });
        return changes;
    }

    /** Bitácora visible: ADMIN ve la de su tenant; SUPERADMIN puede filtrar
     *  por clientId o ver todo (null = todos los tenants). */
    @Transactional(readOnly = true)
    public Page<AuditEventResponse> search(Long clientId, String entityType, String action,
                                           Long storeId, Instant from, Instant to, Pageable pageable) {
        AuthUser auth = AuthContext.current();
        Long scope = auth.role() == com.store.platform.user.User.Role.SUPERADMIN
                ? clientId
                : auth.clientId();
        Pageable sorted = pageable.getSort().isUnsorted()
                ? org.springframework.data.domain.PageRequest.of(
                        pageable.getPageNumber(), pageable.getPageSize(),
                        Sort.by("createdAt").descending())
                : pageable;
        return auditEventRepository.search(scope, entityType, action, storeId, from, to, sorted)
                .map(AuditEventResponse::from);
    }
}
