package com.foodv.backend.domain.port.out;

import com.foodv.backend.domain.model.order.OrderStatus;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Historial de cambios de estado de los pedidos. Hoy solo lo alimenta
 * {@code UpdateOrderStatusHandler}.
 */
public interface OrderHistoryPort {

    record OrderHistoryEntry(
            Long id,
            Long orderId,
            OrderStatus status,
            Long changedBy,
            String notas,
            LocalDateTime creadoEn
    ) {}

    void record(Long orderId, OrderStatus status, Long changedBy, String notas);

    List<OrderHistoryEntry> findByOrderId(Long orderId);
}
