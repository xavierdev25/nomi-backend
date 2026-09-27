package com.foodv.backend.infrastructure.web.dto.product;

import com.foodv.backend.domain.model.product.DietaryTag;
import com.foodv.backend.domain.model.product.ProductCategory;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Set;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Producto del catálogo.")
public record ProductResponse(
        @Schema(description = "Id del producto", example = "4")
        Long id,
        @Schema(description = "Nombre", example = "Chicha Morada")
        String nombre,
        @Schema(description = "Descripción", example = "Bebida tradicional de maíz morado, 500ml")
        String descripcion,
        @Schema(description = "Precio en soles", example = "4.00")
        BigDecimal precio,
        @Schema(description = "Unidades disponibles", example = "30")
        Integer stock,
        @Schema(description = "URL de la imagen")
        String imagenUrl,
        @Schema(description = "Categoría", example = "BEBIDA")
        ProductCategory categoria,
        @Schema(description = "Tienda", example = "1")
        Long storeId,
        @Schema(description = "Si está publicado", example = "true")
        boolean activo,
        @Schema(description = "Si se puede pedir; false al agotarse el stock", example = "true")
        boolean disponible,
        @Schema(description = "Restricciones para las que el comercio declara apto el producto", example = "[\"VEGETARIANO\", \"VEGANO\"]")
        Set<DietaryTag> etiquetasDieteticas,
        @Schema(description = "Fecha de alta (hora local del servidor, sin zona)")
        LocalDateTime creadoEn
) {}
