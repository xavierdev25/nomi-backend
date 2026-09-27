package com.foodv.backend.infrastructure.web.dto.product;

import com.foodv.backend.domain.model.product.DietaryTag;
import com.foodv.backend.domain.model.product.ProductCategory;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.Set;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Alta de un producto en la tienda del usuario autenticado.")
public record CreateProductRequest(
        @NotBlank(message = "Nombre obligatorio")
        @Size(min = 2, max = 100, message = "Nombre entre 2 y 100 caracteres")
        @Schema(description = "Nombre, 2 a 100 caracteres", example = "Aji de Gallina")
        String nombre,

        @Size(max = 500, message = "Descripción máximo 500 caracteres")
        @Schema(description = "Descripción, hasta 500 caracteres", example = "Pollo deshilachado en crema de ají amarillo con arroz")
        String descripcion,

        @NotNull(message = "Precio obligatorio")
        @DecimalMin(value = "0.01", message = "Precio mínimo 0.01")
        @DecimalMax(value = "99999.99", message = "Precio máximo 99999.99")
        @Schema(description = "Precio en soles, de 0.01 a 99999.99", example = "12.50")
        BigDecimal precio,

        @NotNull(message = "Stock obligatorio")
        @Min(value = 0, message = "Stock no puede ser negativo")
        @Schema(description = "Unidades disponibles, 0 o más", example = "20")
        Integer stock,

        @NotNull(message = "Categoría obligatoria")
        @Schema(description = "Categoría", example = "COMIDA")
        ProductCategory categoria,

        @Schema(description = "Restricciones para las que el producto es apto: VEGETARIANO, VEGANO, SIN_GLUTEN, SIN_LACTOSA. Sin etiquetas no se recomienda a estudiantes con restricciones", example = "[\"VEGETARIANO\"]")
        Set<DietaryTag> etiquetasDieteticas
) {}
