package com.nomi.backend.domain.model.product;

/**
 * Restricción alimentaria para la que un producto es apto, declarada por el comercio.
 *
 * <p>Es la única fuente fiable para saber si un plato cumple una restricción: el catálogo no
 * tiene ingredientes y el modelo de IA no conoce las recetas. Un producto sin etiquetas no es
 * apto para ninguna restricción.
 */
public enum DietaryTag {
    VEGETARIANO,
    VEGANO,
    SIN_GLUTEN,
    SIN_LACTOSA
}
