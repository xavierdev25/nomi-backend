package com.foodv.backend.infrastructure.persistence.repository;

import com.foodv.backend.infrastructure.persistence.entity.RatingEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repositorio Spring Data de calificaciones.
 */
@Repository
public interface RatingJpaRepository extends JpaRepository<RatingEntity, Long> {

    Optional<RatingEntity> findByOrderId(Long orderId);

    List<RatingEntity> findByStoreIdOrderByCreadoEnDesc(Long storeId);

    boolean existsByOrderId(Long orderId);

    @Query("SELECT COALESCE(AVG(r.rating), 0), COUNT(r) FROM RatingEntity r WHERE r.storeId = :storeId")
    List<Object[]> findStoreRatingSummary(Long storeId);
}
