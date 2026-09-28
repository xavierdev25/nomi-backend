package com.nomi.backend.domain.exception;

/**
 * Se intentó cancelar un pedido cuyo pago ya está aprobado en la pasarela ({@code 409}).
 *
 * <p>Pasa cuando el estudiante paga y cancela antes de que llegue el webhook: el pedido aún figura
 * como {@code PENDIENTE}, pero ya está pagado. Quien la lanza ya confirmó el pedido (pasó a
 * {@code PREPARANDO}); por eso no debe revertirse la transacción al propagarla.
 */
public class OrderAlreadyPaidException extends IllegalStateException {

    public OrderAlreadyPaidException(String message) {
        super(message);
    }
}
