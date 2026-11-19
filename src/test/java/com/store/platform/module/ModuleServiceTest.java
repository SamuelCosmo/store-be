package com.store.platform.module;

import com.store.platform.audit.AuditService;
import com.store.platform.client.Client;
import com.store.platform.client.ClientRepository;
import com.store.platform.common.exception.BusinessException;
import com.store.platform.common.exception.ResourceNotFoundException;
import com.store.platform.user.User;
import com.store.platform.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ModuleServiceTest {

    @Mock ClientRepository clientRepository;
    @Mock UserRepository userRepository;
    @Mock AuditService auditService;
    @InjectMocks ModuleService moduleService;

    private Client clientWith(Set<Module> modules) {
        return Client.builder().id(1L).name("Resto").modules(new java.util.HashSet<>(modules)).build();
    }

    private User userWith(User.Role role, Client client, Set<Module> modules) {
        return User.builder().id(9L).name("U").email("u@t.com").role(role)
                .client(client).modules(new java.util.HashSet<>(modules)).build();
    }

    // ---- effectiveModules (static, sin mocks) ----

    @Test
    void superadminGetsAllModules() {
        User sa = userWith(User.Role.SUPERADMIN, null, Set.of());
        assertEquals(EnumSet.allOf(Module.class), ModuleService.effectiveModules(sa));
    }

    @Test
    void adminGetsAllPurchasedModules() {
        Client c = clientWith(EnumSet.of(Module.POS, Module.KDS));
        User admin = userWith(User.Role.ADMIN, c, Set.of());
        assertEquals(Set.of(Module.POS, Module.KDS), ModuleService.effectiveModules(admin));
    }

    @Test
    void employeeGetsIntersectionWithPurchased() {
        Client c = clientWith(EnumSet.of(Module.POS, Module.KDS, Module.REPORTS));
        User emp = userWith(User.Role.EMPLOYEE, c, EnumSet.of(Module.POS, Module.BAR));
        // BAR no está comprado → queda solo POS
        assertEquals(Set.of(Module.POS), ModuleService.effectiveModules(emp));
    }

    @Test
    void legacyUserWithoutAssignmentGetsAllPurchased() {
        Client c = clientWith(EnumSet.of(Module.POS, Module.KDS));
        User emp = userWith(User.Role.EMPLOYEE, c, Set.of()); // vacío = nunca configurado
        assertEquals(Set.of(Module.POS, Module.KDS), ModuleService.effectiveModules(emp));
    }

    @Test
    void legacyClientWithoutModulesMeansFullCatalog() {
        Client c = clientWith(Set.of()); // vacío = tenant pre-módulos
        User emp = userWith(User.Role.EMPLOYEE, c, EnumSet.of(Module.POS));
        assertEquals(Set.of(Module.POS), ModuleService.effectiveModules(emp));
        User admin = userWith(User.Role.ADMIN, c, Set.of());
        assertEquals(EnumSet.allOf(Module.class), ModuleService.effectiveModules(admin));
    }

    @Test
    void userWithoutClientGetsNothing() {
        User orphan = userWith(User.Role.EMPLOYEE, null, EnumSet.allOf(Module.class));
        assertTrue(ModuleService.effectiveModules(orphan).isEmpty());
    }

    // ---- setUserModules ----

    @Test
    void setUserModulesRejectsModuleNotPurchased() {
        Client c = clientWith(EnumSet.of(Module.POS));
        User u = userWith(User.Role.EMPLOYEE, c, Set.of(Module.POS));
        when(userRepository.findById(9L)).thenReturn(Optional.of(u));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> moduleService.setUserModules(9L, Set.of(Module.POS, Module.KDS), 1L));
        assertEquals("MODULE_NOT_PURCHASED", ex.getCode());
    }

    @Test
    void setUserModulesRejectsEmpty() {
        Client c = clientWith(EnumSet.of(Module.POS));
        User u = userWith(User.Role.EMPLOYEE, c, Set.of(Module.POS));
        when(userRepository.findById(9L)).thenReturn(Optional.of(u));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> moduleService.setUserModules(9L, Set.of(), 1L));
        assertEquals("MODULES_REQUIRED", ex.getCode());
    }

    @Test
    void setUserModulesRejectsOtherTenant() {
        Client c = clientWith(EnumSet.of(Module.POS));
        c.setId(2L);
        User u = userWith(User.Role.EMPLOYEE, c, Set.of(Module.POS));
        when(userRepository.findById(9L)).thenReturn(Optional.of(u));

        assertThrows(ResourceNotFoundException.class,
                () -> moduleService.setUserModules(9L, Set.of(Module.POS), 1L));
    }

    @Test
    void setUserModulesSavesValidSubset() {
        Client c = clientWith(EnumSet.of(Module.POS, Module.KDS));
        User u = userWith(User.Role.EMPLOYEE, c, EnumSet.allOf(Module.class));
        when(userRepository.findById(9L)).thenReturn(Optional.of(u));

        Set<Module> result = moduleService.setUserModules(9L, Set.of(Module.KDS), 1L);
        assertEquals(Set.of(Module.KDS), result);
        verify(userRepository).save(u);
    }

    // ---- setClientModules ----

    @Test
    void setClientModulesRejectsEmpty() {
        Client c = clientWith(EnumSet.of(Module.POS));
        when(clientRepository.findById(1L)).thenReturn(Optional.of(c));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> moduleService.setClientModules(1L, Set.of()));
        assertEquals("MODULES_REQUIRED", ex.getCode());
    }

    @Test
    void setClientModulesSaves() {
        Client c = clientWith(EnumSet.allOf(Module.class));
        when(clientRepository.findById(1L)).thenReturn(Optional.of(c));

        Set<Module> result = moduleService.setClientModules(1L, Set.of(Module.POS, Module.KDS));
        assertEquals(Set.of(Module.POS, Module.KDS), result);
        verify(clientRepository).save(c);
    }
}
