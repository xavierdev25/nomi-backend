package com.nomi.backend.domain.model.ai;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;

/**
 * Recomendaciones devueltas por el servicio de IA. {@code generatedBy} indica el proveedor, o
 * {@code FALLBACK} si el servicio no respondió.
 */
@Getter
@Builder
public class AiRecommendationResponse {

    private Long userId;
    private List<ProductRecommendation> recommendations;
    private String generatedBy;

    public record ProductRecommendation(Long productId, String nombre, BigDecimal precio, String categoria, double score, String reason) {}
}
