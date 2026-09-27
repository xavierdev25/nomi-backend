package com.foodv.backend.domain.port.in.ai;

import com.foodv.backend.domain.model.ai.AiRecommendationResponse;

/**
 * Recomendaciones personalizadas para un usuario.
 */
public interface GetRecommendationsUseCase {

    record GetRecommendationsCommand(Long userId, int maxRecommendations) {}

    AiRecommendationResponse execute(GetRecommendationsCommand command);
}
