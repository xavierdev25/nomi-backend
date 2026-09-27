package com.nomi.backend.domain.port.in.product;

import com.nomi.backend.domain.model.product.Product;
import com.nomi.backend.domain.model.product.DietaryTag;
import com.nomi.backend.domain.model.product.ProductCategory;

import java.math.BigDecimal;
import java.util.Set;

/**
 * Edición parcial de un producto: los campos {@code null} no cambian.
 */
public interface UpdateProductUseCase {

    record UpdateProductCommand(
            String nombre,
            String descripcion,
            BigDecimal precio,
            Integer stock,
            ProductCategory categoria,
            Boolean disponible,
            Set<DietaryTag> etiquetasDieteticas
    ) {}

    Product execute(Long id, UpdateProductCommand command);
}
