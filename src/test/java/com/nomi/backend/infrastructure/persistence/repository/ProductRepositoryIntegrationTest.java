package com.nomi.backend.infrastructure.persistence.repository;

import com.nomi.backend.domain.model.product.ProductCategory;
import com.nomi.backend.infrastructure.persistence.entity.ProductEntity;
import com.nomi.backend.infrastructure.persistence.entity.StoreEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.context.jdbc.Sql;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Consultas del repositorio de productos contra PostgreSQL real: búsqueda con filtros y
 * borrado lógico. Necesita PostgreSQL en {@code localhost:5432}.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@Sql(scripts = "/db/seed-test-data.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
@DisplayName("ProductJpaRepository - Integración con PostgreSQL real")
class ProductRepositoryIntegrationTest {

    @Autowired
    private ProductJpaRepository productJpaRepository;

    @Autowired
    private StoreJpaRepository storeJpaRepository;

    private UserJpaRepository userJpaRepository;

    private Long testStoreId;

    @BeforeEach
    void setUp() {
        testStoreId = storeJpaRepository.findAll().stream()
                .findFirst()
                .map(s -> s.getId())
                .orElse(1L);
    }

    private ProductEntity buildProduct(String nombre, ProductCategory categoria, BigDecimal precio) {
        return ProductEntity.builder()
                .nombre(nombre + "-" + UUID.randomUUID())
                .descripcion("Descripción de " + nombre)
                .precio(precio)
                .stock(10)
                .categoria(categoria)
                .storeId(testStoreId)
                .activo(true)
                .disponible(true)
                .creadoEn(LocalDateTime.now())
                .build();
    }

    @Test
    @DisplayName("Búsqueda por categoría retorna solo esa categoría")
    void search_by_categoria() {
        String uuid = UUID.randomUUID().toString().substring(0, 8);
        productJpaRepository.save(
                buildProduct("Arroz con leche " + uuid, ProductCategory.POSTRE, BigDecimal.valueOf(5.00)));
        productJpaRepository.save(
                buildProduct("Jugo " + uuid, ProductCategory.BEBIDA, BigDecimal.valueOf(4.00)));

        Page<ProductEntity> result = productJpaRepository.search(
                null, ProductCategory.POSTRE, null, null, null, null,
                PageRequest.of(0, 10)
        );

        assertTrue(result.getTotalElements() >= 1);
        assertTrue(result.getContent().stream()
                .allMatch(p -> p.getCategoria() == ProductCategory.POSTRE));
    }

    @Test
    @DisplayName("Soft delete — producto no aparece en findByIdAndDeletedAtIsNull")
    void soft_delete_hides_product() {
        ProductEntity product = productJpaRepository.save(
                buildProduct("Borrar", ProductCategory.SNACK, BigDecimal.valueOf(3.00)));

        product.setDeletedAt(LocalDateTime.now());
        productJpaRepository.save(product);

        Optional<ProductEntity> found = productJpaRepository.findByIdAndDeletedAtIsNull(product.getId());
        assertTrue(found.isEmpty());
    }

    @Test
    @DisplayName("Búsqueda por rango de precio retorna productos dentro del rango")
    void search_by_precio_range() {
        String uuid = UUID.randomUUID().toString().substring(0, 8);
        productJpaRepository.save(buildProduct("Barato " + uuid, ProductCategory.SNACK, BigDecimal.valueOf(2.00)));
        productJpaRepository.save(buildProduct("Medio " + uuid, ProductCategory.SNACK, BigDecimal.valueOf(7.00)));
        productJpaRepository.save(buildProduct("Caro " + uuid, ProductCategory.COMIDA, BigDecimal.valueOf(15.00)));

        Page<ProductEntity> result = productJpaRepository.search(
                uuid, null, null,
                BigDecimal.valueOf(5.00), BigDecimal.valueOf(10.00),
                null, PageRequest.of(0, 10)
        );

        assertEquals(1, result.getTotalElements());
        assertEquals(BigDecimal.valueOf(7.00), result.getContent().get(0).getPrecio());
    }

    @Test
    @DisplayName("findAllByDeletedAtIsNull excluye productos eliminados")
    void find_all_excludes_deleted() {
        String uuid = UUID.randomUUID().toString().substring(0, 8);
        ProductEntity activo = productJpaRepository.save(
                buildProduct("Activo " + uuid, ProductCategory.COMIDA, BigDecimal.valueOf(10.00)));
        ProductEntity eliminado = productJpaRepository.save(
                buildProduct("Eliminado " + uuid, ProductCategory.COMIDA, BigDecimal.valueOf(10.00)));

        eliminado.setDeletedAt(LocalDateTime.now());
        productJpaRepository.save(eliminado);

        assertTrue(productJpaRepository.findAllByDeletedAtIsNull().stream()
                .anyMatch(p -> p.getId().equals(activo.getId())));
        assertFalse(productJpaRepository.findAllByDeletedAtIsNull().stream()
                .anyMatch(p -> p.getId().equals(eliminado.getId())));
    }
}
