package com.store.platform.order;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Listados (tienda/cliente × status × lista/página) van por Specification
 *  — ver OrderService.scope. Agregados del dashboard: SQL-side, sin cargar
 *  entidades; todos aceptan storeId nullable → scope cliente. */
public interface OrderRepository extends JpaRepository<Order, Long>, JpaSpecificationExecutor<Order> {

    String SCOPE = "and (o.store.id = :storeId or (:storeId is null and o.store.client.id = :clientId))";

    Optional<Order> findByStoreIdAndIdempotencyKey(Long storeId, String idempotencyKey);

    /** Mayor folio de la semana (desde `from`) — null-safe para el primero. */
    @Query("select coalesce(max(o.ticketNumber), 0) from Order o "
            + "where o.store.id = :storeId and o.createdAt >= :from")
    int maxTicketNumberSince(@Param("storeId") Long storeId, @Param("from") Instant from);

    /** Dashboard: conteo y total por (status, refundStatus) — una query. */
    @Query("select o.status, o.refundStatus, count(o), sum(o.total) from Order o "
            + "where o.createdAt between :from and :to " + SCOPE
            + " group by o.status, o.refundStatus")
    List<Object[]> aggregateByStatus(@Param("storeId") Long storeId, @Param("clientId") Long clientId,
                                     @Param("from") Instant from, @Param("to") Instant to);

    /** Dashboard: slice por dimensión (channel/orderType/paymentMethod) — solo vendidas. */
    @Query("select o.channel, count(o), sum(o.total) from Order o "
            + "where o.status <> :cancelled and o.createdAt between :from and :to " + SCOPE
            + " group by o.channel")
    List<Object[]> aggregateByChannel(@Param("storeId") Long storeId, @Param("clientId") Long clientId,
                                      @Param("cancelled") Order.Status cancelled,
                                      @Param("from") Instant from, @Param("to") Instant to);

    @Query("select o.orderType, count(o), sum(o.total) from Order o "
            + "where o.status <> :cancelled and o.createdAt between :from and :to " + SCOPE
            + " group by o.orderType")
    List<Object[]> aggregateByOrderType(@Param("storeId") Long storeId, @Param("clientId") Long clientId,
                                        @Param("cancelled") Order.Status cancelled,
                                        @Param("from") Instant from, @Param("to") Instant to);

    @Query("select o.paymentMethod, count(o), sum(o.total) from Order o "
            + "where o.status <> :cancelled and o.createdAt between :from and :to " + SCOPE
            + " group by o.paymentMethod")
    List<Object[]> aggregateByPaymentMethod(@Param("storeId") Long storeId, @Param("clientId") Long clientId,
                                            @Param("cancelled") Order.Status cancelled,
                                            @Param("from") Instant from, @Param("to") Instant to);

    /** Dashboard: ventas por usuario — incluye la cuenta genérica del kiosco. */
    @Query("select o.user.id, o.user.name, count(o), sum(o.total) from Order o "
            + "where o.status <> :cancelled and o.createdAt between :from and :to " + SCOPE
            + " group by o.user.id, o.user.name")
    List<Object[]> aggregateByUser(@Param("storeId") Long storeId, @Param("clientId") Long clientId,
                                   @Param("cancelled") Order.Status cancelled,
                                   @Param("from") Instant from, @Param("to") Instant to);

    /** Dashboard: filas ligeras (createdAt, completedAt, total, warnMin) para
     *  hourly y onTime/late — el corte por hora va en Java por la zona del
     *  negocio, que la base no conoce sin duplicarla en cada query. */
    @Query("select o.createdAt, o.completedAt, o.total, o.store.kitchenWarnMin from Order o "
            + "where o.status <> :cancelled and o.createdAt between :from and :to " + SCOPE)
    List<Object[]> dashboardRows(@Param("storeId") Long storeId, @Param("clientId") Long clientId,
                                 @Param("cancelled") Order.Status cancelled,
                                 @Param("from") Instant from, @Param("to") Instant to);

    /** Dashboard: top productos por revenue — vendidas solamente. */
    @Query("select i.product.id, i.product.name, sum(i.quantity), sum(i.unitPrice * i.quantity) "
            + "from OrderItem i where i.order.status <> :cancelled "
            + "and i.order.createdAt between :from and :to "
            + "and (i.order.store.id = :storeId or (:storeId is null and i.order.store.client.id = :clientId)) "
            + "group by i.product.id, i.product.name order by sum(i.unitPrice * i.quantity) desc")
    List<Object[]> topProducts(@Param("storeId") Long storeId, @Param("clientId") Long clientId,
                               @Param("cancelled") Order.Status cancelled,
                               @Param("from") Instant from, @Param("to") Instant to);
}
