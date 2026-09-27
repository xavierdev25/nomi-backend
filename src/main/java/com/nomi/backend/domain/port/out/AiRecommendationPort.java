package com.nomi.backend.domain.port.out;

import com.nomi.backend.domain.model.ai.AiRecommendationRequest;
import com.nomi.backend.domain.model.ai.AiRecommendationResponse;

/**
 * Cliente del servicio de IA. La implementación nunca propaga fallos del servicio: devuelve
 * una respuesta vacía con {@code generatedBy = "FALLBACK"}.
 */
public interface AiRecommendationPort {

    AiRecommendationResponse getRecommendations(AiRecommendationRequest request);
}
