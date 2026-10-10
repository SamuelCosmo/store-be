package com.store.platform.user;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmailIgnoreCase(String email);

    /** El admin "principal" del tenant (el primero creado). */
    Optional<User> findFirstByClientIdAndRoleOrderByIdAsc(Long clientId, User.Role role);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCaseAndIdNot(String email, Long id);

    boolean existsByClientId(Long clientId);

    List<User> findByClientId(Long clientId);

    @Query("select u from User u where u.client.id = :clientId and ("
            + ":q = '' or lower(u.name) like concat('%', lower(:q), '%') "
            + "or lower(u.email) like concat('%', lower(:q), '%'))")
    Page<User> pageByClient(@Param("clientId") Long clientId, @Param("q") String q, Pageable pageable);
}
