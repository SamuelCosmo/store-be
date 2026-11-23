package com.store.platform.security;

import com.store.platform.audit.AuditEventRepository;
import com.store.platform.audit.AuditService;
import com.store.platform.category.Category;
import com.store.platform.category.CategoryRepository;
import com.store.platform.category.CategoryService;
import com.store.platform.client.Client;
import com.store.platform.client.ClientController;
import com.store.platform.client.ClientRepository;
import com.store.platform.client.ClientService;
import com.store.platform.client.dto.ClientSettingsRequest;
import com.store.platform.common.exception.BusinessException;
import com.store.platform.common.exception.ResourceNotFoundException;
import com.store.platform.extra.Extra;
import com.store.platform.extra.ExtraRepository;
import com.store.platform.extra.ExtraService;
import com.store.platform.inventory.InventoryRepository;
import com.store.platform.inventory.InventoryService;
import com.store.platform.inventory.dto.InventoryRequest;
import com.store.platform.module.Module;
import com.store.platform.module.ModuleService;
import com.store.platform.order.Order;
import com.store.platform.order.OrderRepository;
import com.store.platform.order.OrderService;
import com.store.platform.product.Product;
import com.store.platform.product.ProductRepository;
import com.store.platform.product.ProductService;
import com.store.platform.report.ReportService;
import com.store.platform.size.Size;
import com.store.platform.size.SizeRepository;
import com.store.platform.size.SizeService;
import com.store.platform.storage.StorageService;
import com.store.platform.store.DeviceRepository;
import com.store.platform.store.Store;
import com.store.platform.store.StoreRepository;
import com.store.platform.store.StoreService;
import com.store.platform.token.TokenSaleRepository;
import com.store.platform.token.TokenService;
import com.store.platform.token.dto.TokenSaleRequest;
import com.store.platform.user.User;
import com.store.platform.user.UserRepository;
import com.store.platform.user.UserService;
import com.store.platform.user.dto.PasswordRequest;
import com.store.platform.user.dto.UserUpdateRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** HP-QA-05 — aislamiento cross-tenant: un actor del tenant A (o de una
 *  tienda no asignada) nunca puede leer ni mutar datos del tenant B, en
 *  ningún módulo. Los services se instancian reales sobre repos mockeados
 *  para que el test truene si alguien quita el check de scoping. */
@ExtendWith(MockitoExtension.class)
class CrossTenantIsolationTest {

    // repos
    @Mock StoreRepository storeRepository;
    @Mock UserRepository userRepository;
    @Mock ClientRepository clientRepository;
    @Mock DeviceRepository deviceRepository;
    @Mock ProductRepository productRepository;
    @Mock CategoryRepository categoryRepository;
    @Mock OrderRepository orderRepository;
    @Mock ExtraRepository extraRepository;
    @Mock SizeRepository sizeRepository;
    @Mock InventoryRepository inventoryRepository;
    @Mock TokenSaleRepository tokenSaleRepository;
    @Mock AuditEventRepository auditEventRepository;

    // services como dependencia (no bajo prueba directa)
    @Mock StorageService storageService;
    @Mock AuditService auditService;
    @Mock StoreService storeService;
    @Mock ProductService productService;
    @Mock CategoryService categoryService;
    @Mock ExtraService extraService;
    @Mock SizeService sizeService;
    @Mock InventoryService inventoryService;
    @Mock ClientService clientService;
    @Mock ModuleService moduleService;

    // services bajo prueba — reales sobre repos mockeados
    private StoreService storeScope;
    private ProductService productServiceSut;
    private CategoryService categoryServiceSut;
    private OrderService orderServiceSut;
    private TokenService tokenServiceSut;
    private UserService userServiceSut;
    private AuditService auditServiceSut;
    private ModuleService moduleServiceSut;
    private InventoryService inventoryServiceSut;
    private ExtraService extraServiceSut;
    private SizeService sizeServiceSut;
    private ReportService reportServiceSut;
    private ClientController clientController;

    private final Client clientA = Client.builder().id(1L).name("Tenant A").build();
    private final Client clientB = Client.builder().id(2L).name("Tenant B").build();
    private final Store storeA = Store.builder().id(1L).client(clientA).name("Store A").build();
    private final Store storeB = Store.builder().id(99L).client(clientB).name("Store B").build();

