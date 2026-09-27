package com.foodv.backend.domain.port.in.payment;

import com.foodv.backend.domain.model.payment.Payment;

/**
 * Creación del pago de un pedido. El monto se deriva del pedido en el servidor.
 */
public interface CreatePaymentUseCase {

    record CreatePaymentCommand(Long orderId, String requesterEmail) {}

    Payment execute(CreatePaymentCommand command);
}
