package com.store.platform.token;

import com.store.platform.client.Client;
import com.store.platform.audit.AuditService;
import com.store.platform.client.ClientService;
import com.store.platform.user.UserRepository;
import com.store.platform.order.Order;
import com.store.platform.security.AuthUser;
import com.store.platform.store.Store;
import com.store.platform.store.StoreRepository;
import com.store.platform.store.StoreService;
import com.store.platform.token.dto.TokenSaleRequest;
import com.store.platform.user.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TokenServiceTest {

    @Mock TokenSaleRepository tokenSaleRepository;
    @Mock StoreRepository storeRepository;
    @Mock StoreService storeService;
    @Mock UserRepository userRepository;
    @Mock ClientService clientService;
    @Mock AuditService auditService;
    @InjectMocks TokenService tokenService;

    private final Store store = Store.builder().id(1L)
            .client(Client.builder().id(1L).tokenPrice(new BigDecimal("10")).build()).build();

    @BeforeEach
    void setUp() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new AuthUser(7L, 1L, 1L, User.Role.EMPLOYEE), null, List.of()));
        when(storeService.resolveScope(null)).thenReturn(1L);
        when(storeRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(store));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void retryWithSameIdempotencyKeyReturnsExistingSale() {
        TokenSale existing = TokenSale.builder().id(9L).store(store)
                .user(User.builder().id(7L).build()).quantity(5)
                .unitPrice(BigDecimal.TEN).totalPrice(new BigDecimal("50"))
                .paymentMethod(Order.PaymentMethod.CASH).idempotencyKey("sale-key-123").build();
        when(tokenSaleRepository.findByStoreIdAndIdempotencyKey(1L, "sale-key-123"))
                .thenReturn(Optional.of(existing));

        var response = tokenService.purchase(
                new TokenSaleRequest(5, Order.PaymentMethod.CASH, null), "sale-key-123");

        assertEquals(9L, response.id());
        verify(tokenSaleRepository, never()).save(any());
    }
}
