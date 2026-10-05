package com.store.platform.category;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    String SEARCH = "(:q = '' or lower(c.name) like concat('%', lower(:q), '%') "
            + "or lower(coalesce(c.description, '')) like concat('%', lower(:q), '%'))";

    List<Category> findDistinctByStores_IdOrderByName(Long storeId);

    List<Category> findDistinctByStores_Client_IdOrderByName(Long clientId);

    boolean existsByStores_Client_IdAndNameIgnoreCase(Long clientId, String name);

    boolean existsByStores_Client_IdAndNameIgnoreCaseAndIdNot(Long clientId, String name, Long id);

    // paginación para la tabla del admin

    @Query("select distinct c from Category c join c.stores s where s.id = :storeId and " + SEARCH)
    Page<Category> pageByStore(@Param("storeId") Long storeId, @Param("q") String q, Pageable pageable);

    @Query("select distinct c from Category c join c.stores s where s.client.id = :clientId and " + SEARCH)
    Page<Category> pageByClient(@Param("clientId") Long clientId, @Param("q") String q, Pageable pageable);

    // "products" no es property de Category — el sort por conteo va en queries dedicados
    // con countQuery explícita (el group by rompería el total de páginas derivado)

    @Query(value = "select c from Category c join c.stores s left join Product p on p.category = c "
            + "where s.id = :storeId and " + SEARCH + " group by c order by count(distinct p.id) asc",
            countQuery = "select count(distinct c) from Category c join c.stores s "
                    + "where s.id = :storeId and " + SEARCH)
    Page<Category> pageByStoreOrderByProductsAsc(@Param("storeId") Long storeId, @Param("q") String q,
                                                 Pageable pageable);

    @Query(value = "select c from Category c join c.stores s left join Product p on p.category = c "
            + "where s.id = :storeId and " + SEARCH + " group by c order by count(distinct p.id) desc",
            countQuery = "select count(distinct c) from Category c join c.stores s "
                    + "where s.id = :storeId and " + SEARCH)
    Page<Category> pageByStoreOrderByProductsDesc(@Param("storeId") Long storeId, @Param("q") String q,
                                                  Pageable pageable);

    @Query(value = "select c from Category c join c.stores s left join Product p on p.category = c "
            + "where s.client.id = :clientId and " + SEARCH + " group by c order by count(distinct p.id) asc",
            countQuery = "select count(distinct c) from Category c join c.stores s "
                    + "where s.client.id = :clientId and " + SEARCH)
    Page<Category> pageByClientOrderByProductsAsc(@Param("clientId") Long clientId, @Param("q") String q,
                                                  Pageable pageable);

    @Query(value = "select c from Category c join c.stores s left join Product p on p.category = c "
            + "where s.client.id = :clientId and " + SEARCH + " group by c order by count(distinct p.id) desc",
            countQuery = "select count(distinct c) from Category c join c.stores s "
                    + "where s.client.id = :clientId and " + SEARCH)
    Page<Category> pageByClientOrderByProductsDesc(@Param("clientId") Long clientId, @Param("q") String q,
                                                   Pageable pageable);
}
