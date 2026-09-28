package com.nomi.backend.domain.model.payment;

import java.util.Optional;
import java.util.UUID;

/**
 * Referencia externa de un checkout en la pasarela: {@code nomi-<pedido>-<uuid>}.
 *
 * <p>Con ella se encuentran los pagos de un checkout. No basta con el id del pedido: se repite si la
 * base de datos se reinicia o si otro entorno usa la misma cuenta de MercadoPago, y entonces un pago
 * antiguo de "otro pedido 12" daría por pagado el pedido 12 actual. Los checkouts creados antes de
 * esta regla usan solo el id del pedido.
 */
public final class CheckoutReference {

    private static final String PREFIX = "nomi-";

    private CheckoutReference() {
    }

    /** Referencia nueva y única para un checkout del pedido. */
    public static String newFor(Long orderId) {
        return PREFIX + orderId + "-" + UUID.randomUUID();
    }

    /** Pedido de una referencia, nueva o antigua; vacío si no es de Nomi. */
    public static Optional<Long> orderIdOf(String reference) {
        if (reference == null || reference.isBlank()) {
            return Optional.empty();
        }
        String candidate = reference.trim();
        if (candidate.startsWith(PREFIX)) {
            int end = candidate.indexOf('-', PREFIX.length());
            if (end < 0) {
                return Optional.empty();
            }
            candidate = candidate.substring(PREFIX.length(), end);
        }
        try {
            return Optional.of(Long.parseLong(candidate));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }
}
