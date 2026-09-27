package com.nomi.backend.application.product;

import com.nomi.backend.domain.port.in.product.DeleteProductUseCase;
import com.nomi.backend.domain.port.out.ProductRepositoryPort;
import com.nomi.backend.domain.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Borrado lógico de un producto.
 */
@Service
@RequiredArgsConstructor
public class DeleteProductHandler implements DeleteProductUseCase {

    private final ProductRepositoryPort productRepositoryPort;

    @Override
    @Transactional
    @CacheEvict(value = "products", key = "#result")
    public Long execute(Long id) {
        Long storeId = productRepositoryPort.findById(id)
                .map(product -> product.getStoreId())
                .orElseThrow(() -> new ResourceNotFoundException("Producto no encontrado"));

        productRepositoryPort.deleteById(id);
        return storeId;
    }
}
