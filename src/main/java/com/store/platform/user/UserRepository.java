package com.store.platform.user;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmailIgnoreCaseAndDeletedAtIsNull(String email);

    /** El admin "principal" del tenant (el primero creado). */
    Optional<User> findFirstByClientIdAndRoleAndDeletedAtIsNullOrderByIdAsc(Long clientId, User.Role role);

    boolean existsByEmailIgnoreCaseAndDeletedAtIsNull(String email);

    boolean existsByEmailIgnoreCaseAndIdNotAndDeletedAtIsNull(String email, Long id);

    boolean existsByClientIdAndDeletedAtIsNull(Long clientId);

    List<User> findByClientIdAndDeletedAtIsNull(Long clientId);

    List<User> findByClientIdAndDeletedAtIsNotNullOrderByName(Long clientId);

    @Query("select u from User u where u.client.id = :clientId and u.deletedAt is null and ("
            + ":q = '' or lower(u.name) like concat('%', lower(:q), '%') "
            + "or lower(u.email) like concat('%', lower(:q), '%'))")
    Page<User> pageByClient(@Param("clientId") Long clientId, @Param("q") String q, Pageable pageable);

    /** IDs de tiendas asignadas al usuario (user_stores) — sin cargar entidades. */
    @Query("select s.id from User u join u.stores s where u.id = :userId")
    Set<Long> findStoreIds(@Param("userId") Long userId);
}
