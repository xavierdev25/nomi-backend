package com.nomi.backend.domain.service;

import com.nomi.backend.domain.model.product.DietaryTag;
import com.nomi.backend.domain.model.product.Product;
import com.nomi.backend.domain.model.product.ProductCategory;
import com.nomi.backend.domain.model.user.DietaryRestriction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.stream.LongStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("RecommendationCandidates - Selección determinista de candidatos")
class RecommendationCandidatesTest {

    private static final Set<Long> ACTIVE_STORES = Set.of(1L);

    private static Product product(long id, Set<DietaryTag> tags) {
        return Product.builder()
                .id(id)
                .nombre("Producto " + id)
                .precio(BigDecimal.TEN)
                .stock(5)
                .categoria(ProductCategory.COMIDA)
                .storeId(1L)
                .activo(true)
                .disponible(true)
                .etiquetasDieteticas(tags)
                .build();
    }

    private static List<Long> ids(List<Product> products) {
        return products.stream().map(Product::getId).toList();
    }

    @Test
    void only_products_suitable_for_every_restriction_are_candidates() {
        List<Product> catalog = List.of(
                product(1, Set.of()),                                           // sin etiquetas: pollo
                product(2, Set.of(DietaryTag.VEGETARIANO)),
                product(3, Set.of(DietaryTag.VEGANO, DietaryTag.SIN_GLUTEN)));

        List<Product> vegetarian = RecommendationCandidates.select(catalog, ACTIVE_STORES,
                Set.of(DietaryRestriction.VEGETARIANO), Set.of(), List.of());
        List<Product> veganGlutenFree = RecommendationCandidates.select(catalog, ACTIVE_STORES,
                Set.of(DietaryRestriction.VEGANO, DietaryRestriction.SIN_GLUTEN), Set.of(), List.of());
        List<Product> unrestricted = RecommendationCandidates.select(catalog, ACTIVE_STORES,
                Set.of(), Set.of(), List.of());

        assertEquals(List.of(2L, 3L), ids(vegetarian));
        assertEquals(List.of(3L), ids(veganGlutenFree));
        assertEquals(List.of(1L, 2L, 3L), ids(unrestricted));
    }

    @Test
    void products_that_cannot_be_ordered_are_excluded() {
        Product outOfStock = Product.builder().id(10L).precio(BigDecimal.ONE).stock(0).categoria(ProductCategory.SNACK)
                .storeId(1L).activo(true).disponible(false).build();
        Product unpublished = Product.builder().id(11L).precio(BigDecimal.ONE).stock(3).categoria(ProductCategory.SNACK)
                .storeId(1L).activo(false).disponible(true).build();
        Product fromInactiveStore = Product.builder().id(12L).precio(BigDecimal.ONE).stock(3).categoria(ProductCategory.SNACK)
                .storeId(99L).activo(true).disponible(true).build();

        List<Product> candidates = RecommendationCandidates.select(
                List.of(outOfStock, unpublished, fromInactiveStore, product(13, Set.of())),
                ACTIVE_STORES, Set.of(), Set.of(), List.of());

        assertEquals(List.of(13L), ids(candidates));
    }

    @Test
    void disliked_products_are_excluded() {
        List<Product> candidates = RecommendationCandidates.select(
                List.of(product(1, Set.of()), product(2, Set.of())),
                ACTIVE_STORES, Set.of(), Set.of(1L), List.of());

        assertEquals(List.of(2L), ids(candidates));
    }

    @Test
    void most_ordered_products_come_first_and_survive_the_cap() {
        List<Product> catalog = LongStream.rangeClosed(1, 60).mapToObj(id -> product(id, Set.of())).toList();

        List<Product> candidates = RecommendationCandidates.select(catalog, ACTIVE_STORES,
                Set.of(), Set.of(), List.of(55L, 58L));

        assertEquals(RecommendationCandidates.MAX_CANDIDATES, candidates.size());
        assertEquals(List.of(55L, 58L, 1L, 2L), ids(candidates).subList(0, 4));
    }
}
