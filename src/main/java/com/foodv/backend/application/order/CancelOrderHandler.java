package com.foodv.backend.application.order;

import com.foodv.backend.domain.model.notification.NotificationEvent;
import com.foodv.backend.domain.model.order.Order;
import com.foodv.backend.domain.model.order.OrderItem;
import com.foodv.backend.domain.model.order.OrderStatus;
import com.foodv.backend.domain.port.in.order.CancelOrderUseCase;
import com.foodv.backend.domain.port.out.BusinessMetricsPort;
import com.foodv.backend.domain.port.out.NotificationPort;
import com.foodv.backend.domain.port.out.OrderRepositoryPort;
import com.foodv.backend.domain.port.out.ProductRepositoryPort;
import com.foodv.backend.domain.port.out.notification.PushNotificationPort;
import com.foodv.backend.domain.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Cancela un pedido si su estado lo permite (solo {@code PENDIENTE}), devuelve el stock y
 * notifica al estudiante y a la tienda.
 *
 * <p>No consulta el estado del pago: si MercadoPago ya aprobó el pago pero el webhook aún no
 * llegó, el pedido se cancela sin reembolso (ver la auditoría técnica, A3).
 */
@Service
@RequiredArgsConstructor
public class CancelOrderHandler implements CancelOrderUseCase {

    private final OrderRepositoryPort orderRepositoryPort;
    private final ProductRepositoryPort productRepositoryPort;
    private final NotificationPort notificationPort;
    private final PushNotificationPort pushNotificationPort;
    private final BusinessMetricsPort metricsPort;

    @Override
    @Transactional
    public Order execute(CancelOrderCommand command) {
        Order existing = orderRepositoryPort.findByIdWithItems(command.orderId())
                .orElseThrow(() -> new ResourceNotFoundException("Orden no encontrada"));

        if (!existing.getStatus().canTransitionTo(OrderStatus.CANCELADO)) {
            throw new IllegalArgumentException(
                    "La orden no puede cancelarse en estado: " + existing.getStatus().enEspanol());
        }

        if (existing.getItems() != null) {
            for (OrderItem item : existing.getItems()) {
                productRepositoryPort.findById(item.getProductId()).ifPresent(product -> {
                    int restored = -item.getCantidad();
                    productRepositoryPort.decrementStock(item.getProductId(), restored);
                });
            }
        }

        Order order = Order.builder()
                .id(existing.getId())
                .userId(existing.getUserId())
                .storeId(existing.getStoreId())
                .aulaId(existing.getAulaId())
                .repartidorId(existing.getRepartidorId())
                .items(existing.getItems())
                .total(existing.getTotal())
                .propina(existing.getPropina())
                .tarifaServicio(existing.getTarifaServicio())
                .comisionFoodv(existing.getComisionFoodv())
                .status(OrderStatus.CANCELADO)
                .notas(existing.getNotas())
                .motivoCancelacion(command.motivoCancelacion())
                .canceladoPor(command.canceladoPor())
                .codigoConfirmacion(existing.getCodigoConfirmacion())
                .fotoEntregaUrl(existing.getFotoEntregaUrl())
                .creadoEn(existing.getCreadoEn())
                .actualizadoEn(LocalDateTime.now())
                .build();

        Order cancelled = orderRepositoryPort.save(order);
        metricsPort.recordOrderCancelled();

        notifySafe(cancelled, command.motivoCancelacion());

        return cancelled;
    }

    private void notifySafe(Order cancelled, String motivo) {
        try {
            NotificationEvent event = NotificationEvent.builder()
                    .type("ORDER_CANCELLED")
                    .orderId(cancelled.getId())
                    .userId(cancelled.getUserId())
                    .storeId(cancelled.getStoreId())
                    .message("Tu orden ha sido cancelada. Motivo: " +
                            (motivo != null ? motivo : "No especificado"))
                    .payload(cancelled)
                    .timestamp(LocalDateTime.now())
                    .build();

            notificationPort.notifyUser(cancelled.getUserId(), event);
            notificationPort.notifyStore(cancelled.getStoreId(), event);
            notificationPort.notifyOrderUpdate(cancelled.getId(), event);
            pushNotificationPort.sendToUser(
                    String.valueOf(cancelled.getUserId()),
                    "Orden cancelada",
                    "Tu pedido #" + cancelled.getId() + " fue cancelado"
            );
        } catch (Exception ignore) {
            // Una notificación fallida nunca debe revertir la cancelación.
        }
    }
}
