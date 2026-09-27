package com.nomi.backend.domain.model.user;

import com.nomi.backend.domain.model.product.DietaryTag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("DietaryRestriction - Cumplimiento con etiquetas del producto")
class DietaryRestrictionTest {

    @Test
    void untagged_product_satisfies_no_restriction() {
        Set<DietaryTag> none = Set.of();
        assertFalse(DietaryRestriction.VEGETARIANO.isSatisfiedBy(none));
        assertFalse(DietaryRestriction.VEGANO.isSatisfiedBy(none));
        assertFalse(DietaryRestriction.SIN_GLUTEN.isSatisfiedBy(none));
        assertFalse(DietaryRestriction.SIN_LACTOSA.isSatisfiedBy(none));
        assertTrue(DietaryRestriction.NINGUNA.isSatisfiedBy(none));
    }

    @Test
    void vegan_product_is_also_vegetarian_and_lactose_free_but_not_gluten_free() {
        Set<DietaryTag> vegan = Set.of(DietaryTag.VEGANO);
        assertTrue(DietaryRestriction.VEGANO.isSatisfiedBy(vegan));
        assertTrue(DietaryRestriction.VEGETARIANO.isSatisfiedBy(vegan));
        assertTrue(DietaryRestriction.SIN_LACTOSA.isSatisfiedBy(vegan));
        assertFalse(DietaryRestriction.SIN_GLUTEN.isSatisfiedBy(vegan));
    }

    @Test
    void vegetarian_product_is_not_vegan() {
        assertFalse(DietaryRestriction.VEGANO.isSatisfiedBy(Set.of(DietaryTag.VEGETARIANO)));
    }

    @Test
    void parse_all_accepts_known_values_ignoring_case_and_drops_ninguna() {
        Optional<Set<DietaryRestriction>> parsed =
                DietaryRestriction.parseAll(List.of("vegetariano", " SIN_GLUTEN ", "NINGUNA", ""));
        assertEquals(Optional.of(Set.of(DietaryRestriction.VEGETARIANO, DietaryRestriction.SIN_GLUTEN)), parsed);
    }

    @Test
    void parse_all_with_an_unknown_value_is_empty_so_it_cannot_be_ignored() {
        assertTrue(DietaryRestriction.parseAll(List.of("VEGANO", "SIN_MARISCOS")).isEmpty());
    }

    @Test
    void parse_all_with_null_is_no_restrictions() {
        assertEquals(Optional.of(Set.of()), DietaryRestriction.parseAll(null));
    }
}
