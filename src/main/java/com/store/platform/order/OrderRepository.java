package com.store.platform.order;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Listados (tienda/cliente × status × lista/página) van por Specification
 *  — ver OrderService.scope. */
public interface OrderRepository extends JpaRepository<Order, Long>, JpaSpecificationExecutor<Order> {

    Optional<Order> findByStoreIdAndIdempotencyKey(Long storeId, String idempotencyKey);

    /** Mayor folio de la semana (desde `from`) — null-safe para el primero. */
    @Query("select coalesce(max(o.ticketNumber), 0) from Order o "
            + "where o.store.id = :storeId and o.createdAt >= :from")
    int maxTicketNumberSince(@Param("storeId") Long storeId, @Param("from") Instant from);

    @EntityGraph(attributePaths = { "items", "items.product", "user" })
    List<Order> findByStoreIdAndCreatedAtBetween(Long storeId, Instant from, Instant to);

    @EntityGraph(attributePaths = { "items", "items.product", "user" })
    List<Order> findByStoreClientIdAndCreatedAtBetween(Long clientId, Instant from, Instant to);
}
