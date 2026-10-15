package com.store.platform;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.scheduling.annotation.EnableScheduling;

// JWT stateless API — no in-memory user details needed
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@EnableScheduling
public class StorePlatformApplication {

    public static void main(String[] args) {
        SpringApplication.run(StorePlatformApplication.class, args);
    }
}
