package com.nomi.backend.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Tabla {@code ai_recommendation_feedback}; única por usuario y producto.
 */
@Entity
@Table(name = "ai_recommendation_feedback")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiFeedbackEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(nullable = false)
    private Boolean liked;

    @Column(length = 20)
    private String context;

    @Column(name = "creado_en")
    private LocalDateTime creadoEn;
}
