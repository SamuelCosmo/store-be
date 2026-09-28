package com.store.platform.category;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    List<Category> findDistinctByStores_IdOrderByName(Long storeId);

    List<Category> findDistinctByStores_Client_IdOrderByName(Long clientId);

    boolean existsByStores_Client_IdAndNameIgnoreCase(Long clientId, String name);

    boolean existsByStores_Client_IdAndNameIgnoreCaseAndIdNot(Long clientId, String name, Long id);
}
