package com.store.platform.token;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface TokenSaleRepository extends JpaRepository<TokenSale, Long> {

    java.util.Optional<TokenSale> findByStoreIdAndIdempotencyKey(Long storeId, String idempotencyKey);

    /** Dashboard: {revenue, piezas} del rango — storeId null → todo el cliente. */
    @Query("select coalesce(sum(t.totalPrice), 0), coalesce(sum(t.quantity), 0) from TokenSale t "
            + "where t.createdAt between :from and :to "
            + "and (t.store.id = :storeId or (:storeId is null and t.store.client.id = :clientId))")
    Object[] sumInRange(@Param("storeId") Long storeId, @Param("clientId") Long clientId,
                        @Param("from") Instant from, @Param("to") Instant to);

    List<TokenSale> findByStoreIdAndCreatedAtBetweenOrderByCreatedAtDesc(
            Long storeId, Instant from, Instant to);

    List<TokenSale> findByStoreClientIdAndCreatedAtBetweenOrderByCreatedAtDesc(
            Long clientId, Instant from, Instant to);
}
