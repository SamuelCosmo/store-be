package com.store.platform.store;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface StoreRepository extends JpaRepository<Store, Long> {

    List<Store> findByClientIdAndDeletedAtIsNull(Long clientId);

    long countByClientIdAndDeletedAtIsNull(Long clientId);

    @Query("select s from Store s where s.client.id = :clientId and s.deletedAt is null and ("
            + ":q = '' or lower(s.name) like concat('%', lower(:q), '%') "
            + "or lower(coalesce(s.description, '')) like concat('%', lower(:q), '%') "
            + "or lower(coalesce(s.address, '')) like concat('%', lower(:q), '%'))")
    Page<Store> pageByClient(@Param("clientId") Long clientId, @Param("q") String q, Pageable pageable);

    /** Lock de fila — serializa la asignación del folio semanal al crear
     *  órdenes concurrentes en la misma tienda. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Store s where s.id = :id and s.deletedAt is null")
    Optional<Store> findByIdForUpdate(@Param("id") Long id);
}
