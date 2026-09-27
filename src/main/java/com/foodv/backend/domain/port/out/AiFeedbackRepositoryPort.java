package com.foodv.backend.domain.port.out;

import com.foodv.backend.domain.model.ai.AiFeedback;

import java.util.List;
import java.util.Optional;

/**
 * Persistencia de valoraciones de recomendaciones.
 */
public interface AiFeedbackRepositoryPort {

    AiFeedback save(AiFeedback feedback);

    Optional<AiFeedback> findByUserIdAndProductId(Long userId, Long productId);

    List<AiFeedback> findLikedByUserId(Long userId);

    List<AiFeedback> findDislikedByUserId(Long userId);
}
