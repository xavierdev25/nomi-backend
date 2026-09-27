package com.foodv.backend.domain.port.in.order;

import com.foodv.backend.domain.model.order.Order;
import com.foodv.backend.domain.model.order.OrderStatus;

/**
 * Avance del estado de un pedido por la tienda, el repartidor o un administrador.
 */
public interface UpdateOrderStatusUseCase {
    Order execute(Long orderId, OrderStatus newStatus, Long changedBy);
}
