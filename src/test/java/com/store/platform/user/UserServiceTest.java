package com.store.platform.user;

import com.store.platform.client.ClientRepository;
import com.store.platform.audit.AuditService;
import com.store.platform.security.AuthUser;
import com.store.platform.store.StoreRepository;
import com.store.platform.user.dto.UserRequest;
import com.store.platform.user.dto.UserUpdateRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock UserRepository userRepository;
    @Mock ClientRepository clientRepository;
    @Mock StoreRepository storeRepository;
    @Mock AuditService auditService;
    @InjectMocks UserService userService;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new AuthUser(7L, 1L, null, User.Role.ADMIN), null, List.of()));
        User self = User.builder().id(7L)
                .password(new BCryptPasswordEncoder().encode("Secret123")).build();
        when(userRepository.findById(7L)).thenReturn(Optional.of(self));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void tenantAdminCannotCreateSuperadmin() {
        UserRequest request = new UserRequest("x", "x@x.com", "Passw0rd1",
                User.Role.SUPERADMIN, List.of(), "Secret123");
        assertThrows(AccessDeniedException.class, () -> userService.create(1L, request));
        verify(userRepository, never()).save(any());
    }

    @Test
    void deactivatingRevokesTokens() {
        User target = User.builder().id(8L)
                .client(com.store.platform.client.Client.builder().id(1L).build()).build();
        when(userRepository.findById(8L)).thenReturn(Optional.of(target));
        when(userRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        userService.setActive(8L, false, new com.store.platform.user.dto.PasswordRequest("Secret123"));
        assertEquals(1, target.getTokenVersion());
    }

    @Test
    void tenantAdminCannotPromoteToSuperadmin() {
        UserUpdateRequest request = new UserUpdateRequest("x", "x@x.com", null,
                User.Role.SUPERADMIN, List.of(), "Secret123");
        assertThrows(AccessDeniedException.class, () -> userService.update(8L, request));
        verify(userRepository, never()).save(any());
    }
}
