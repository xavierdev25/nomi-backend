package com.nomi.backend.infrastructure.persistence.repository;

import com.nomi.backend.infrastructure.persistence.entity.UserEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repositorio Spring Data de usuarios.
 */
@Repository
public interface UserJpaRepository extends JpaRepository<UserEntity, Long> {

    List<UserEntity> findAllByDeletedAtIsNull();

    Page<UserEntity> findAllByDeletedAtIsNull(Pageable pageable);

    List<UserEntity> findAllByDeletedAtIsNotNull();

    Optional<UserEntity> findByEmailAndDeletedAtIsNull(String email);

    Optional<UserEntity> findByIdAndDeletedAtIsNull(Long id);

    boolean existsByEmailAndDeletedAtIsNull(String email);

    boolean existsByEmail(String email);
}
