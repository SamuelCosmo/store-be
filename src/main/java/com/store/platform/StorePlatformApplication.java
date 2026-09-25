package com.store.platform;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;

// JWT stateless API — no in-memory user details needed
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class StorePlatformApplication {

    public static void main(String[] args) {
        SpringApplication.run(StorePlatformApplication.class, args);
    }
}
