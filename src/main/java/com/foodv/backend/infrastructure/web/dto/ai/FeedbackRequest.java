package com.foodv.backend.infrastructure.web.dto.ai;

import jakarta.validation.constraints.NotNull;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Valoración del usuario sobre un producto recomendado.")
public record FeedbackRequest(
        @Schema(description = "Producto recomendado", example = "4")
        @NotNull Long productId,
        @Schema(description = "true si le gustó, false si no", example = "true")
        @NotNull Boolean liked
) {}
