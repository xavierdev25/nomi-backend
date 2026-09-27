package com.nomi.backend.infrastructure.web.dto.product;

import com.nomi.backend.domain.model.product.DietaryTag;
import com.nomi.backend.domain.model.product.ProductCategory;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.Set;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Actualización parcial de un producto: los campos omitidos no cambian.")
public record UpdateProductRequest(
        @Schema(description = "Nombre, 2 a 100 caracteres")
        @Size(min = 2, max = 100) String nombre,
        @Schema(description = "Descripción, hasta 500 caracteres")
        @Size(max = 500) String descripcion,
        @Schema(description = "Precio en soles, de 0.01 a 99999.99")
        @DecimalMin("0.01") @DecimalMax("99999.99") BigDecimal precio,
        @Schema(description = "Unidades disponibles, 0 o más")
        @Min(0) Integer stock,
        @Schema(description = "Categoría")
        ProductCategory categoria,
        @Schema(description = "Si se puede pedir")
        Boolean disponible,
        @Schema(description = "Restricciones para las que es apto; reemplaza las anteriores. Una lista vacía las quita todas")
        Set<DietaryTag> etiquetasDieteticas
) {}
