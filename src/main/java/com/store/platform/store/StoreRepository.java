package com.store.platform.store;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StoreRepository extends JpaRepository<Store, Long> {

    List<Store> findByClientId(Long clientId);

    long countByClientId(Long clientId);
}
