package com.foodv.backend.domain.port.in.product;

import com.foodv.backend.domain.common.PageQuery;
import com.foodv.backend.domain.common.PagedResult;
import com.foodv.backend.domain.model.product.Product;
import com.foodv.backend.domain.model.product.ProductCategory;

import java.math.BigDecimal;
import java.util.List;

/**
 * Consulta y búsqueda del catálogo.
 */
public interface FindProductUseCase {

    Product findById(Long id);

    List<Product> findByStoreId(Long storeId);

    List<Product> findByStoreIdActivos(Long storeId);

    List<Product> findByCategoria(ProductCategory categoria);

    PagedResult<Product> findByCategoriaPaginated(ProductCategory categoria, PageQuery query);

    List<Product> findAll();

    PagedResult<Product> findAllPaginated(PageQuery query);

    PagedResult<Product> findByStoreIdPaginated(Long storeId, PageQuery query);

    PagedResult<Product> search(String nombre, ProductCategory categoria, Long storeId,
                                BigDecimal precioMin, BigDecimal precioMax,
                                Boolean disponible, PageQuery query);
}