    @BeforeEach
    void setUp() {
        storeScope = new StoreService(storeRepository, deviceRepository, clientRepository,
                userRepository, storageService, auditService);
        categoryServiceSut = new CategoryService(categoryRepository, productRepository,
                storeService, auditService);
        productServiceSut = new ProductService(productRepository, categoryService, storeService,
                extraService, sizeService, storageService, auditService);
        orderServiceSut = new OrderService(orderRepository, productRepository, storeRepository,
                userRepository, inventoryService, storeService, auditService);
        tokenServiceSut = new TokenService(tokenSaleRepository, storeRepository, userRepository,
                storeService, clientService, auditService);
        userServiceSut = new UserService(userRepository, clientRepository, storeRepository, auditService);
        auditServiceSut = new AuditService(auditEventRepository, userRepository, clientRepository,
                storeRepository);
        moduleServiceSut = new ModuleService(clientRepository, userRepository, auditService);
        inventoryServiceSut = new InventoryService(inventoryRepository, productService, auditService);
        extraServiceSut = new ExtraService(extraRepository, clientRepository, productRepository,
                auditService);
        sizeServiceSut = new SizeService(sizeRepository, clientRepository, productRepository,
                auditService);
        reportServiceSut = new ReportService(orderRepository, tokenSaleRepository, storeService,
                clientService);
        clientController = new ClientController(clientService, storeService, moduleService);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void login(Long userId, Long clientId, Long storeId, User.Role role) {
        AuthUser auth = new AuthUser(userId, clientId, storeId, role);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(auth, null, List.of()));
    }

    // ── Stores / scope ─────────────────────────────────────────────────

    @Test
    void adminCannotScopeStoreOfAnotherTenant() {
        login(1L, 1L, null, User.Role.ADMIN);
        when(storeRepository.findById(99L)).thenReturn(Optional.of(storeB));

        assertThrows(AccessDeniedException.class, () -> storeScope.resolveScope(99L));
    }

    @Test
    void employeeCannotScopeUnassignedOrForeignStore() {
        login(7L, 1L, 1L, User.Role.EMPLOYEE);
        when(userRepository.findStoreIds(7L)).thenReturn(Set.of(1L));

        assertThrows(AccessDeniedException.class, () -> storeScope.resolveScope(99L));
        assertFalse(storeScope.canScope(99L));
    }

    // ── Client endpoints ───────────────────────────────────────────────

    @Test
    void clientEndpointsRejectForeignTenantId() {
        login(1L, 1L, null, User.Role.ADMIN);

        assertThrows(AccessDeniedException.class, () -> clientController.getById(2L));
        assertThrows(AccessDeniedException.class, () -> clientController.getSettings(2L));
        assertThrows(AccessDeniedException.class,
                () -> clientController.listStores(2L, null, null, null, null, Pageable.unpaged()));
        assertThrows(AccessDeniedException.class, () -> clientController.updateSettings(2L,
                new ClientSettingsRequest(null, null, null, null, null, null, null, null)));
        verifyNoInteractions(clientService);
    }

    @Test
    void clientEndpointsRejectSuperadminWithoutTenant() {
        login(1L, null, null, User.Role.SUPERADMIN);

        // SUPERADMIN no tiene clientId — cae en el mismo AccessDenied
        assertThrows(AccessDeniedException.class, () -> clientController.getById(2L));
        verifyNoInteractions(clientService);
    }

    // ── Usuarios ───────────────────────────────────────────────────────

    @Test
    void deletedUserOfAnotherTenantIsInvisibleToRestore() {
        login(1L, 1L, null, User.Role.ADMIN);
        User foreign = User.builder().id(50L).client(clientB).name("B").email("b@b.com")
                .role(User.Role.EMPLOYEE).build();
        foreign.setDeletedAt(Instant.now());
        when(userRepository.findById(50L)).thenReturn(Optional.of(foreign));

        assertThrows(ResourceNotFoundException.class, () -> userServiceSut.restore(50L));
        verify(userRepository, never()).save(any());
    }

