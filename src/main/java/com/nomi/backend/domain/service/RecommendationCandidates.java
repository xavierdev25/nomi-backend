package com.nomi.backend.domain.service;

import com.nomi.backend.domain.model.product.Product;
import com.nomi.backend.domain.model.user.DietaryRestriction;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * Selección determinista de los productos que se pueden recomendar a un estudiante.
 *
 * <p>El modelo de IA solo ordena y explica esta lista: nunca ve un producto que el estudiante no
 * puede pedir (sin stock, no publicado o de una tienda inactiva), que no cumple sus restricciones
 * alimentarias o que marcó como "no me gusta". Las restricciones se comprueban aquí con las
 * etiquetas del comercio porque el modelo no conoce las recetas.
 */
public final class RecommendationCandidates {

    /**
     * Tope de candidatos por petición. Cuarenta líneas de producto caben en el contexto de un
     * modelo pequeño ({@code phi3}, ~4 000 tokens) con margen para la respuesta, y quedan muy por
     * debajo del máximo de 200 del servicio de IA.
     */
    public static final int MAX_CANDIDATES = 40;

    private RecommendationCandidates() {
    }

    /**
     * @param catalog             productos del sistema
     * @param activeStoreIds      tiendas activas
     * @param restrictions        restricciones del estudiante, ya validadas
     * @param dislikedProductIds  productos que el estudiante marcó como "no me gusta"
     * @param preferredProductIds productos que más pide, de más a menos; van primero para que el
     *                            tope no los deje fuera
     * @return como mucho {@link #MAX_CANDIDATES} productos: primero los preferidos, después por id
     */
    public static List<Product> select(List<Product> catalog,
                                       Set<Long> activeStoreIds,
                                       Set<DietaryRestriction> restrictions,
                                       Set<Long> dislikedProductIds,
                                       List<Long> preferredProductIds) {
        Comparator<Product> preferredFirst = Comparator.comparingInt(product -> {
            int index = preferredProductIds.indexOf(product.getId());
            return index < 0 ? Integer.MAX_VALUE : index;
        });
        return catalog.stream()
                .filter(Product::isOrderable)
                .filter(product -> activeStoreIds.contains(product.getStoreId()))
                .filter(product -> product.isSuitableFor(restrictions))
                .filter(product -> !dislikedProductIds.contains(product.getId()))
                .sorted(preferredFirst.thenComparing(Product::getId))
                .limit(MAX_CANDIDATES)
                .toList();
    }
}
