package com.nomi.backend.domain.port.in.order;

import com.nomi.backend.domain.model.order.Order;

/**
 * Cancelación de un pedido; devuelve el stock reservado.
 */
public interface CancelOrderUseCase {
    record CancelOrderCommand(Long orderId, Long canceladoPor, String motivoCancelacion) {}
    Order execute(CancelOrderCommand command);
}
