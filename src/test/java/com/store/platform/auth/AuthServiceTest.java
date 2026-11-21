package com.store.platform.auth;

import com.store.platform.auth.dto.LoginRequest;
import com.store.platform.client.Client;
import com.store.platform.client.ClientRepository;
import com.store.platform.common.RateLimiter;
import com.store.platform.common.exception.BusinessException;
import com.store.platform.common.exception.RateLimitException;
import com.store.platform.security.JwtService;
import com.store.platform.store.StoreRepository;
import com.store.platform.user.User;
import com.store.platform.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock UserRepository userRepository;
    @Mock ClientRepository clientRepository;
    @Mock StoreRepository storeRepository;
    @Mock JwtService jwtService;

    private AuthService authService;
    private Client client;

    @BeforeEach
    void setUp() {
        authService = new AuthService(
                userRepository, clientRepository, storeRepository, jwtService, new RateLimiter());
        client = Client.builder().id(1L).name("Kofi").active(true).build();
        User user = User.builder()
                .id(9L).client(client).email("ana@test.com").active(true)
                .password(new BCryptPasswordEncoder().encode("secreta"))
                .role(User.Role.EMPLOYEE).stores(Set.of()).build();
        when(userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull("ana@test.com"))
                .thenReturn(Optional.of(user));
    }

    private LoginRequest req(String password) {
        return new LoginRequest("ana@test.com", password, null);
    }

    @Test
    void fiveFailuresLockTheAccount() {
        for (int i = 0; i < 5; i++) {
            assertThrows(BadCredentialsException.class,
                    () -> authService.storesForLogin(req("mala"), "1.2.3.4"));
        }
        // ya ni con la contraseña correcta entra — está bloqueado
        assertThrows(RateLimitException.class,
                () -> authService.storesForLogin(req("secreta"), "1.2.3.4"));
    }

    @Test
    void successfulLoginResetsTheCounter() {
        for (int i = 0; i < 4; i++) {
            assertThrows(BadCredentialsException.class,
                    () -> authService.storesForLogin(req("mala"), "1.2.3.4"));
        }
        assertDoesNotThrow(() -> authService.storesForLogin(req("secreta"), "1.2.3.4"));
        // otros 4 fallos no bloquean — el éxito reinició la ventana
        for (int i = 0; i < 4; i++) {
            assertThrows(BadCredentialsException.class,
                    () -> authService.storesForLogin(req("mala"), "1.2.3.4"));
        }
        assertDoesNotThrow(() -> authService.storesForLogin(req("secreta"), "1.2.3.4"));
    }

    @Test
    void thirtyFailuresFromOneIpLockAllEmails() {
        // cada correo distinto llega solo 5 veces — el freno es por IP
        for (int i = 0; i < 30; i++) {
            String email = "u" + i + "@test.com";
            when(userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(email))
                    .thenReturn(Optional.empty());
            assertThrows(BadCredentialsException.class,
                    () -> authService.storesForLogin(new LoginRequest(email, "x", null), "9.9.9.9"));
        }
        assertThrows(RateLimitException.class,
                () -> authService.storesForLogin(req("secreta"), "9.9.9.9"));
        // otra IP del mismo correo sí entra — el bloqueo de correo fue de 5, no 30
        assertDoesNotThrow(() -> authService.storesForLogin(req("secreta"), "8.8.8.8"));
    }

    @Test
    void suspendedClientBlocksLoginAndStoreLookup() {
        client.setActive(false);
        BusinessException ex1 = assertThrows(BusinessException.class,
                () -> authService.login(req("secreta"), "1.2.3.4"));
        assertEquals("CLIENT_SUSPENDED", ex1.getCode());

        BusinessException ex2 = assertThrows(BusinessException.class,
                () -> authService.storesForLogin(req("secreta"), "1.2.3.4"));
        assertEquals("CLIENT_SUSPENDED", ex2.getCode());
    }
}
