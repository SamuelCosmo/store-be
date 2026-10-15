package com.store.platform.token;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface TokenSaleRepository extends JpaRepository<TokenSale, Long> {

    List<TokenSale> findByStoreIdAndCreatedAtBetweenOrderByCreatedAtDesc(
            Long storeId, Instant from, Instant to);

    List<TokenSale> findByStoreClientIdAndCreatedAtBetweenOrderByCreatedAtDesc(
            Long clientId, Instant from, Instant to);
}
