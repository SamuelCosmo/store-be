package com.store.platform.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.store.platform.category.Category;
import com.store.platform.client.Client;
import com.store.platform.order.Order;
import com.store.platform.order.OrderItem;
import com.store.platform.order.OrderRepository;
import com.store.platform.product.Product;
import com.store.platform.store.Store;
import com.store.platform.token.TokenSale;
import com.store.platform.token.TokenSaleRepository;
import com.store.platform.user.User;
import com.store.platform.user.UserRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.hibernate.exception.ConstraintViolationException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Queries críticas contra Postgres real (HP-QA-04). Flyway aplica V1+V2 al
 * contenedor y Hibernate corre en ddl-auto: validate — si una migración se
 * desvía de las entidades, este test truena antes de llegar a producción.
 * Cubre: scope multi-tenant de los agregados, folio semanal, constraints de
 * idempotencia y unicidad/soft-delete de usuarios.
 */
@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class PostgresRepositoryIT {

    @Container
    @ServiceConnection
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        // docker-java llama /info sin versionar; Docker Desktop reciente
        // (Engine ≥28, API mín. 1.40) responde 400 → "Could not find a valid
        // Docker environment". 1.44 = Docker ≥25. Override: -Dapi.version=...
        if (System.getProperty("api.version") == null) {
            System.setProperty("api.version", "1.44");
        }
    }

    @Autowired TestEntityManager em;
    @Autowired OrderRepository orders;
    @Autowired TokenSaleRepository tokenSales;
    @Autowired UserRepository users;

    private Client client;
    private Store store;
    private User user;
    private Product product;
    // tenant ajeno — el scope nunca debe leerlo
    private Store foreignStore;
    private User foreignUser;

    @BeforeEach
    void seed() {
        client = em.persist(Client.builder().name("Tenant A")
                .maxStores(5).maxPosPerStore(3).maxKiosksPerStore(3).build());
        Client foreign = em.persist(Client.builder().name("Tenant B")
                .maxStores(5).maxPosPerStore(3).maxKiosksPerStore(3).build());
        store = em.persist(Store.builder().client(client).name("Sucursal A")
                .businessType(Store.BusinessType.RESTAURANT).build());
        foreignStore = em.persist(Store.builder().client(foreign).name("Sucursal B")
                .businessType(Store.BusinessType.RESTAURANT).build());
        user = em.persist(User.builder().client(client).name("Ana")
                .email("ana@a.com").password("x").role(User.Role.EMPLOYEE).build());
        foreignUser = em.persist(User.builder().client(foreign).name("Beto")
                .email("beto@b.com").password("x").role(User.Role.EMPLOYEE).build());
        Category cat = em.persist(Category.builder().name("Comida").build());
        product = em.persist(Product.builder().category(cat).name("Ramen")
                .price(new BigDecimal("100.00")).active(true).tokenCost(0).build());
        em.flush();
        em.clear();
    }

    private Order order(Store s, User u, Order.Status status,
            Order.PaymentMethod pay, String total, Integer ticket) {
        return em.persist(Order.builder()
                .store(s).user(u)
                .channel(Order.Channel.POS)
                .orderType(Order.OrderType.DINE_IN)
                .paymentMethod(pay).status(status)
                .refundStatus(Order.RefundStatus.NONE)
                .total(new BigDecimal(total))
                .subtotal(new BigDecimal(total))
                .ticketNumber(ticket)
                .build());
    }

    @Test
    void maxTicketNumberSince_respectsStoreAndWindow() {
        order(store, user, Order.Status.COMPLETED, Order.PaymentMethod.CASH, "100.00", 41);
        order(store, user, Order.Status.COMPLETED, Order.PaymentMethod.CASH, "100.00", 42);
        order(foreignStore, foreignUser, Order.Status.COMPLETED, Order.PaymentMethod.CASH, "100.00", 99);
        em.flush();

        Instant yesterday = Instant.now().minus(1, ChronoUnit.DAYS);
        assertThat(orders.maxTicketNumberSince(store.getId(), yesterday)).isEqualTo(42);
        // fuera de la ventana → 0 (no null)
        assertThat(orders.maxTicketNumberSince(store.getId(), Instant.now().plus(1, ChronoUnit.HOURS))).isZero();
    }

    @Test
    void aggregateByStatus_scopesToClientAndExcludesForeign() {
        order(store, user, Order.Status.COMPLETED, Order.PaymentMethod.CASH, "100.00", 1);
        order(store, user, Order.Status.COMPLETED, Order.PaymentMethod.CASH, "50.00", 2);
        order(store, user, Order.Status.CANCELLED, Order.PaymentMethod.CASH, "70.00", 3);
        order(foreignStore, foreignUser, Order.Status.COMPLETED, Order.PaymentMethod.CASH, "999.00", 4);
        em.flush();

        Instant from = Instant.now().minus(1, ChronoUnit.DAYS);
        Instant to = Instant.now().plus(1, ChronoUnit.DAYS);

        // scope por cliente (storeId null) — el agregado del tenant B no entra
        var rows = orders.aggregateByStatus(null, client.getId(), from, to);
        BigDecimal completedTotal = rows.stream()
                .filter(r -> r[0] == Order.Status.COMPLETED)
                .map(r -> (BigDecimal) r[3])
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long cancelledCount = rows.stream()
                .filter(r -> r[0] == Order.Status.CANCELLED)
                .mapToLong(r -> (Long) r[2]).sum();
        assertThat(completedTotal).isEqualByComparingTo("150.00");
        assertThat(cancelledCount).isEqualTo(1);

        // scope por tienda — igual, sin datos del tenant ajeno
        assertThat(orders.aggregateByStatus(store.getId(), client.getId(), from, to))
                .hasSize(2); // COMPLETED + CANCELLED de A, nada de B
    }

    @Test
    void aggregates_excludeCancelled() {
        order(store, user, Order.Status.COMPLETED, Order.PaymentMethod.CASH, "100.00", 1);
        order(store, user, Order.Status.CANCELLED, Order.PaymentMethod.CARD, "999.00", 2);
        order(store, user, Order.Status.READY, Order.PaymentMethod.TOKEN, "0.00", 3);
        em.flush();

        Instant from = Instant.now().minus(1, ChronoUnit.DAYS);
        Instant to = Instant.now().plus(1, ChronoUnit.DAYS);

        var byPayment = orders.aggregateByPaymentMethod(
                store.getId(), client.getId(), Order.Status.CANCELLED, from, to);
        assertThat(byPayment).extracting(r -> r[0])
                .containsExactlyInAnyOrder(Order.PaymentMethod.CASH, Order.PaymentMethod.TOKEN);

        var byChannel = orders.aggregateByChannel(
                store.getId(), client.getId(), Order.Status.CANCELLED, from, to);
        assertThat(byChannel).hasSize(1); // las 2 no-canceladas son POS
    }

    @Test
    void topProducts_ordersByRevenueAndExcludesCancelled() {
        Order cheap = order(store, user, Order.Status.COMPLETED, Order.PaymentMethod.CASH, "100.00", 1);
        em.persist(OrderItem.builder().order(cheap).product(product)
                .quantity(1).unitPrice(new BigDecimal("100.00")).build());
        Order pricey = order(store, user, Order.Status.COMPLETED, Order.PaymentMethod.CASH, "600.00", 2);
        em.persist(OrderItem.builder().order(pricey).product(product)
                .quantity(2).unitPrice(new BigDecimal("300.00")).build());
        Order cancelled = order(store, user, Order.Status.CANCELLED, Order.PaymentMethod.CASH, "9999.00", 3);
        em.persist(OrderItem.builder().order(cancelled).product(product)
                .quantity(99).unitPrice(new BigDecimal("999.00")).build());
        em.flush();

        Instant from = Instant.now().minus(1, ChronoUnit.DAYS);
        Instant to = Instant.now().plus(1, ChronoUnit.DAYS);

        var top = orders.topProducts(store.getId(), client.getId(), Order.Status.CANCELLED, from, to);
        assertThat(top).hasSize(1);
        assertThat(top.getFirst()[2]).isEqualTo(3L); // 1 + 2 unidades, sin la cancelada
        assertThat((BigDecimal) top.getFirst()[3]).isEqualByComparingTo("700.00");
    }

    @Test
    void orderIdempotencyKey_uniquePerStore() {
        Order first = order(store, user, Order.Status.COMPLETED, Order.PaymentMethod.CASH, "100.00", 1);
        first.setIdempotencyKey("key-123");
        Order sameKeyOtherStore = order(foreignStore, foreignUser,
                Order.Status.COMPLETED, Order.PaymentMethod.CASH, "100.00", 1);
        sameKeyOtherStore.setIdempotencyKey("key-123");
        em.flush();

        assertThat(orders.findByStoreIdAndIdempotencyKey(store.getId(), "key-123")).isPresent();
        assertThat(orders.findByStoreIdAndIdempotencyKey(foreignStore.getId(), "key-123")).isPresent();

        Order dup = order(store, user, Order.Status.COMPLETED, Order.PaymentMethod.CASH, "100.00", 2);
        dup.setIdempotencyKey("key-123");
        assertThatThrownBy(() -> em.flush())
                .isInstanceOf(ConstraintViolationException.class);
        em.clear();
    }

    @Test
    void tokenSaleIdempotencyKey_uniquePerStore() {
        TokenSale first = em.persist(TokenSale.builder()
                .store(store).user(user).quantity(5)
                .unitPrice(new BigDecimal("10.00")).totalPrice(new BigDecimal("50.00"))
                .paymentMethod(Order.PaymentMethod.CASH)
                .idempotencyKey("tok-1").build());
        em.persist(TokenSale.builder()
                .store(foreignStore).user(foreignUser).quantity(5)
                .unitPrice(new BigDecimal("10.00")).totalPrice(new BigDecimal("50.00"))
                .paymentMethod(Order.PaymentMethod.CASH)
                .idempotencyKey("tok-1").build());
        em.flush();

        assertThat(tokenSales.findByStoreIdAndIdempotencyKey(store.getId(), "tok-1"))
                .isPresent().get().extracting(TokenSale::getId).isEqualTo(first.getId());

        // con IDENTITY el INSERT ocurre en persist — el duplicado truena ahí
        assertThatThrownBy(() -> em.persist(TokenSale.builder()
                .store(store).user(user).quantity(5)
                .unitPrice(new BigDecimal("10.00")).totalPrice(new BigDecimal("50.00"))
                .paymentMethod(Order.PaymentMethod.CASH)
                .idempotencyKey("tok-1").build()))
                .isInstanceOf(ConstraintViolationException.class);
        em.clear();
    }

    @Test
    void userLookups_areCaseInsensitiveAndSkipSoftDeleted() {
        assertThat(users.findByEmailIgnoreCaseAndDeletedAtIsNull("ANA@A.COM")).isPresent();
        assertThat(users.existsByEmailIgnoreCaseAndDeletedAtIsNull("ana@a.com")).isTrue();
        assertThat(users.existsByEmailIgnoreCaseAndIdNotAndDeletedAtIsNull("ana@a.com", user.getId()))
                .isFalse();
        assertThat(users.existsByEmailIgnoreCaseAndIdNotAndDeletedAtIsNull("ana@a.com", foreignUser.getId()))
                .isTrue();

        // soft-delete → el email deja de resolver pero la fila sigue
        User u = users.findByEmailIgnoreCaseAndDeletedAtIsNull("ana@a.com").orElseThrow();
        u.setDeletedAt(Instant.now());
        em.flush();
        em.clear();
        assertThat(users.findByEmailIgnoreCaseAndDeletedAtIsNull("ana@a.com")).isEmpty();
        assertThat(users.findByClientIdAndDeletedAtIsNull(client.getId())).isEmpty();
    }
}
