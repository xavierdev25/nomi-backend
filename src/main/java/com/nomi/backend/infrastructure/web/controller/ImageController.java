package com.nomi.backend.infrastructure.web.controller;

import com.nomi.backend.domain.model.user.UserRole;
import com.nomi.backend.domain.port.out.ImageStoragePort;
import com.nomi.backend.infrastructure.security.AuthenticatedUserResolver;
import com.nomi.backend.infrastructure.security.OwnershipService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

/**
 * Subida de imágenes de productos y tiendas por su dueño (máximo 5 MB; jpg, png, webp o gif)
 * y borrado por un administrador.
 */
@Tag(name = "Imágenes")
@RestController
@RequestMapping("/images")
@RequiredArgsConstructor
public class ImageController {

    private static final long MAX_FILE_SIZE = 5L * 1024 * 1024;
    private static final Set<String> ALLOWED_MIME = Set.of(
            "image/jpeg", "image/png", "image/webp", "image/gif"
    );
    private static final Set<String> ALLOWED_EXT = Set.of("jpg", "jpeg", "png", "webp", "gif");

    private final ImageStoragePort imageStoragePort;
    private final AuthenticatedUserResolver currentUser;
    private final OwnershipService ownershipService;

    @Operation(summary = "Subir imagen de producto (sólo dueño o ADMIN)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Imagen subida"),
            @ApiResponse(responseCode = "400", description = "Archivo inválido"),
            @ApiResponse(responseCode = "403", description = "Acceso denegado")
    })
    @PostMapping("/products/{productId}")
    public ResponseEntity<Map<String, String>> uploadProductImage(
            @PathVariable Long productId,
            @RequestParam("file") MultipartFile file
    ) throws IOException {
        ownershipService.requireProductOwnerOrAdmin(currentUser.currentUserSummary(), productId);
        validateFile(file);
        String imageUrl = imageStoragePort.uploadImage(
                file.getBytes(), sanitizeFilename(file.getOriginalFilename()), "nomi/products");
        return ResponseEntity.ok(Map.of("url", imageUrl));
    }

    @Operation(summary = "Subir imagen de tienda (sólo dueño o ADMIN)")
    @PostMapping("/stores/{storeId}")
    public ResponseEntity<Map<String, String>> uploadStoreImage(
            @PathVariable Long storeId,
            @RequestParam("file") MultipartFile file
    ) throws IOException {
        ownershipService.requireStoreOwnerOrAdmin(currentUser.currentUserSummary(), storeId);
        validateFile(file);
        String imageUrl = imageStoragePort.uploadImage(
                file.getBytes(), sanitizeFilename(file.getOriginalFilename()), "nomi/stores");
        return ResponseEntity.ok(Map.of("url", imageUrl));
    }

    @Operation(summary = "Eliminar imagen por publicId (sólo ADMIN)")
    @DeleteMapping
    public ResponseEntity<Void> deleteImage(@RequestParam String publicId) {
        if (currentUser.currentRole() != UserRole.ADMIN) {
            throw new AccessDeniedException("Sólo ADMIN puede eliminar imágenes directamente");
        }
        if (publicId == null || publicId.isBlank()) {
            throw new IllegalArgumentException("publicId requerido");
        }
        imageStoragePort.deleteImage(publicId);
        return ResponseEntity.noContent().build();
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("El archivo no puede estar vacío");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("El archivo excede el máximo permitido de 5MB");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_MIME.contains(contentType.toLowerCase())) {
            throw new IllegalArgumentException("Formato no soportado. Permitidos: jpg, png, webp, gif");
        }
        String extension = extension(file.getOriginalFilename());
        if (extension == null || !ALLOWED_EXT.contains(extension.toLowerCase())) {
            throw new IllegalArgumentException("Extensión inválida. Permitidas: jpg, jpeg, png, webp, gif");
        }
    }

    private String extension(String filename) {
        if (filename == null) return null;
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(dot + 1) : null;
    }

    private String sanitizeFilename(String filename) {
        if (filename == null) return "image";
        String base = filename.replaceAll("[^a-zA-Z0-9._-]", "_");
        return base.length() > 100 ? base.substring(base.length() - 100) : base;
    }
}
