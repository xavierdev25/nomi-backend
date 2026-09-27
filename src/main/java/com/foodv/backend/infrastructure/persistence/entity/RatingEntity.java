package com.foodv.backend.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Tabla {@code ratings}; una por pedido.
 */
@Entity
@Table(
        name = "ratings",
        uniqueConstraints = @UniqueConstraint(name = "uk_ratings_order", columnNames = "order_id")
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RatingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "store_id", nullable = false)
    private Long storeId;

    @Column(nullable = false)
    private Integer rating;

    @Column(length = 500)
    private String comentario;

    @Column(name = "creado_en", nullable = false)
    private LocalDateTime creadoEn;
}
