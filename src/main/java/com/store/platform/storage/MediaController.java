package com.store.platform.storage;

import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.util.concurrent.TimeUnit;

/**
 * Sirve archivos del bucket privado proxyeando los bytes — los buckets de
 * Railway/Tigris son privados y las URLs presignadas del MinioClient
 * devuelven 404 ahí (scope de firma incompatible). Es público: las
 * imágenes se renderizan sin sesión (kiosk, ticket).
 * El `STORAGE_PUBLIC_URL` en prod apunta aquí: {@code https://<dominio>/api/media}.
 */
@RestController
@RequestMapping("/api/media")
@RequiredArgsConstructor
public class MediaController {

    private final StorageService storageService;

    @GetMapping("/{*key}")
    public ResponseEntity<StreamingResponseBody> media(@PathVariable String key) {
        var obj = storageService.get(key);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(obj.stat().contentType()))
                .contentLength(obj.stat().size())
                .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS).cachePublic())
                .body(out -> {
                    try (var in = obj.stream()) {
                        in.transferTo(out);
                    }
                });
    }
}
