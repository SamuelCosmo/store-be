package com.store.platform.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface AuditEventRepository extends JpaRepository<AuditEvent, Long> {

    /** Búsqueda del audit log — todos los filtros opcionales. clientId null =
     *  todos los tenants (solo SUPERADMIN llega así); el resto de filtros se
     *  combinan con AND. Orden default: más reciente primero. */
    @Query("""
            select e from AuditEvent e
            left join fetch e.client
            left join fetch e.store
            where (:clientId is null or e.client.id = :clientId)
              and (:entityType is null or e.entityType = :entityType)
              and (:action is null or e.action = :action)
              and (:storeId is null or e.store.id = :storeId)
              and e.createdAt >= coalesce(:from, e.createdAt)
              and e.createdAt <= coalesce(:to, e.createdAt)
            """)
    Page<AuditEvent> search(@Param("clientId") Long clientId,
                            @Param("entityType") String entityType,
                            @Param("action") String action,
                            @Param("storeId") Long storeId,
                            @Param("from") Instant from,
                            @Param("to") Instant to,
                            Pageable pageable);
}
