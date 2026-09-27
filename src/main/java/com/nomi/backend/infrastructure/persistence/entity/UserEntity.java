package com.nomi.backend.infrastructure.persistence.entity;

import com.nomi.backend.domain.model.user.BudgetRange;
import com.nomi.backend.domain.model.user.UserRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Array;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/**
 * Tabla {@code users}, con borrado lógico ({@code deleted_at}). Preferencias y restricciones
 * son arrays de PostgreSQL.
 */
@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nombres;

    @Column(nullable = false)
    private String apellidos;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String password;

    private String telefono;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserRole role;

    @Column(nullable = false)
    private boolean activo;

    @Column(name = "creado_en")
    private LocalDateTime creadoEn;

    @Column(name = "actualizado_en")
    private LocalDateTime actualizadoEn;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "es_repartidor")
    private Boolean esRepartidor;

    @Column(name = "campus_id")
    private Long campusId;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "preferences", columnDefinition = "text[]")
    private String[] preferences;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "restrictions", columnDefinition = "text[]")
    private String[] restrictions;

    @Enumerated(EnumType.STRING)
    @Column(name = "budget_range", length = 20)
    private BudgetRange budgetRange;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "cuisine_types", columnDefinition = "text[]")
    private String[] cuisineTypes;
}
