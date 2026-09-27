package com.nomi.backend.infrastructure.web.dto.rating;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Calificación de un pedido entregado.")
public record CreateRatingRequest(
        @NotNull(message = "Rating obligatorio")
        @Min(value = 1, message = "Rating mínimo 1")
        @Max(value = 5, message = "Rating máximo 5")
        @Schema(description = "Puntuación de 1 a 5", example = "5")
        Integer rating,

        @Size(max = 500, message = "Comentario máximo 500 caracteres")
        @Schema(description = "Comentario opcional, hasta 500 caracteres", example = "Llegó rápido y caliente")
        String comentario
) {}
