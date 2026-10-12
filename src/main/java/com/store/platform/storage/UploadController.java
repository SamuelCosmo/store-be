package com.store.platform.storage;

import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/uploads")
@RequiredArgsConstructor
public class UploadController {

    private final StorageService storageService;

    /** Sube una imagen de producto → devuelve la URL pública para `products.image`. */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public UploadResponse upload(@RequestParam("file") MultipartFile file) {
        return new UploadResponse(storageService.uploadProductImage(file));
    }

    /** Logo del tenant → `clients/{id}/brand/` — a nivel cliente, solo ADMIN. */
    @PostMapping(value = "/brand", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    public UploadResponse uploadBrand(@RequestParam("file") MultipartFile file) {
        return new UploadResponse(storageService.uploadBrandImage(file));
    }

    public record UploadResponse(String url) {}
}
