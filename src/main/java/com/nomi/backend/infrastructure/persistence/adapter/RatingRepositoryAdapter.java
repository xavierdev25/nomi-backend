package com.nomi.backend.infrastructure.persistence.adapter;

import com.nomi.backend.domain.model.rating.Rating;
import com.nomi.backend.domain.model.rating.StoreRatingSummary;
import com.nomi.backend.domain.port.out.RatingRepositoryPort;
import com.nomi.backend.infrastructure.persistence.repository.RatingJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Implementación JPA de {@code RatingRepositoryPort}.
 */
@Component
@RequiredArgsConstructor
public class RatingRepositoryAdapter implements RatingRepositoryPort {

    private final RatingJpaRepository ratingJpaRepository;
    private final RatingEntityMapper ratingEntityMapper;

    @Override
    public Rating save(Rating rating) {
        return ratingEntityMapper.toDomain(ratingJpaRepository.save(ratingEntityMapper.toEntity(rating)));
    }

    @Override
    public Optional<Rating> findByOrderId(Long orderId) {
        return ratingJpaRepository.findByOrderId(orderId)
                .map(ratingEntityMapper::toDomain);
    }

    @Override
    public List<Rating> findByStoreId(Long storeId) {
        return ratingJpaRepository.findByStoreIdOrderByCreadoEnDesc(storeId).stream()
                .map(ratingEntityMapper::toDomain)
                .toList();
    }

    @Override
    public boolean existsByOrderId(Long orderId) {
        return ratingJpaRepository.existsByOrderId(orderId);
    }

    @Override
    public StoreRatingSummary findStoreRatingSummary(Long storeId) {
        List<Object[]> results = ratingJpaRepository.findStoreRatingSummary(storeId);
        Object[] values = results.get(0);
        return StoreRatingSummary.builder()
                .storeId(storeId)
                .promedio(((Number) values[0]).doubleValue())
                .total(((Number) values[1]).longValue())
                .build();
    }
}
