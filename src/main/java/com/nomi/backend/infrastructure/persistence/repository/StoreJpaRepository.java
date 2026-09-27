package com.nomi.backend.infrastructure.persistence.repository;

import com.nomi.backend.infrastructure.persistence.entity.StoreEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repositorio Spring Data de tiendas.
 */
@Repository
public interface StoreJpaRepository extends JpaRepository<StoreEntity, Long> {

    List<StoreEntity> findAllByDeletedAtIsNull();
    Page<StoreEntity> findAllByDeletedAtIsNull(Pageable pageable);
    List<StoreEntity> findByIdInAndDeletedAtIsNull(List<Long> ids);
    Optional<StoreEntity> findByIdAndDeletedAtIsNull(Long id);
    Optional<StoreEntity> findByOwnerIdAndDeletedAtIsNull(Long ownerId);
    Optional<StoreEntity> findByOwnerId(Long ownerId);
    boolean existsByOwnerId(Long ownerId);
    List<StoreEntity> findByActivoTrueAndDeletedAtIsNull();
    Page<StoreEntity> findByActivoTrueAndDeletedAtIsNull(Pageable pageable);
    Page<StoreEntity> findByNombreContainingIgnoreCaseAndActivoTrueAndDeletedAtIsNull(String nombre, Pageable pageable);

    @Deprecated
    List<StoreEntity> findByActivoTrue();
}
