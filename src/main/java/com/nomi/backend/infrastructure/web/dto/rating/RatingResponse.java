package com.nomi.backend.infrastructure.web.dto.rating;

import java.time.LocalDateTime;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Calificación de un pedido.")
public record RatingResponse(
        @Schema(description = "Id de la calificación", example = "1")
        Long id,
        @Schema(description = "Pedido calificado", example = "2")
        Long orderId,
        @Schema(description = "Estudiante", example = "5")
        Long userId,
        @Schema(description = "Tienda", example = "1")
        Long storeId,
        @Schema(description = "Puntuación de 1 a 5", example = "5")
        Integer rating,
        @Schema(description = "Comentario")
        String comentario,
        @Schema(description = "Fecha (hora local del servidor, sin zona)")
        LocalDateTime creadoEn
) {}
