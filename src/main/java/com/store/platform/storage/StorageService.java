package com.store.platform.storage;

import com.store.platform.common.exception.BusinessException;
import com.store.platform.security.AuthContext;
import com.store.platform.security.AuthUser;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import io.minio.errors.ErrorResponseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.UUID;

/** Sube archivos al bucket (MinIO local / S3-compatible en producción). */
@Service
public class StorageService {

    private static final long MAX_BYTES = 5 * 1024 * 1024;
    private static final Map<String, String> TYPES = Map.of(
            "image/jpeg", "jpg",
            "image/png", "png",
            "image/webp", "webp",
            "image/gif", "gif");

    private static final Logger log = LoggerFactory.getLogger(StorageService.class);

    private final MinioClient client;
    private final String bucket;
    private final String publicUrl;
    private volatile boolean bucketReady;

    public StorageService(@Value("${app.storage.endpoint}") String endpoint,
                          @Value("${app.storage.access-key}") String accessKey,
                          @Value("${app.storage.secret-key}") String secretKey,
                          @Value("${app.storage.bucket}") String bucket,
                          @Value("${app.storage.public-url}") String publicUrl) {
        this.client = MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
        this.bucket = bucket;
        this.publicUrl = publicUrl;
    }

    /** Imagen de producto: {@code clients/{c}/stores/{s}/products/{uuid}}. */
    public String uploadProductImage(MultipartFile file) {
        AuthUser auth = AuthContext.current();
        // admin sin tienda activa → stores/shared
        Long storeId = auth.storeId();
        String prefix = "clients/" + auth.clientId()
                + "/stores/" + (storeId == null ? "shared" : storeId)
                + "/products";
        return upload(file, prefix);
    }

    /** Logo del tenant: {@code clients/{c}/brand/{uuid}} — a nivel cliente. */
    public String uploadBrandImage(MultipartFile file) {
        String prefix = "clients/" + AuthContext.current().clientId() + "/brand";
        return upload(file, prefix);
    }

    /** Valida tipo/tamaño, sube como `{prefix}/{uuid}.{ext}` y devuelve su URL pública. */
    private String upload(MultipartFile file, String prefix) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("Empty file");
        }
        String ext = TYPES.get(file.getContentType());
        if (ext == null) {
            throw new BusinessException("Only image files allowed (jpg, png, webp, gif)");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new BusinessException("Image too large (max 5 MB)");
        }
        String key = prefix + "/" + UUID.randomUUID() + "." + ext;
        try (var in = file.getInputStream()) {
            ensureBucket();
            client.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(key)
                    .stream(in, file.getSize(), -1)
                    .contentType(file.getContentType())
                    .build());
        } catch (Exception e) {
            throw new BusinessException("Could not upload image: " + e.getMessage());
        }
        return publicUrl + "/" + key;
    }

    /** Borra el objeto detrás de una URL pública del bucket — best effort:
     *  un fallo no debe tumbar el request (quedaría una imagen huérfana más). */
    public void deleteByUrl(String url) {
        if (url == null || !url.startsWith(publicUrl + "/")) return;
        String key = url.substring(publicUrl.length() + 1);
        try {
            client.removeObject(RemoveObjectArgs.builder()
                    .bucket(bucket)
                    .object(key)
                    .build());
            log.info("Deleted orphan object {}", key);
        } catch (Exception e) {
            log.warn("Could not delete object {}: {}", key, e.getMessage());
        }
    }

    /** Lee un objeto del bucket para servirlo vía /api/media — stream + meta. */
    public StoredObject get(String key) {
        try {
            StatObjectResponse stat = client.statObject(
                    StatObjectArgs.builder().bucket(bucket).object(key).build());
            GetObjectResponse stream = client.getObject(
                    GetObjectArgs.builder().bucket(bucket).object(key).build());
            return new StoredObject(stat, stream);
        } catch (ErrorResponseException e) {
            if ("NoSuchKey".equals(e.errorResponse().code())) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Not found");
            }
            throw new BusinessException("Could not read image: " + e.getMessage());
        } catch (Exception e) {
            throw new BusinessException("Could not read image: " + e.getMessage());
        }
    }

    public record StoredObject(StatObjectResponse stat, GetObjectResponse stream) {}

    /** Crea el bucket si no existe (s3mock ya lo crea vía initialBuckets). */
    private void ensureBucket() throws Exception {
        if (bucketReady) return;
        if (!client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
            client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
        }
        bucketReady = true;
    }
}
