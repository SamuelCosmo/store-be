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
        // {*key} captura con slash inicial → "clients/1/x.webp" para Minio
        if (key.startsWith("/")) key = key.substring(1);
        var in = storageService.get(key);
        String contentType = in.headers().get("Content-Type");
        String length = in.headers().get("Content-Length");
        var body = ResponseEntity.ok()
                .contentType(contentType != null
                        ? MediaType.parseMediaType(contentType)
                        : MediaType.APPLICATION_OCTET_STREAM)
                .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS).cachePublic());
        if (length != null) body.contentLength(Long.parseLong(length));
        return body.body(out -> {
            try (in) {
                in.transferTo(out);
            }
        });
    }
}
