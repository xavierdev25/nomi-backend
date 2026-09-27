package com.foodv.backend.infrastructure.web.dto.ai;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Valoración registrada.")
public record FeedbackResponse(
        @Schema(description = "Id de la valoración", example = "1")
        Long id,
        @Schema(description = "Usuario que valoró", example = "5")
        Long userId,
        @Schema(description = "Producto valorado", example = "4")
        Long productId,
        @Schema(description = "Si le gustó", example = "true")
        Boolean liked,
        @Schema(description = "Mensaje de confirmación para mostrar al usuario")
        String message
) {}
