package com.nomi.backend.infrastructure.persistence.repository;

import com.nomi.backend.domain.model.user.UserRole;
import com.nomi.backend.infrastructure.persistence.entity.UserEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Consultas del repositorio de usuarios contra PostgreSQL real: búsqueda por email y borrado
 * lógico. Necesita PostgreSQL en {@code localhost:5432}.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("UserJpaRepository - Integración con PostgreSQL real")
class UserRepositoryIntegrationTest {

    @Autowired
    private UserJpaRepository userJpaRepository;

    private UserEntity buildUser(String email) {
        return UserEntity.builder()
                .nombres("Xavier")
                .apellidos("David")
                .email(email)
                .password("hashedPassword")
                .role(UserRole.ESTUDIANTE)
                .activo(true)
                .creadoEn(LocalDateTime.now())
                .build();
    }

    @Test
    @DisplayName("Guardar y encontrar usuario por email")
    void save_and_find_by_email() {
        String email = "test-" + UUID.randomUUID() + "@nomi.com";
        userJpaRepository.save(buildUser(email));

        Optional<UserEntity> found = userJpaRepository.findByEmailAndDeletedAtIsNull(email);
        assertTrue(found.isPresent());
        assertEquals("Xavier", found.get().getNombres());
    }

    @Test
    @DisplayName("Soft delete — usuario no aparece en findByEmailAndDeletedAtIsNull")
    void soft_delete_hides_user() {
        String email = "borrar-" + UUID.randomUUID() + "@nomi.com";
        UserEntity saved = userJpaRepository.save(buildUser(email));
        saved.setDeletedAt(LocalDateTime.now());
        userJpaRepository.save(saved);

        Optional<UserEntity> found = userJpaRepository.findByEmailAndDeletedAtIsNull(email);
        assertTrue(found.isEmpty());

        assertTrue(userJpaRepository.findAllByDeletedAtIsNotNull().stream()
                .anyMatch(u -> u.getEmail().equals(email)));
    }

    @Test
    @DisplayName("existsByEmail retorna true si el email existe")
    void exists_by_email() {
        String email = "existe-" + UUID.randomUUID() + "@nomi.com";
        userJpaRepository.save(buildUser(email));

        assertTrue(userJpaRepository.existsByEmail(email));
        assertFalse(userJpaRepository.existsByEmail("noexiste-" + UUID.randomUUID() + "@nomi.com"));
    }
}
