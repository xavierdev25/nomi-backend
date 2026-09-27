package com.nomi.backend.domain.port.in.payment;

import com.nomi.backend.domain.model.payment.Payment;

/**
 * Creación del pago de un pedido. El monto se deriva del pedido en el servidor.
 */
public interface CreatePaymentUseCase {

    record CreatePaymentCommand(Long orderId, String requesterEmail) {}

    Payment execute(CreatePaymentCommand command);
}
