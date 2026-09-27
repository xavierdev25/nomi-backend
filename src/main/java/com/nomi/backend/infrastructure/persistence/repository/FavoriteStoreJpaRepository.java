package com.nomi.backend.infrastructure.persistence.repository;

import com.nomi.backend.infrastructure.persistence.entity.FavoriteStoreEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repositorio Spring Data de tiendas favoritas.
 */
@Repository
public interface FavoriteStoreJpaRepository extends JpaRepository<FavoriteStoreEntity, Long> {

    Optional<FavoriteStoreEntity> findByUserIdAndStoreId(Long userId, Long storeId);

    List<FavoriteStoreEntity> findByUserIdOrderByCreadoEnDesc(Long userId);

    boolean existsByUserIdAndStoreId(Long userId, Long storeId);

    void deleteByUserIdAndStoreId(Long userId, Long storeId);
}
