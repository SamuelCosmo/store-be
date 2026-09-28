package com.store.platform.config;

import com.store.platform.client.ClientService;
import com.store.platform.client.dto.ClientRequest;
import com.store.platform.client.dto.ClientResponse;
import com.store.platform.user.User;
import com.store.platform.user.UserRepository;
import com.store.platform.user.UserService;
import com.store.platform.user.dto.UserRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/** Dev seed: a test client (1 store, 2 POS, 2 kiosks) with its ADMIN user. */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private static final String ADMIN_EMAIL = "admin@test.com";

    private final ClientService clientService;
    private final UserService userService;
    private final UserRepository userRepository;

    @Override
    public void run(String... args) {
        if (userRepository.existsByEmail(ADMIN_EMAIL)) {
            return;
        }
        ClientResponse client = clientService.create(
                new ClientRequest("client_test", 1, 2, 2));
        userService.create(client.id(),
                new UserRequest("admin_test", ADMIN_EMAIL, "admin_test", User.Role.ADMIN, List.of()));
        log.info("Seeded client_test (id={}) with admin {}", client.id(), ADMIN_EMAIL);
    }
}
