package com.nomi.backend.infrastructure.web.dto.ai;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Parámetros de recomendación. Ningún endpoint actual lo usa: /ai/recommendations recibe maxRecommendations como query.")
public record RecommendationRequest(
        @Schema(description = "Usuario", example = "5")
        @NotNull Long userId,
        @Schema(description = "Restricciones alimentarias")
        List<String> restrictions,
        @Schema(description = "Preferencias de comida")
        List<String> preferences,
        @Schema(description = "Cantidad de recomendaciones, de 1 a 20; 0 equivale a 5", example = "5")
        @Min(1) @Max(20) int maxRecommendations
) {
    public RecommendationRequest {
        if (restrictions == null) restrictions = List.of();
        if (preferences == null) preferences = List.of();
        if (maxRecommendations == 0) maxRecommendations = 5;
    }
}
