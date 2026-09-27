package com.nomi.backend.infrastructure.persistence.repository;

import com.nomi.backend.infrastructure.persistence.entity.AiFeedbackEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repositorio Spring Data de valoraciones de IA.
 */
@Repository
public interface AiFeedbackRepository extends JpaRepository<AiFeedbackEntity, Long> {

    Optional<AiFeedbackEntity> findByUserIdAndProductId(Long userId, Long productId);

    List<AiFeedbackEntity> findByUserIdAndLikedTrue(Long userId);

    List<AiFeedbackEntity> findByUserIdAndLikedFalse(Long userId);

    @Query("SELECT f.productId, COUNT(f) as likes FROM AiFeedbackEntity f WHERE f.liked = true GROUP BY f.productId ORDER BY likes DESC")
    List<Object[]> findTopLikedProducts();
}
