package com.foodv.backend.infrastructure.persistence.entity;

import com.foodv.backend.domain.model.user.UserRole;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Tabla {@code stores}, con borrado lógico ({@code deleted_at}).
 */
@Entity
@Table(name = "stores")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StoreEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nombre;

    private String descripcion;

    @Column(name = "imagen_url")
    private String imagenUrl;

    private String telefono;

    @Enumerated(EnumType.STRING)
    @Column(name = "owner_role", nullable = false)
    private UserRole ownerRole;

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    @Column(nullable = false)
    private boolean activo;

    @Column(name = "creado_en")
    private LocalDateTime creadoEn;

    @Column(name = "actualizado_en")
    private LocalDateTime actualizadoEn;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "horario_apertura")
    private java.time.LocalTime horarioApertura;

    @Column(name = "horario_cierre")
    private java.time.LocalTime horarioCierre;
}
