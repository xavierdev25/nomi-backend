package com.nomi.backend.infrastructure.persistence.entity;

import com.nomi.backend.domain.model.product.ProductCategory;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Tabla {@code products}, con borrado lógico ({@code deleted_at}).
 */
@Entity
@Table(name = "products")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nombre;

    private String descripcion;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal precio;

    @Column(nullable = false)
    private Integer stock;

    @Column(name = "imagen_url")
    private String imagenUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProductCategory categoria;

    @Column(name = "store_id", nullable = false)
    private Long storeId;

    @Column(nullable = false)
    private boolean activo;

    @Column(nullable = false)
    private boolean disponible;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "etiquetas_dieteticas", columnDefinition = "text[]", nullable = false)
    @Builder.Default
    private String[] etiquetasDieteticas = new String[0];

    @Column(name = "creado_en")
    private LocalDateTime creadoEn;

    @Column(name = "actualizado_en")
    private LocalDateTime actualizadoEn;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;
}
