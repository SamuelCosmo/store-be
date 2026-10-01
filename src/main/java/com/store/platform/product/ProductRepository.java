package com.store.platform.product;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ProductRepository extends JpaRepository<Product, Long> {

    // explicit JPQL: "CategoryStores" sin guión se parsea ambiguo por Spring Data
    // (en la práctica filtraba por category.id, no por category.stores.id)
    @Query("select distinct p from Product p join p.category.stores s where s.id = :storeId")
    List<Product> findByCategoryStores_Id(Long storeId);

    @Query("select distinct p from Product p join p.category.stores s where s.id = :storeId and p.active = true")
    List<Product> findByCategoryStores_IdAndActiveTrue(Long storeId);

    List<Product> findDistinctByCategory_Stores_Client_Id(Long clientId);

    List<Product> findDistinctByCategory_Stores_Client_IdAndActiveTrue(Long clientId);

    List<Product> findByCategoryId(Long categoryId);

    List<Product> findByCategoryIdAndActiveTrue(Long categoryId);

    List<Product> findDistinctByExtras_Id(Long extraId);

    List<Product> findDistinctBySizes_Id(Long sizeId);
}
