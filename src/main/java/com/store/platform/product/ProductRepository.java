package com.store.platform.product;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProductRepository extends JpaRepository<Product, Long> {

    List<Product> findByCategoryStoreId(Long storeId);

    List<Product> findByCategoryStoreIdAndActiveTrue(Long storeId);

    List<Product> findByCategoryId(Long categoryId);

    List<Product> findByCategoryIdAndActiveTrue(Long categoryId);
}
