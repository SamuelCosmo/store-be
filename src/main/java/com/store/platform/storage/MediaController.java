package com.store.platform.storage;

import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.concurrent.TimeUnit;

/**
 * Sirve archivos del bucket privado: redirige (302) a una URL presignada.
 * Es público — las imágenes de producto/marca se renderizan sin sesión (kiosk, ticket).
 * El `STORAGE_PUBLIC_URL` en prod apunta aquí: {@code https://<dominio>/api/media}.
 */
@RestController
@RequestMapping("/api/media")
@RequiredArgsConstructor
public class MediaController {

    private final StorageService storageService;

    @GetMapping("/{*key}")
    public ResponseEntity<Void> media(@PathVariable String key) {
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(storageService.presignedUrl(key)))
                .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS).cachePublic())
                .build();
    }
}
