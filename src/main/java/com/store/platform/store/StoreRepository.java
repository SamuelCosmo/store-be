package com.store.platform.store;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface StoreRepository extends JpaRepository<Store, Long> {

    List<Store> findByClientId(Long clientId);

    long countByClientId(Long clientId);

    @Query("select s from Store s where s.client.id = :clientId and ("
            + ":q = '' or lower(s.name) like concat('%', lower(:q), '%') "
            + "or lower(coalesce(s.description, '')) like concat('%', lower(:q), '%') "
            + "or lower(coalesce(s.address, '')) like concat('%', lower(:q), '%'))")
    Page<Store> pageByClient(@Param("clientId") Long clientId, @Param("q") String q, Pageable pageable);
}
