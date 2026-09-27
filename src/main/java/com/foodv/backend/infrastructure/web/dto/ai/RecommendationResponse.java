package com.foodv.backend.infrastructure.web.dto.ai;

import java.math.BigDecimal;
import java.util.List;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Recomendaciones personalizadas de productos.")
public record RecommendationResponse(
        @Schema(description = "Usuario", example = "5")
        Long userId,
        @Schema(description = "Productos recomendados, del más al menos relevante; vacía si la IA no respondió")
        List<ProductRecommendationDto> recommendations,
        @Schema(description = "Proveedor que generó la lista, o FALLBACK si el servicio de IA no respondió", example = "FALLBACK")
        String generatedBy
) {
    @Schema(description = "Producto recomendado. Nombre, precio y categoría vienen del catálogo real.")
    public record ProductRecommendationDto(
            @Schema(description = "Producto", example = "4")
            Long productId,
            @Schema(description = "Nombre", example = "Chicha Morada")
            String nombre,
            @Schema(description = "Precio en soles", example = "4.00")
            BigDecimal precio,
            @Schema(description = "Categoría", example = "BEBIDA")
            String categoria,
            @Schema(description = "Relevancia de 0 a 1", example = "0.87")
            double score,
            @Schema(description = "Motivo generado por la IA, ya saneado", example = "Combina con tus pedidos de almuerzo")
            String reason
    ) {}
}
