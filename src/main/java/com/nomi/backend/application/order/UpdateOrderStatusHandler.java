package com.nomi.backend.application.order;

import com.nomi.backend.domain.exception.ResourceNotFoundException;
import com.nomi.backend.domain.model.order.Order;
import com.nomi.backend.domain.model.order.OrderStatus;
import com.nomi.backend.domain.port.in.order.UpdateOrderStatusUseCase;
import com.nomi.backend.domain.port.out.BusinessMetricsPort;
import com.nomi.backend.domain.port.out.OrderHistoryPort;
import com.nomi.backend.domain.port.out.OrderRepositoryPort;
import com.nomi.backend.domain.service.OrderDomainService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Avanza el estado de un pedido, registra el cambio en el historial y publica
 * {@link OrderStatusChangedEvent}.
 *
 * <p>Los dos cambios que salen de {@code PENDIENTE} no pasan por aquí: el pedido pasa a
 * preparación solo cuando MercadoPago aprueba el pago (webhook o conciliación), y se cancela con
 * {@code PATCH /orders/{id}/cancel}, que comprueba el pago y devuelve el stock. Si no, una tienda
 * podría preparar un pedido sin cobrar o cancelarlo sin devolver el stock.
 *
 * <p>Hoy ningún componente escucha ese evento, así que los cambios de estado no generan
 * notificaciones. Tampoco se verifica el código de confirmación al marcar {@code ENTREGADO}.
 */
@Service
@RequiredArgsConstructor
public class UpdateOrderStatusHandler implements UpdateOrderStatusUseCase {

    private final OrderRepositoryPort orderRepositoryPort;
    private final OrderHistoryPort orderHistoryPort;
    private final BusinessMetricsPort metricsPort;
    private final ApplicationEventPublisher eventPublisher;
    private final OrderDomainService orderDomainService;

    @Override
    @Transactional
    public Order execute(Long orderId, OrderStatus newStatus, Long changedBy) {
        Order existing = orderRepositoryPort.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Orden no encontrada"));

        if (newStatus == OrderStatus.CANCELADO) {
            throw new IllegalStateException("Para cancelar un pedido usa PATCH /orders/{id}/cancel");
        }
        if (existing.getStatus() == OrderStatus.PENDIENTE && newStatus == OrderStatus.PREPARANDO) {
            throw new IllegalStateException("Un pedido pasa a preparación cuando se aprueba su pago");
        }

        Order updatedOrderModel = orderDomainService.applyStatusTransition(existing, newStatus);
        Order updatedOrder = orderRepositoryPort.save(updatedOrderModel);

        orderHistoryPort.record(updatedOrder.getId(), newStatus, changedBy,
                "Estado actualizado a " + newStatus.enEspanol());

        if (newStatus == OrderStatus.ENTREGADO) {
            metricsPort.recordOrderCompleted();
        }

        eventPublisher.publishEvent(new OrderStatusChangedEvent(
                updatedOrder.getId(),
                updatedOrder.getUserId(),
                updatedOrder.getStoreId(),
                newStatus,
                updatedOrder,
                LocalDateTime.now()
        ));

        return updatedOrder;
    }

    /**
     * Evento de aplicación publicado tras cada cambio de estado.
     */
    public record OrderStatusChangedEvent(
            Long orderId,
            Long userId,
            Long storeId,
            OrderStatus newStatus,
            Order order,
            LocalDateTime occurredAt
    ) {}
}
