package com.foodv.backend.application.product;

import com.foodv.backend.domain.common.PageQuery;
import com.foodv.backend.domain.common.PagedResult;
import com.foodv.backend.domain.model.product.Product;
import com.foodv.backend.domain.model.product.ProductCategory;
import com.foodv.backend.domain.port.in.product.FindProductUseCase;
import com.foodv.backend.domain.port.out.ProductRepositoryPort;
import com.foodv.backend.domain.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * Consulta y búsqueda del catálogo.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FindProductHandler implements FindProductUseCase {

    private final ProductRepositoryPort productRepositoryPort;

    @Override
    public Product findById(Long id) {
        return productRepositoryPort.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Producto no encontrado"));
    }

    @Override
    public List<Product> findByStoreId(Long storeId) {
        return productRepositoryPort.findByStoreId(storeId);
    }

    @Override
    public List<Product> findByStoreIdActivos(Long storeId) {
        return productRepositoryPort.findByStoreIdAndActivoTrue(storeId);
    }

    @Override
    public List<Product> findByCategoria(ProductCategory categoria) {
        return productRepositoryPort.findByCategoria(categoria);
    }

    @Override
    public PagedResult<Product> findByCategoriaPaginated(ProductCategory categoria, PageQuery query) {
        return productRepositoryPort.findByCategoriaPaginated(categoria, query);
    }

    @Override
    public List<Product> findAll() {
        return productRepositoryPort.findAll();
    }

    @Override
    public PagedResult<Product> findAllPaginated(PageQuery query) {
        return productRepositoryPort.findAllPaginated(query);
    }

    @Override
    public PagedResult<Product> findByStoreIdPaginated(Long storeId, PageQuery query) {
        return productRepositoryPort.findByStoreIdPaginated(storeId, query);
    }

    @Override
    public PagedResult<Product> search(String nombre, ProductCategory categoria, Long storeId,
                                       BigDecimal precioMin, BigDecimal precioMax,
                                       Boolean disponible, PageQuery query) {
        return productRepositoryPort.search(nombre, categoria, storeId, precioMin, precioMax, disponible, query);
    }
}
