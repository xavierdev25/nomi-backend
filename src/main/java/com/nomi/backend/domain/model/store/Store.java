package com.nomi.backend.domain.model.store;

import com.nomi.backend.domain.model.user.UserRole;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Tienda del campus, gestionada por un usuario {@code COMERCIO} (una tienda por dueño).
 * {@code activo} indica si está habilitada en la plataforma, no si está abierta ahora.
 */
@Getter
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Store {

    @EqualsAndHashCode.Include
    private Long id;

    private String nombre;
    private String descripcion;
    private String imagenUrl;
    private String telefono;
    private UserRole ownerRole;
    private Long ownerId;
    private boolean activo;
    private LocalDateTime creadoEn;
    private LocalDateTime actualizadoEn;
    private LocalTime horarioApertura;
    private LocalTime horarioCierre;
}
