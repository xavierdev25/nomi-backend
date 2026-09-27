package com.foodv.backend.infrastructure.persistence.adapter;

import com.foodv.backend.domain.common.PageQuery;
import com.foodv.backend.domain.common.PagedResult;
import com.foodv.backend.domain.model.product.Product;
import com.foodv.backend.domain.model.product.ProductCategory;
import com.foodv.backend.domain.port.out.ProductRepositoryPort;
import com.foodv.backend.infrastructure.common.PagingMapper;
import com.foodv.backend.infrastructure.persistence.repository.ProductJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Implementación JPA de {@code ProductRepositoryPort}. Excluye productos borrados y aplica el
 * borrado lógico.
 */
@Component
@RequiredArgsConstructor
public class ProductRepositoryAdapter implements ProductRepositoryPort {

    private final ProductJpaRepository jpaRepository;
    private final ProductEntityMapper mapper;

    @Override
    public PagedResult<Product> search(String nombre, ProductCategory categoria, Long storeId,
                                       BigDecimal precioMin, BigDecimal precioMax,
                                       Boolean disponible, PageQuery query) {
        return PagingMapper.toDomain(
                jpaRepository.search(nombre, categoria, storeId, precioMin, precioMax, disponible,
                        PagingMapper.toPageable(query)),
                mapper::toDomain
        );
    }

    @Override
    public Product save(Product product) {
        return mapper.toDomain(jpaRepository.save(mapper.toEntity(product)));
    }

    @Override
    public Optional<Product> findById(Long id) {
        return jpaRepository.findByIdAndDeletedAtIsNull(id).map(mapper::toDomain);
    }

    @Override
    public List<Product> findAllById(List<Long> ids) {
        return jpaRepository.findByIdInAndDeletedAtIsNull(ids).stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public List<Product> findByStoreId(Long storeId) {
        return jpaRepository.findByStoreIdAndDeletedAtIsNull(storeId).stream().map(mapper::toDomain).toList();
    }

    @Override
    public List<Product> findByStoreIdAndActivoTrue(Long storeId) {
        return jpaRepository.findByStoreIdAndActivoTrue(storeId).stream().map(mapper::toDomain).toList();
    }

    @Override
    public List<Product> findByCategoria(ProductCategory categoria) {
        return jpaRepository.findByCategoria(categoria).stream().map(mapper::toDomain).toList();
    }

    @Override
    public PagedResult<Product> findByCategoriaPaginated(ProductCategory categoria, PageQuery query) {
        return PagingMapper.toDomain(
                jpaRepository.findByCategoriaAndDeletedAtIsNull(categoria, PagingMapper.toPageable(query)),
                mapper::toDomain
        );
    }

    @Override
    public List<Product> findAll() {
        return jpaRepository.findAllByDeletedAtIsNull().stream().map(mapper::toDomain).toList();
    }

    @Override
    public void deleteById(Long id) {
        jpaRepository.findByIdAndDeletedAtIsNull(id).ifPresent(entity -> {
            entity.setDeletedAt(LocalDateTime.now());
            jpaRepository.save(entity);
        });
    }

    @Override
    public PagedResult<Product> findAllPaginated(PageQuery query) {
        return PagingMapper.toDomain(
                jpaRepository.findAllByDeletedAtIsNull(PagingMapper.toPageable(query)),
                mapper::toDomain
        );
    }

    @Override
    public PagedResult<Product> findByStoreIdPaginated(Long storeId, PageQuery query) {
        return PagingMapper.toDomain(
                jpaRepository.findByStoreId(storeId, PagingMapper.toPageable(query)),
                mapper::toDomain
        );
    }

    @Override
    public int decrementStock(Long productId, int cantidad) {
        return jpaRepository.decrementStock(productId, cantidad);
    }

    @Override
    public int incrementStock(Long productId, int cantidad) {
        return jpaRepository.incrementStock(productId, cantidad);
    }
}
