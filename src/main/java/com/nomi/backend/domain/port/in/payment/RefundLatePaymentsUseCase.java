package com.nomi.backend.domain.port.in.payment;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Devuelve los pagos que se aprueban cuando su pedido ya está cancelado.
 *
 * <p>Al cancelar se cierra el checkout, pero un pago que ya estaba en curso (por ejemplo, en
 * revisión) puede aprobarse después. El webhook lo detecta y lo reembolsa; esta revisión cubre los
 * avisos que no llegan, que es lo normal en el sandbox de MercadoPago.
 */
public interface RefundLatePaymentsUseCase {

    /** Pedidos cancelados desde {@code since} cuyo checkout no tiene un pago aprobado ni reembolsado. */
    List<Long> findCancelledOrdersToCheck(LocalDateTime since);

    /**
     * Revisa un pedido, en su propia transacción, y reembolsa su pago si se aprobó.
     *
     * @return {@code true} si reembolsó un pago
     * @throws com.nomi.backend.domain.exception.PaymentGatewayUnavailableException si no se pudo
     *         consultar o reembolsar; el pedido se revisa en la siguiente pasada
     */
    boolean refundIfPaid(Long orderId);
}
