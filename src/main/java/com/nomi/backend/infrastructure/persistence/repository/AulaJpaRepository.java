package com.nomi.backend.infrastructure.persistence.repository;

import com.nomi.backend.infrastructure.persistence.entity.AulaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repositorio Spring Data de aulas.
 */
@Repository
public interface AulaJpaRepository extends JpaRepository<AulaEntity, Long> {

    Optional<AulaEntity> findByCodigo(String codigo);

    boolean existsByCodigo(String codigo);

    List<AulaEntity> findByActivoTrue();
}
