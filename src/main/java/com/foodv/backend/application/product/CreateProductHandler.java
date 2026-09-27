package com.foodv.backend.application.product;

import com.foodv.backend.domain.model.product.Product;
import com.foodv.backend.domain.model.store.Store;
import com.foodv.backend.domain.port.in.product.CreateProductUseCase;
import com.foodv.backend.domain.port.out.ProductRepositoryPort;
import com.foodv.backend.domain.port.out.StoreRepositoryPort;
import com.foodv.backend.domain.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Set;

/**
 * Crea un producto en una tienda activa; queda disponible si tiene stock.
 */
@Service
@RequiredArgsConstructor
public class CreateProductHandler implements CreateProductUseCase {

    private final ProductRepositoryPort productRepositoryPort;
    private final StoreRepositoryPort storeRepositoryPort;

    @Override
    @Transactional
    @CacheEvict(value = "products", key = "#command.storeId()")
    public Product execute(CreateProductCommand command) {
        Store store = storeRepositoryPort.findById(command.storeId())
                .orElseThrow(() -> new ResourceNotFoundException("Tienda no encontrada"));

        if (!store.isActivo()) {
            throw new IllegalArgumentException("La tienda no está activa");
        }

        if (command.precio() == null || command.precio().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("El precio debe ser mayor a 0");
        }
        if (command.stock() == null || command.stock() < 0) {
            throw new IllegalArgumentException("Stock inválido");
        }

        Product product = Product.builder()
                .nombre(command.nombre() == null ? null : command.nombre().trim())
                .descripcion(command.descripcion() == null ? null : command.descripcion().trim())
                .precio(command.precio())
                .stock(command.stock())
                .categoria(command.categoria())
                .storeId(command.storeId())
                .activo(true)
                .disponible(command.stock() > 0)
                .etiquetasDieteticas(command.etiquetasDieteticas() == null ? Set.of() : Set.copyOf(command.etiquetasDieteticas()))
                .creadoEn(LocalDateTime.now())
                .build();

        return productRepositoryPort.save(product);
    }
}
