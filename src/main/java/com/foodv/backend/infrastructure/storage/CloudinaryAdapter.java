package com.foodv.backend.infrastructure.storage;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.foodv.backend.domain.port.out.ImageStoragePort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Subida y borrado de imágenes en Cloudinary. La subida se reintenta hasta dos veces con
 * espera creciente antes de fallar.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CloudinaryAdapter implements ImageStoragePort {

    private static final int MAX_RETRIES = 2;

    private final Cloudinary cloudinary;

    @Override
    public String uploadImage(byte[] imageBytes, String filename, String folder) {
        Exception last = null;
        for (int attempt = 1; attempt <= MAX_RETRIES + 1; attempt++) {
            try {
                @SuppressWarnings("rawtypes")
                Map options = ObjectUtils.asMap(
                        "folder", folder,
                        "resource_type", "image",
                        "use_filename", true,
                        "unique_filename", true,
                        "overwrite", false
                );
                @SuppressWarnings("rawtypes")
                Map result = cloudinary.uploader().upload(imageBytes, options);
                return (String) result.get("secure_url");
            } catch (Exception e) {
                last = e;
                log.warn("Intento {} fallido subiendo imagen ({}): {}", attempt, filename, e.getMessage());
                try {
                    Thread.sleep(150L * attempt);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        log.error("No fue posible subir la imagen tras {} intentos", MAX_RETRIES + 1, last);
        throw new IllegalStateException("Error subiendo la imagen. Intenta nuevamente más tarde.");
    }

    @Override
    public void deleteImage(String publicId) {
        try {
            cloudinary.uploader().destroy(publicId, ObjectUtils.emptyMap());
        } catch (Exception e) {
            log.warn("Error eliminando imagen con publicId {}: {}", publicId, e.getMessage());
        }
    }
}
