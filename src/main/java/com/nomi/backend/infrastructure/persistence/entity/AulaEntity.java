package com.nomi.backend.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Tabla {@code aulas}; {@code codigo} es único.
 */
@Entity
@Table(name = "aulas")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AulaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String codigo;

    @Column(nullable = false)
    private String nombre;

    private String piso;

    private String pabellon;

    @Column(nullable = false)
    private boolean activo;
}
