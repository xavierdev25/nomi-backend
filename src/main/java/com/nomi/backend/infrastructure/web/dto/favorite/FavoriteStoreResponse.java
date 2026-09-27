package com.nomi.backend.infrastructure.web.dto.favorite;

import java.time.LocalDateTime;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Tienda marcada como favorita.")
public record FavoriteStoreResponse(
        @Schema(description = "Id del favorito", example = "1")
        Long id,
        @Schema(description = "Usuario", example = "5")
        Long userId,
        @Schema(description = "Tienda", example = "1")
        Long storeId,
        @Schema(description = "Fecha de alta (hora local del servidor, sin zona)")
        LocalDateTime creadoEn
) {}
