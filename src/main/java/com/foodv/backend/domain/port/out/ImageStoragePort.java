package com.foodv.backend.domain.port.out;

/**
 * Almacenamiento de imágenes (Cloudinary).
 */
public interface ImageStoragePort {

    /**
     * @return URL pública (https) de la imagen subida
     */
    String uploadImage(byte[] imageBytes, String filename, String folder);

    void deleteImage(String publicId);
}
