package com.nomi.backend.domain.model.payment;

/**
 * Estados de un pago. Solo el webhook de MercadoPago los cambia.
 */
public enum PaymentStatus {
    PENDIENTE,
    APROBADO,
    RECHAZADO,
    CANCELADO,
    REEMBOLSADO
}
