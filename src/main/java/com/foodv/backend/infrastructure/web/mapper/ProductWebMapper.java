package com.foodv.backend.infrastructure.web.mapper;

import com.foodv.backend.domain.model.product.Product;
import com.foodv.backend.domain.port.in.product.CreateProductUseCase;
import com.foodv.backend.domain.port.in.product.UpdateProductUseCase;
import com.foodv.backend.infrastructure.web.dto.product.CreateProductRequest;
import com.foodv.backend.infrastructure.web.dto.product.ProductResponse;
import com.foodv.backend.infrastructure.web.dto.product.UpdateProductRequest;
import org.mapstruct.Mapper;

/**
 * Mapeo MapStruct entre DTOs web y el dominio de productos.
 */
@Mapper(componentModel = "spring")
public interface ProductWebMapper {

    ProductResponse toResponse(Product product);

    UpdateProductUseCase.UpdateProductCommand toCommand(UpdateProductRequest request);

    default CreateProductUseCase.CreateProductCommand toCommandWithStore(CreateProductRequest request, Long storeId) {
        return new CreateProductUseCase.CreateProductCommand(
                request.nombre(),
                request.descripcion(),
                request.precio(),
                request.stock(),
                request.categoria(),
                storeId,
                request.etiquetasDieteticas()
        );
    }
}
