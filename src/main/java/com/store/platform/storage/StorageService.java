package com.store.platform.storage;

import com.store.platform.common.exception.BusinessException;
import com.store.platform.security.AuthContext;
import com.store.platform.security.AuthUser;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

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

    /** Guarda la imagen bajo {@code products/{uuid}} y devuelve su URL pública. */
    public String uploadProductImage(MultipartFile file) {
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
        // carpeta por tenant: clients/{id}/stores/{id}/products/{uuid}
        // (admin sin tienda activa → stores/shared)
        AuthUser auth = AuthContext.current();
        Long storeId = auth.storeId();
        String key = "clients/" + auth.clientId()
                + "/stores/" + (storeId == null ? "shared" : storeId)
                + "/products/" + UUID.randomUUID() + "." + ext;
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

    /** Crea el bucket si no existe (s3mock ya lo crea vía initialBuckets). */
    private void ensureBucket() throws Exception {
        if (bucketReady) return;
        if (!client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
            client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
        }
        bucketReady = true;
    }
}
