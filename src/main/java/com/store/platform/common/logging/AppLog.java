package com.store.platform.common.logging;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** Fila de log persistida por {@link DbLogAppender} — solo lectura desde JPA. */
@Entity
@Table(name = "app_logs", indexes = {
        @Index(columnList = "created_at"),
        @Index(columnList = "request_id")})
@Getter
@NoArgsConstructor
public class AppLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false, length = 5)
    private String level;

    @Column(length = 120)
    private String logger;

    @Column(columnDefinition = "text")
    private String message;

    @Column(columnDefinition = "text")
    private String stack;

    @Column(length = 64)
    private String requestId;

    private Long userId;

    private Long clientId;

    private Long storeId;

    @Column(length = 20)
    private String role;
}
