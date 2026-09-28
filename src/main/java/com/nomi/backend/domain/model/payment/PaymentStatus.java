package com.nomi.backend.domain.model.payment;

/**
 * Estados de un pago. Los cambia la pasarela: el webhook, la cancelación y la conciliación de
 * pedidos pendientes consultan su estado real en MercadoPago.
 */
public enum PaymentStatus {
    PENDIENTE,
    APROBADO,
    RECHAZADO,
    CANCELADO,
    REEMBOLSADO;

    /**
     * Si un aviso de la pasarela con {@code next} puede sustituir este estado.
     *
     * <p>Un pedido tiene un solo registro de pago, pero en el checkout de MercadoPago el
     * estudiante puede hacer varios intentos (uno rechazado y otro aprobado) y los avisos llegan
     * en cualquier orden. Un pago aprobado solo puede pasar a reembolsado, y uno reembolsado es
     * definitivo: así un rechazo tardío nunca borra una aprobación. Repetir el mismo estado se
     * permite (sirve para completar el id del pago).
     */
    public boolean canBeReplacedBy(PaymentStatus next) {
        return switch (this) {
            case APROBADO -> next == APROBADO || next == REEMBOLSADO;
            case REEMBOLSADO -> next == REEMBOLSADO;
            default -> true;
        };
    }
}
