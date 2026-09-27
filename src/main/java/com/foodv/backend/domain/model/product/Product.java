package com.foodv.backend.domain.model.product;

import com.foodv.backend.domain.model.user.DietaryRestriction;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Set;

/**
 * Producto del catálogo de una tienda.
 *
 * <p>{@code activo} indica si está publicado; {@code disponible} si se puede pedir (pasa a
 * {@code false} al agotarse el stock). El borrado es lógico ({@code deletedAt}).
 * {@code etiquetasDieteticas} son las restricciones para las que el comercio declara el producto
 * apto; sin etiquetas no es apto para ninguna.
 */
@Getter
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Product {

    @EqualsAndHashCode.Include
    private Long id;

    private String nombre;
    private String descripcion;
    private BigDecimal precio;
    private Integer stock;
    private String imagenUrl;
    private ProductCategory categoria;
    private Long storeId;
    private boolean activo;
    private boolean disponible;
    @Builder.Default
    private Set<DietaryTag> etiquetasDieteticas = Set.of();
    private LocalDateTime creadoEn;
    private LocalDateTime actualizadoEn;

    /** Si cumple todas las restricciones; con ninguna, siempre. */
    public boolean isSuitableFor(Set<DietaryRestriction> restrictions) {
        Set<DietaryTag> tags = etiquetasDieteticas == null ? Set.of() : etiquetasDieteticas;
        return restrictions.stream().allMatch(restriction -> restriction.isSatisfiedBy(tags));
    }

    /** Si se puede pedir ahora: publicado, disponible y con stock. */
    public boolean isOrderable() {
        return activo && disponible && stock != null && stock > 0;
    }
}
