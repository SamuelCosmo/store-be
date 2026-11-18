package com.store.platform.audit;

import com.store.platform.client.Client;
import com.store.platform.client.ClientRepository;
import com.store.platform.security.AuthUser;
import com.store.platform.store.Store;
import com.store.platform.store.StoreRepository;
import com.store.platform.user.User;
import com.store.platform.user.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuditServiceTest {

    @Mock AuditEventRepository auditEventRepository;
    @Mock UserRepository userRepository;
    @Mock ClientRepository clientRepository;
    @Mock StoreRepository storeRepository;
    @InjectMocks AuditService auditService;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void authenticate(AuthUser auth) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(auth, null, List.of()));
    }

    @Test
    void diffOnlyReportsChangedFields() {
        Map<String, Object> before = Map.of("name", "Old", "price", 10, "active", true);
        Map<String, Object> after = Map.of("name", "New", "price", 10, "active", false);

        Map<String, Object> changes = AuditService.diff(before, after);

        assertEquals(2, changes.size());
        assertEquals(Map.of("from", "Old", "to", "New"), changes.get("name"));
        assertEquals(Map.of("from", true, "to", false), changes.get("active"));
        assertFalse(changes.containsKey("price"));
    }

    @Test
    void diffIsNullSafeOnBothSides() {
        // Map.of no admite nulls — construimos con HashMap
        var before = new java.util.HashMap<String, Object>();
        before.put("a", null);
        var after = new java.util.HashMap<String, Object>();
        after.put("a", "x");
        after.put("b", null);

        Map<String, Object> changes = AuditService.diff(before, after);
        assertEquals(1, changes.size());
        assertEquals("x", ((Map<?, ?>) changes.get("a")).get("to"));
        assertNull(((Map<?, ?>) changes.get("a")).get("from"));
    }

    @Test
    void recordPersistsActorSnapshotAndJwtScope() {
        authenticate(new AuthUser(7L, 1L, 2L, User.Role.MANAGER));
        User actor = User.builder().id(7L).name("Ana").email("ana@x.com").build();
        Client client = Client.builder().id(1L).build();
        Store store = Store.builder().id(2L).build();
        when(userRepository.findById(7L)).thenReturn(Optional.of(actor));
        when(clientRepository.getReferenceById(1L)).thenReturn(client);
        when(storeRepository.getReferenceById(2L)).thenReturn(store);

        auditService.record("product", "update", 9L, "Latte", Map.of("price", Map.of("from", 10, "to", 12)));

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditEventRepository).save(captor.capture());
        AuditEvent saved = captor.getValue();
        assertEquals(7L, saved.getActorId());
        assertEquals("Ana", saved.getActorName());
        assertEquals("ana@x.com", saved.getActorEmail());
        assertEquals("MANAGER", saved.getActorRole());
        assertEquals("product", saved.getEntityType());
        assertEquals("update", saved.getAction());
        assertEquals(9L, saved.getEntityId());
        assertEquals("Latte", saved.getEntityLabel());
        assertSame(client, saved.getClient());
        assertSame(store, saved.getStore());
        assertEquals(12, ((Map<?, ?>) saved.getDetails().get("price")).get("to"));
    }

    @Test
    void recordForUsesExplicitScopeForSuperadmin() {
        // SUPERADMIN no tiene clientId/storeId en el JWT — el scope viene del recurso
        authenticate(new AuthUser(1L, null, null, User.Role.SUPERADMIN));
        Client client = Client.builder().id(5L).name("TenantX").build();
        when(userRepository.findById(1L)).thenReturn(Optional.empty());
        when(clientRepository.getReferenceById(5L)).thenReturn(client);

        auditService.recordFor(5L, null, "client", "update", 5L, "TenantX",
                Map.of("plan", Map.of("from", "basic", "to", "pro")));

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditEventRepository).save(captor.capture());
        AuditEvent saved = captor.getValue();
        assertSame(client, saved.getClient());
        assertNull(saved.getStore());
        assertNull(saved.getActorName()); // actor borrado → snapshot null, no falla
        assertEquals("SUPERADMIN", saved.getActorRole());
    }

    @Test
    void adminSearchForcesOwnTenantIgnoringClientIdParam() {
        authenticate(new AuthUser(7L, 1L, null, User.Role.ADMIN));
        when(auditEventRepository.search(eq(1L), isNull(), isNull(), isNull(),
                isNull(), isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        auditService.search(99L, null, null, null, null, null, PageRequest.of(0, 20));

        // aunque pidan clientId=99, el scope real es el del JWT (1L)
        verify(auditEventRepository).search(eq(1L), isNull(), isNull(), isNull(),
                isNull(), isNull(), any(Pageable.class));
    }

    @Test
    void superadminSearchHonorsClientIdFilterAndNullMeansAll() {
        authenticate(new AuthUser(1L, null, null, User.Role.SUPERADMIN));
        when(auditEventRepository.search(any(), any(), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        auditService.search(5L, "product", null, null, null, null, PageRequest.of(0, 20));
        verify(auditEventRepository).search(eq(5L), eq("product"), isNull(), isNull(),
                isNull(), isNull(), any(Pageable.class));

        auditService.search(null, null, null, null, null, null, PageRequest.of(0, 20));
        verify(auditEventRepository).search(isNull(), isNull(), isNull(), isNull(),
                isNull(), isNull(), any(Pageable.class));
    }

    @Test
    void searchAppliesCreatedAtDescDefaultSort() {
        authenticate(new AuthUser(7L, 1L, null, User.Role.ADMIN));
        when(auditEventRepository.search(any(), any(), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        auditService.search(null, null, null, null, null, null, PageRequest.of(0, 20));

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(auditEventRepository).search(any(), any(), any(), any(), any(), any(), captor.capture());
        assertEquals(org.springframework.data.domain.Sort.Direction.DESC,
                captor.getValue().getSort().getOrderFor("createdAt").getDirection());
    }
}
