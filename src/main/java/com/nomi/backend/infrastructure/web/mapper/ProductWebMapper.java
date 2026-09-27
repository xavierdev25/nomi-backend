package com.nomi.backend.infrastructure.web.mapper;

import com.nomi.backend.domain.model.product.Product;
import com.nomi.backend.domain.port.in.product.CreateProductUseCase;
import com.nomi.backend.domain.port.in.product.UpdateProductUseCase;
import com.nomi.backend.infrastructure.web.dto.product.CreateProductRequest;
import com.nomi.backend.infrastructure.web.dto.product.ProductResponse;
import com.nomi.backend.infrastructure.web.dto.product.UpdateProductRequest;
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
