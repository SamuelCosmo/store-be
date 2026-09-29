package com.store.platform.product;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProductRepository extends JpaRepository<Product, Long> {

    List<Product> findByCategoryStores_Id(Long storeId);

    List<Product> findByCategoryStores_IdAndActiveTrue(Long storeId);

    List<Product> findDistinctByCategory_Stores_Client_Id(Long clientId);

    List<Product> findDistinctByCategory_Stores_Client_IdAndActiveTrue(Long clientId);

    List<Product> findByCategoryId(Long categoryId);

    List<Product> findByCategoryIdAndActiveTrue(Long categoryId);

    List<Product> findDistinctByExtras_Id(Long extraId);

    List<Product> findDistinctBySizes_Id(Long sizeId);
}
