package com.nomi.backend.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Tabla {@code favorite_stores}; única por usuario y tienda.
 */
@Entity
@Table(
        name = "favorite_stores",
        uniqueConstraints = @UniqueConstraint(name = "uk_favorite_stores_user_store", columnNames = {"user_id", "store_id"})
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FavoriteStoreEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "store_id", nullable = false)
    private Long storeId;

    @Column(name = "creado_en", nullable = false)
    private LocalDateTime creadoEn;
}
