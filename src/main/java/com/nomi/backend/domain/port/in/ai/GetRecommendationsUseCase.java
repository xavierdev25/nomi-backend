package com.nomi.backend.domain.port.in.ai;

import com.nomi.backend.domain.model.ai.AiRecommendationResponse;

/**
 * Recomendaciones personalizadas para un usuario.
 */
public interface GetRecommendationsUseCase {

    record GetRecommendationsCommand(Long userId, int maxRecommendations) {}

    AiRecommendationResponse execute(GetRecommendationsCommand command);
}