    @Test
    void foreignTenantUserIsInvisibleToSetActive() {
        login(1L, 1L, null, User.Role.ADMIN);
        User self = User.builder().id(1L).client(clientA)
                .password(new BCryptPasswordEncoder().encode("pw")).build();
        User foreign = User.builder().id(50L).client(clientB).name("B").email("b@b.com")
                .role(User.Role.EMPLOYEE).build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(self));
        when(userRepository.findById(50L)).thenReturn(Optional.of(foreign));

        assertThrows(ResourceNotFoundException.class,
                () -> userServiceSut.setActive(50L, false, new PasswordRequest("pw")));
        verify(userRepository, never()).save(any());
    }

    @Test
    void userCannotBeAssignedStoresOfAnotherTenant() {
        login(1L, 1L, null, User.Role.ADMIN);
        User self = User.builder().id(1L).client(clientA)
                .password(new BCryptPasswordEncoder().encode("pw")).build();
        User own = User.builder().id(50L).client(clientA).name("E").email("e@a.com")
                .role(User.Role.EMPLOYEE).build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(self));
        when(userRepository.findById(50L)).thenReturn(Optional.of(own));
        when(storeRepository.findAllById(List.of(99L))).thenReturn(List.of(storeB));

        UserUpdateRequest request = new UserUpdateRequest("E", "e@a.com", null,
                User.Role.EMPLOYEE, List.of(99L), "pw");
        BusinessException ex = assertThrows(BusinessException.class,
                () -> userServiceSut.update(50L, request));
        assertEquals("STORE_OTHER_CLIENT", ex.getCode());
        verify(userRepository, never()).save(any());
    }

    // ── Catálogo ───────────────────────────────────────────────────────

    @Test
    void productOfAnotherTenantIsInvisible() {
        login(7L, 1L, 1L, User.Role.EMPLOYEE);
        Category categoryB = Category.builder().id(9L).stores(Set.of(storeB)).build();
        Product productB = Product.builder().id(50L).category(categoryB).name("X").build();
        when(productRepository.findById(50L)).thenReturn(Optional.of(productB));
        when(storeService.canScope(99L)).thenReturn(false);

        assertThrows(ResourceNotFoundException.class, () -> productServiceSut.getById(50L));
        assertThrows(ResourceNotFoundException.class, () -> productServiceSut.delete(50L));
    }

    @Test
    void categoryOfAnotherTenantIsInvisible() {
        login(7L, 1L, 1L, User.Role.EMPLOYEE);
        Category categoryB = Category.builder().id(9L).stores(Set.of(storeB)).build();
        when(categoryRepository.findById(9L)).thenReturn(Optional.of(categoryB));
        when(storeService.canScope(99L)).thenReturn(false);

        assertThrows(ResourceNotFoundException.class, () -> categoryServiceSut.getById(9L));
        assertThrows(ResourceNotFoundException.class, () -> categoryServiceSut.delete(9L));
    }

    @Test
    void extraOfAnotherTenantIsInvisible() {
        login(1L, 1L, null, User.Role.ADMIN);
        Extra extraB = Extra.builder().id(50L).client(clientB).name("E").build();
        when(extraRepository.findById(50L)).thenReturn(Optional.of(extraB));

        assertThrows(ResourceNotFoundException.class, () -> extraServiceSut.findOwnedOrThrow(50L));
        assertThrows(ResourceNotFoundException.class, () -> extraServiceSut.restore(50L));
    }

    @Test
    void sizeOfAnotherTenantIsInvisible() {
        login(1L, 1L, null, User.Role.ADMIN);
        Size sizeB = Size.builder().id(50L).client(clientB).name("S").build();
        when(sizeRepository.findById(50L)).thenReturn(Optional.of(sizeB));

        assertThrows(ResourceNotFoundException.class, () -> sizeServiceSut.findOwnedOrThrow(50L));
        assertThrows(ResourceNotFoundException.class, () -> sizeServiceSut.restore(50L));
    }

    // ── Órdenes ────────────────────────────────────────────────────────

    @Test
    void orderOfAnotherTenantIsBlocked() {
        login(7L, 1L, 1L, User.Role.EMPLOYEE);
        Order orderB = Order.builder().id(50L).store(storeB).build();
        when(orderRepository.findById(50L)).thenReturn(Optional.of(orderB));
        when(storeService.resolveScope(99L))
                .thenThrow(new AccessDeniedException("You are not assigned to this store"));

        assertThrows(AccessDeniedException.class, () -> orderServiceSut.getById(50L));
        assertThrows(AccessDeniedException.class, () -> orderServiceSut.getTicket(50L));
    }

    // ── Fichas ─────────────────────────────────────────────────────────

    @Test
    void tokenSalesOfAnotherTenantAreBlocked() {
        login(7L, 1L, 1L, User.Role.EMPLOYEE);
        when(clientService.findOrThrow(1L)).thenReturn(clientA);
        when(storeService.resolveScope(99L))
                .thenThrow(new AccessDeniedException("You are not assigned to this store"));

        assertThrows(AccessDeniedException.class, () -> tokenServiceSut.list(99L, null, null));
        assertThrows(AccessDeniedException.class, () -> tokenServiceSut.purchase(
                new TokenSaleRequest(5, Order.PaymentMethod.CASH, 99L), "key-0001"));
        verifyNoInteractions(tokenSaleRepository);
    }

    // ── Reportes ───────────────────────────────────────────────────────

    @Test
    void dashboardAlwaysAggregatesWithSessionClient() {
        login(1L, 1L, null, User.Role.ADMIN);
        when(clientService.findOrThrow(1L)).thenReturn(clientA);
        when(tokenSaleRepository.sumInRange(any(), any(), any(), any()))
                .thenReturn(List.<Object[]>of(new Object[]{BigDecimal.ZERO, 0L}));

        reportServiceSut.dashboard(null, null, null, 5);

        // el clientId del agregado siempre es el de la sesión — no hay
        // parámetro que permita pedir datos de otro tenant
        verify(orderRepository).aggregateByStatus(isNull(), eq(1L),
                any(Instant.class), any(Instant.class));
        verify(tokenSaleRepository).sumInRange(isNull(), eq(1L),
                any(Instant.class), any(Instant.class));
    }

    // ── Auditoría ──────────────────────────────────────────────────────

    @Test
    void auditSearchForcesOwnTenantForNonSuperadmin() {
        login(1L, 1L, null, User.Role.ADMIN);
        when(auditEventRepository.search(any(), any(), any(), any(), any(), any(),
                any(Pageable.class))).thenReturn(Page.empty());

        // pedir clientId=2 explícito se ignora — siempre busca el suyo
        auditServiceSut.search(2L, null, null, null, null, null, PageRequest.of(0, 20));

        verify(auditEventRepository).search(eq(1L), isNull(), isNull(), isNull(), isNull(),
                isNull(), any(Pageable.class));
    }

    @Test
    void auditSearchLetsSuperadminFilterAnyTenant() {
        login(1L, null, null, User.Role.SUPERADMIN);
        when(auditEventRepository.search(any(), any(), any(), any(), any(), any(),
                any(Pageable.class))).thenReturn(Page.empty());

        auditServiceSut.search(2L, null, null, null, null, null, PageRequest.of(0, 20));

        verify(auditEventRepository).search(eq(2L), isNull(), isNull(), isNull(), isNull(),
                isNull(), any(Pageable.class));
    }

    // ── Módulos ────────────────────────────────────────────────────────

    @Test
    void moduleAssignmentHidesForeignTenantUser() {
        login(1L, 1L, null, User.Role.ADMIN);
        User foreign = User.builder().id(50L).client(clientB).name("B").email("b@b.com")
                .role(User.Role.EMPLOYEE).build();
        when(userRepository.findById(50L)).thenReturn(Optional.of(foreign));

        assertThrows(ResourceNotFoundException.class,
                () -> moduleServiceSut.setUserModules(50L, Set.of(Module.POS), 1L));
        verify(userRepository, never()).save(any());
    }

    // ── Inventario ─────────────────────────────────────────────────────

    @Test
    void inventoryFollowsProductScope() {
        login(7L, 1L, 1L, User.Role.MANAGER);
        when(productService.findScopedOrThrow(50L))
                .thenThrow(new ResourceNotFoundException("Product not found: 50"));

        assertThrows(ResourceNotFoundException.class, () -> inventoryServiceSut.get(50L));
        assertThrows(ResourceNotFoundException.class,
                () -> inventoryServiceSut.set(50L, new InventoryRequest(10)));
        verifyNoInteractions(inventoryRepository);
    }
}
