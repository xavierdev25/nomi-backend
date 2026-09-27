package com.nomi.backend.infrastructure.persistence.repository;

import com.nomi.backend.domain.model.product.ProductCategory;
import com.nomi.backend.infrastructure.persistence.entity.ProductEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Repositorio Spring Data de productos.
 *
 * <p>{@code search} filtra por cada criterio solo si no es {@code null}; el nombre se busca por
 * coincidencia parcial sin distinguir mayúsculas. {@code decrementStock} descuenta de forma
 * atómica solo si hay stock suficiente (una cantidad negativa devuelve stock), y ambas
 * operaciones de stock recalculan {@code disponible}.
 */
@Repository
public interface ProductJpaRepository extends JpaRepository<ProductEntity, Long> {

    List<ProductEntity> findByStoreId(Long storeId);
    List<ProductEntity> findByStoreIdAndActivoTrue(Long storeId);
    List<ProductEntity> findByCategoria(ProductCategory categoria);
    Page<ProductEntity> findAll(Pageable pageable);
    Page<ProductEntity> findByStoreId(Long storeId, Pageable pageable);
    List<ProductEntity> findAllByDeletedAtIsNull();
    List<ProductEntity> findByStoreIdAndDeletedAtIsNull(Long storeId);
    List<ProductEntity> findByIdInAndDeletedAtIsNull(List<Long> ids);
    Optional<ProductEntity> findByIdAndDeletedAtIsNull(Long id);
    Page<ProductEntity> findAllByDeletedAtIsNull(Pageable pageable);
    Page<ProductEntity> findByCategoriaAndDeletedAtIsNull(ProductCategory categoria, Pageable pageable);

    @Query("""
    SELECT p FROM ProductEntity p
    WHERE p.activo = true
    AND p.deletedAt IS NULL
    AND (:nombre IS NULL OR LOWER(p.nombre) LIKE LOWER(CONCAT('%', CAST(:nombre AS string), '%')))
    AND (:categoria IS NULL OR p.categoria = :categoria)
    AND (:storeId IS NULL OR p.storeId = :storeId)
    AND (:precioMin IS NULL OR p.precio >= :precioMin)
    AND (:precioMax IS NULL OR p.precio <= :precioMax)
    AND (:disponible IS NULL OR p.disponible = :disponible)
""")
    Page<ProductEntity> search(
            @Param("nombre") String nombre,
            @Param("categoria") ProductCategory categoria,
            @Param("storeId") Long storeId,
            @Param("precioMin") BigDecimal precioMin,
            @Param("precioMax") BigDecimal precioMax,
            @Param("disponible") Boolean disponible,
            Pageable pageable
    );

    @Modifying
    @Query("""
        UPDATE ProductEntity p
        SET p.stock = p.stock - :cantidad,
            p.disponible = CASE WHEN (p.stock - :cantidad) > 0 THEN true ELSE false END
        WHERE p.id = :id
          AND p.deletedAt IS NULL
          AND (:cantidad <= 0 OR p.stock >= :cantidad)
    """)
    int decrementStock(@Param("id") Long id, @Param("cantidad") int cantidad);

    @Modifying
    @Query("""
    UPDATE ProductEntity p
    SET p.stock = p.stock + :cantidad,
        p.disponible = CASE WHEN (p.stock + :cantidad) > 0 THEN true ELSE false END
    WHERE p.id = :id
      AND p.deletedAt IS NULL
""")
    int incrementStock(@Param("id") Long id, @Param("cantidad") int cantidad);
}
