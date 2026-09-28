package com.nomi.backend.domain.model.payment;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Referencia de un checkout: única por checkout y de la que se recupera el pedido.
 */
@DisplayName("CheckoutReference - Referencia externa del checkout")
class CheckoutReferenceTest {

    @Test
    @DisplayName("Dos checkouts del mismo pedido tienen referencias distintas")
    void referencias_unicas() {
        String first = CheckoutReference.newFor(12L);

        assertTrue(first.startsWith("nomi-12-"));
        assertNotEquals(first, CheckoutReference.newFor(12L));
    }

    @Test
    @DisplayName("Recupera el pedido de referencias nuevas y antiguas")
    void recupera_el_pedido() {
        assertEquals(Optional.of(12L), CheckoutReference.orderIdOf(CheckoutReference.newFor(12L)));
        assertEquals(Optional.of(12L), CheckoutReference.orderIdOf("12"));
        assertEquals(Optional.of(12L), CheckoutReference.orderIdOf(" 12 "));
    }

    @Test
    @DisplayName("Una referencia que no es de Nomi no da pedido")
    void referencia_ajena() {
        assertTrue(CheckoutReference.orderIdOf(null).isEmpty());
        assertTrue(CheckoutReference.orderIdOf("").isEmpty());
        assertTrue(CheckoutReference.orderIdOf("otra-app-99").isEmpty());
        assertTrue(CheckoutReference.orderIdOf("nomi-").isEmpty());
        assertTrue(CheckoutReference.orderIdOf("nomi-abc-123").isEmpty());
    }
}
