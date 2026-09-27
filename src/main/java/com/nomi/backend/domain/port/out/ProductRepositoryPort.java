package com.nomi.backend.domain.port.out;

import com.nomi.backend.domain.common.PageQuery;
import com.nomi.backend.domain.common.PagedResult;
import com.nomi.backend.domain.model.product.Product;
import com.nomi.backend.domain.model.product.ProductCategory;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Persistencia del catálogo. Las lecturas excluyen productos borrados lógicamente.
 */
public interface ProductRepositoryPort {

    Product save(Product product);

    Optional<Product> findById(Long id);

    List<Product> findAllById(List<Long> ids);

    List<Product> findByStoreId(Long storeId);

    List<Product> findByStoreIdAndActivoTrue(Long storeId);

    List<Product> findByCategoria(ProductCategory categoria);

    PagedResult<Product> findByCategoriaPaginated(ProductCategory categoria, PageQuery query);

    List<Product> findAll();

    void deleteById(Long id);

    PagedResult<Product> findAllPaginated(PageQuery query);

    PagedResult<Product> findByStoreIdPaginated(Long storeId, PageQuery query);

    PagedResult<Product> search(String nombre, ProductCategory categoria, Long storeId,
                                BigDecimal precioMin, BigDecimal precioMax,
                                Boolean disponible, PageQuery query);

    /**
     * Descuenta stock de forma atómica solo si alcanza, y actualiza {@code disponible}.
     * Una cantidad negativa devuelve stock.
     *
     * @return filas actualizadas: 0 si no había stock suficiente
     */
    int decrementStock(Long productId, int cantidad);
    /**
     * Devuelve stock y actualiza {@code disponible}.
     */
    int incrementStock(Long productId, int cantidad);
}
