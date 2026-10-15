package com.store.platform.extra;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExtraRepository extends JpaRepository<Extra, Long> {

    List<Extra> findByClientIdAndDeletedAtIsNullOrderByName(Long clientId);

    List<Extra> findByClientIdAndDeletedAtIsNotNullOrderByName(Long clientId);
}
