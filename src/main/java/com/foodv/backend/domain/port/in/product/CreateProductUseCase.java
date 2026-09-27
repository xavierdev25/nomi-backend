package com.foodv.backend.domain.port.in.product;

import com.foodv.backend.domain.model.product.Product;
import com.foodv.backend.domain.model.product.DietaryTag;
import com.foodv.backend.domain.model.product.ProductCategory;

import java.math.BigDecimal;
import java.util.Set;

/**
 * Alta de un producto en la tienda del usuario.
 */
public interface CreateProductUseCase {

    record CreateProductCommand(String nombre, String descripcion, BigDecimal precio, Integer stock, ProductCategory categoria, Long storeId,
                                Set<DietaryTag> etiquetasDieteticas) {}

    Product execute(CreateProductCommand command);
}
