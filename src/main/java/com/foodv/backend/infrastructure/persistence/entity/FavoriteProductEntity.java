package com.foodv.backend.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Tabla {@code favorite_products}; única por usuario y producto.
 */
@Entity
@Table(
        name = "favorite_products",
        uniqueConstraints = @UniqueConstraint(name = "uk_favorite_products_user_product", columnNames = {"user_id", "product_id"})
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FavoriteProductEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "creado_en", nullable = false)
    private LocalDateTime creadoEn;
}
