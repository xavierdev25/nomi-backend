package com.foodv.backend.infrastructure.persistence.repository;

import com.foodv.backend.infrastructure.persistence.entity.FavoriteProductEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repositorio Spring Data de productos favoritos.
 */
@Repository
public interface FavoriteProductJpaRepository extends JpaRepository<FavoriteProductEntity, Long> {

    Optional<FavoriteProductEntity> findByUserIdAndProductId(Long userId, Long productId);

    List<FavoriteProductEntity> findByUserIdOrderByCreadoEnDesc(Long userId);

    boolean existsByUserIdAndProductId(Long userId, Long productId);

    void deleteByUserIdAndProductId(Long userId, Long productId);
}
