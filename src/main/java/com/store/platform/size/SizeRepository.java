package com.store.platform.size;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SizeRepository extends JpaRepository<Size, Long> {

    List<Size> findByClientIdOrderByName(Long clientId);
}
