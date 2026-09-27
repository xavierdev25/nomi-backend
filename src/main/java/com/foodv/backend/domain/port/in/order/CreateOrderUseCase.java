package com.foodv.backend.domain.port.in.order;

import com.foodv.backend.domain.model.order.Order;

import java.math.BigDecimal;
import java.util.List;

/**
 * Creación de un pedido: valida tienda, aula, productos y stock, reserva el stock y calcula
 * los importes.
 */
public interface CreateOrderUseCase {

    record OrderItemCommand(Long productId, Integer cantidad) {}

    record CreateOrderCommand(Long userId, Long storeId, Long aulaId, List<OrderItemCommand> items, String notas, BigDecimal propina) {}

    Order execute(CreateOrderCommand command);
}
