package com.nomi.backend.infrastructure.persistence.adapter;

import com.nomi.backend.domain.model.ai.AiFeedback;
import com.nomi.backend.domain.port.out.AiFeedbackRepositoryPort;
import com.nomi.backend.infrastructure.persistence.entity.AiFeedbackEntity;
import com.nomi.backend.infrastructure.persistence.repository.AiFeedbackRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Implementación JPA de {@code AiFeedbackRepositoryPort}.
 */
@Component
@RequiredArgsConstructor
public class AiFeedbackRepositoryAdapter implements AiFeedbackRepositoryPort {

    private final AiFeedbackRepository repository;

    @Override
    public AiFeedback save(AiFeedback feedback) {
        AiFeedbackEntity entity = AiFeedbackEntity.builder()
                .id(feedback.getId())
                .userId(feedback.getUserId())
                .productId(feedback.getProductId())
                .liked(feedback.getLiked())
                .context(feedback.getContext())
                .creadoEn(feedback.getCreadoEn() != null ? feedback.getCreadoEn() : LocalDateTime.now())
                .build();
        AiFeedbackEntity saved = repository.save(entity);
        return toDomain(saved);
    }

    @Override
    public Optional<AiFeedback> findByUserIdAndProductId(Long userId, Long productId) {
        return repository.findByUserIdAndProductId(userId, productId).map(this::toDomain);
    }

    @Override
    public List<AiFeedback> findLikedByUserId(Long userId) {
        return repository.findByUserIdAndLikedTrue(userId).stream().map(this::toDomain).toList();
    }

    @Override
    public List<AiFeedback> findDislikedByUserId(Long userId) {
        return repository.findByUserIdAndLikedFalse(userId).stream().map(this::toDomain).toList();
    }

    private AiFeedback toDomain(AiFeedbackEntity e) {
        return AiFeedback.builder()
                .id(e.getId())
                .userId(e.getUserId())
                .productId(e.getProductId())
                .liked(e.getLiked())
                .context(e.getContext())
                .creadoEn(e.getCreadoEn())
                .build();
    }
}
