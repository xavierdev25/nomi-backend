package com.foodv.backend.domain.model.user;

import com.foodv.backend.domain.model.product.DietaryTag;

import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Restricción alimentaria de un estudiante. Los valores coinciden con los que acepta el servicio
 * de IA.
 */
public enum DietaryRestriction {
    VEGETARIANO,
    VEGANO,
    SIN_GLUTEN,
    SIN_LACTOSA,
    NINGUNA;

    /** Expresión regular para validar restricciones que llegan como texto. */
    public static final String PATTERN = "VEGETARIANO|VEGANO|SIN_GLUTEN|SIN_LACTOSA|NINGUNA";

    /**
     * Si un producto con estas etiquetas cumple la restricción. Un producto vegano es también
     * vegetariano y sin lactosa.
     */
    public boolean isSatisfiedBy(Set<DietaryTag> tags) {
        return switch (this) {
            case VEGETARIANO -> tags.contains(DietaryTag.VEGETARIANO) || tags.contains(DietaryTag.VEGANO);
            case VEGANO -> tags.contains(DietaryTag.VEGANO);
            case SIN_GLUTEN -> tags.contains(DietaryTag.SIN_GLUTEN);
            case SIN_LACTOSA -> tags.contains(DietaryTag.SIN_LACTOSA) || tags.contains(DietaryTag.VEGANO);
            case NINGUNA -> true;
        };
    }

    /**
     * Convierte las restricciones guardadas del usuario, sin {@code NINGUNA}.
     *
     * @return vacío si alguna no se reconoce. Una restricción desconocida no se puede comprobar,
     *     así que quien llama debe tratarla como imposible de cumplir en lugar de ignorarla.
     */
    public static Optional<Set<DietaryRestriction>> parseAll(Collection<String> values) {
        Set<DietaryRestriction> parsed = EnumSet.noneOf(DietaryRestriction.class);
        for (String value : values == null ? List.<String>of() : values) {
            if (value == null || value.isBlank()) {
                continue;
            }
            try {
                parsed.add(valueOf(value.trim().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException e) {
                return Optional.empty();
            }
        }
        parsed.remove(NINGUNA);
        return Optional.of(parsed);
    }
}
