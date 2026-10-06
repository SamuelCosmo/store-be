package com.store.platform.order;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface OrderRepository extends JpaRepository<Order, Long> {

    List<Order> findByStoreIdOrderByCreatedAtDesc(Long storeId);

    Page<Order> findByStoreId(Long storeId, Pageable pageable);

    Page<Order> findByStoreIdAndStatus(Long storeId, Order.Status status, Pageable pageable);

    long countByStoreIdAndCreatedAtGreaterThanEqual(Long storeId, Instant from);

    List<Order> findByStoreIdAndStatusOrderByCreatedAtDesc(Long storeId, Order.Status status);

    @EntityGraph(attributePaths = { "items", "items.product", "user" })
    List<Order> findByStoreIdAndCreatedAtBetween(Long storeId, Instant from, Instant to);

    @EntityGraph(attributePaths = { "items", "items.product", "user" })
    List<Order> findByStoreClientIdAndCreatedAtBetween(Long clientId, Instant from, Instant to);

    // admin sin tienda activa → órdenes de todo el cliente
    List<Order> findByStoreClientIdOrderByCreatedAtDesc(Long clientId);

    List<Order> findByStoreClientIdAndStatusOrderByCreatedAtDesc(Long clientId, Order.Status status);

    Page<Order> findByStoreClientId(Long clientId, Pageable pageable);

    Page<Order> findByStoreClientIdAndStatus(Long clientId, Order.Status status, Pageable pageable);
}
