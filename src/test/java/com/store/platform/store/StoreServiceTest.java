package com.store.platform.store;

import com.store.platform.client.Client;
import com.store.platform.common.exception.BusinessException;
import com.store.platform.security.AuthUser;
import com.store.platform.store.dto.StoreSettingsRequest;
import com.store.platform.user.User;
import com.store.platform.user.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StoreServiceTest {

    @Mock StoreRepository storeRepository;
    @Mock UserRepository userRepository;
    @InjectMocks StoreService storeService;

    private final Client client = Client.builder().id(1L).maxStores(1).build();

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void restoreRespectsPlanStoreLimit() {
        Store deleted = Store.builder().id(2L).client(client).deletedAt(Instant.now()).build();
        when(storeRepository.findById(2L)).thenReturn(Optional.of(deleted));
        when(storeRepository.countByClientIdAndDeletedAtIsNull(1L)).thenReturn(1L);

        assertThrows(BusinessException.class, () -> storeService.restore(2L, 1L));
        verify(storeRepository, never()).save(any());
    }

    @Test
    void kitchenWarnMustExceedOk() {
        Store store = Store.builder().id(2L).client(client).kitchenOkMin(8).kitchenWarnMin(15).build();
        when(storeRepository.findById(2L)).thenReturn(Optional.of(store));

        // solo llega okMin=20 → choca con el warnMin=15 guardado
        assertThrows(BusinessException.class, () -> storeService.updateSettings(2L,
                new StoreSettingsRequest(null, 20, null, null), 1L));
        verify(storeRepository, never()).save(any());
    }

    @Test
    void canScopeAllowsAnyAssignedStoreNotJustSession() {
        // MANAGER con sesión en tienda 1, asignado también a la 2
        AuthUser auth = new AuthUser(7L, 1L, 1L, User.Role.MANAGER);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(auth, null, List.of()));
        when(userRepository.findStoreIds(7L)).thenReturn(Set.of(1L, 2L));
        when(storeRepository.findById(2L)).thenReturn(
                Optional.of(Store.builder().id(2L).client(client).build()));

        assertTrue(storeService.canScope(2L));
        assertFalse(storeService.canScope(3L));
    }
}
