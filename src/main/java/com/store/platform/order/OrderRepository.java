package com.store.platform.order;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface OrderRepository extends JpaRepository<Order, Long> {

    List<Order> findByStoreIdOrderByCreatedAtDesc(Long storeId);

    List<Order> findByStoreIdAndStatusOrderByCreatedAtDesc(Long storeId, Order.Status status);

    @EntityGraph(attributePaths = { "items", "items.product", "user" })
    List<Order> findByStoreIdAndCreatedAtBetween(Long storeId, Instant from, Instant to);

    @EntityGraph(attributePaths = { "items", "items.product", "user" })
    List<Order> findByStoreClientIdAndCreatedAtBetween(Long clientId, Instant from, Instant to);
}
