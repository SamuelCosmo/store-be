package com.store.platform.product;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ProductRepository extends JpaRepository<Product, Long> {

    /** Fragmento de búsqueda libre para las listas paginadas. */
    String SEARCH = "(:q = '' or lower(p.name) like concat('%', lower(:q), '%') "
            + "or lower(coalesce(p.description, '')) like concat('%', lower(:q), '%'))";

    // explicit JPQL: "CategoryStores" sin guión se parsea ambiguo por Spring Data
    // (en la práctica filtraba por category.id, no por category.stores.id)
    @Query("select distinct p from Product p join p.category.stores s where s.id = :storeId "
            + "and p.deletedAt is null")
    List<Product> findByCategoryStores_Id(Long storeId);

    @Query("select distinct p from Product p join p.category.stores s where s.id = :storeId "
            + "and p.active = true and p.deletedAt is null")
    List<Product> findByCategoryStores_IdAndActiveTrue(Long storeId);

    List<Product> findDistinctByCategory_Stores_Client_IdAndDeletedAtIsNull(Long clientId);

    List<Product> findDistinctByCategory_Stores_Client_IdAndDeletedAtIsNotNull(Long clientId);

    List<Product> findDistinctByCategory_Stores_Client_IdAndActiveTrueAndDeletedAtIsNull(Long clientId);

    List<Product> findByCategoryIdAndDeletedAtIsNull(Long categoryId);

    long countByCategoryIdAndDeletedAtIsNull(Long categoryId);

    List<Product> findByCategoryIdAndActiveTrueAndDeletedAtIsNull(Long categoryId);

    List<Product> findDistinctByExtras_IdAndDeletedAtIsNull(Long extraId);

    List<Product> findDistinctBySizes_IdAndDeletedAtIsNull(Long sizeId);

    // variantes paginadas para las tablas del admin (sort/search via Pageable+search)

    // subquery en vez de join+distinct: con DISTINCT Postgres exige que las
    // columnas del ORDER BY estén en el SELECT (rompería sort=category.name)
    @Query("select p from Product p where p.deletedAt is null and p.category.id in "
            + "(select c.id from Category c join c.stores s where s.id = :storeId) "
            + "and (:onlyActive = false or p.active = true) and " + SEARCH)
    Page<Product> pageByStore(@Param("storeId") Long storeId, @Param("q") String q,
                              @Param("onlyActive") boolean onlyActive, Pageable pageable);

    @Query("select p from Product p where p.deletedAt is null and p.category.id in "
            + "(select c.id from Category c join c.stores s where s.client.id = :clientId) "
            + "and (:onlyActive = false or p.active = true) and " + SEARCH)
    Page<Product> pageByClient(@Param("clientId") Long clientId, @Param("q") String q,
                               @Param("onlyActive") boolean onlyActive, Pageable pageable);

    @Query("select p from Product p where p.deletedAt is null and p.category.id = :categoryId "
            + "and (:onlyActive = false or p.active = true) and " + SEARCH)
    Page<Product> pageByCategory(@Param("categoryId") Long categoryId, @Param("q") String q,
                                 @Param("onlyActive") boolean onlyActive, Pageable pageable);
}
