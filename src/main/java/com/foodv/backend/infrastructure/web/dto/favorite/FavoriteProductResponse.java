package com.foodv.backend.infrastructure.web.dto.favorite;

import java.time.LocalDateTime;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Producto marcado como favorito.")
public record FavoriteProductResponse(
        @Schema(description = "Id del favorito", example = "1")
        Long id,
        @Schema(description = "Usuario", example = "5")
        Long userId,
        @Schema(description = "Producto", example = "4")
        Long productId,
        @Schema(description = "Fecha de alta (hora local del servidor, sin zona)")
        LocalDateTime creadoEn
) {}
