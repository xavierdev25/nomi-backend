package com.nomi.backend.application.order;

import com.nomi.backend.domain.model.notification.NotificationEvent;
import com.nomi.backend.domain.model.order.Order;
import com.nomi.backend.domain.model.order.OrderItem;
import com.nomi.backend.domain.model.order.OrderStatus;
import com.nomi.backend.domain.model.payment.Payment;
import com.nomi.backend.domain.port.out.BusinessMetricsPort;
import com.nomi.backend.domain.port.out.NotificationPort;
import com.nomi.backend.domain.port.out.OrderHistoryPort;
import com.nomi.backend.domain.port.out.OrderRepositoryPort;
import com.nomi.backend.domain.port.out.PaymentGatewayPort;
import com.nomi.backend.domain.port.out.PaymentRepositoryPort;
import com.nomi.backend.domain.port.out.ProductRepositoryPort;
import com.nomi.backend.domain.port.out.notification.PushNotificationPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Cancela un pedido pendiente y devuelve su stock. Lo usan la cancelación del estudiante y la
 * caducidad de pedidos sin pagar.
 *
 * <p>La cancelación es condicional ({@code ... WHERE status = 'PENDIENTE'}): si el webhook
 * confirmó el pago un instante antes, no se cancela y el stock no se devuelve dos veces. Debe
 * llamarse dentro de una transacción.
 *
 * <p>Si el pedido tenía un checkout abierto, lo cierra en MercadoPago para que nadie pague un
 * pedido cancelado. Un pago que ya estaba en curso puede aprobarse igual: lo devuelve
 * {@code RefundLatePaymentsHandler}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class PendingOrderCanceller {

    private final OrderRepositoryPort orderRepositoryPort;
    private final ProductRepositoryPort productRepositoryPort;
    private final OrderHistoryPort orderHistoryPort;
    private final BusinessMetricsPort metricsPort;
    private final NotificationPort notificationPort;
    private final PushNotificationPort pushNotificationPort;
    private final PaymentRepositoryPort paymentRepositoryPort;
    private final PaymentGatewayPort paymentGatewayPort;

    /**
     * @param canceladoPor usuario que cancela, o {@code null} si lo cancela el sistema
     * @return {@code false} si el pedido ya no estaba pendiente y no se tocó
     */
    boolean cancel(Order order, String motivo, Long canceladoPor) {
        LocalDateTime now = LocalDateTime.now();
        if (!orderRepositoryPort.cancelIfPending(order.getId(), motivo, canceladoPor, now)) {
            return false;
        }
        if (order.getItems() != null) {
            for (OrderItem item : order.getItems()) {
                productRepositoryPort.incrementStock(item.getProductId(), item.getCantidad());
            }
        }
        orderHistoryPort.record(order.getId(), OrderStatus.CANCELADO, canceladoPor, motivo);
        metricsPort.recordOrderCancelled();
        notifySafe(order, motivo);
        closeCheckoutAfterCommit(order, now);
        return true;
    }

    /**
     * Se cierra tras el commit: si la cancelación se revirtiera, el checkout debe seguir abierto. Es
     * accesorio: si MercadoPago falla, el pedido queda cancelado igual y lo que se pague después se
     * reembolsa. Un checkout que ya venció (el pedido caducó) no se toca.
     */
    private void closeCheckoutAfterCommit(Order order, LocalDateTime now) {
        if (order.getPagoExpiraEn() != null && !now.isBefore(order.getPagoExpiraEn())) {
            return;
        }
        Optional<Payment> record = paymentRepositoryPort.findByOrderId(order.getId());
        if (record.isEmpty() || record.get().getExternalId() == null) {
            return;
        }
        String checkoutId = record.get().getExternalId();
        Runnable close = () -> {
            try {
                paymentGatewayPort.closeCheckout(checkoutId);
            } catch (Exception e) {
                log.warn("No se pudo cerrar el checkout del pedido cancelado {}: {}", order.getId(), e.getMessage());
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    close.run();
                }
            });
        } else {
            close.run();
        }
    }

    /** Una notificación fallida nunca debe revertir la cancelación. */
    private void notifySafe(Order order, String motivo) {
        try {
            String message = "Tu pedido #" + order.getId() + " fue cancelado. Motivo: "
                    + (motivo != null ? motivo : "No especificado");
            NotificationEvent event = NotificationEvent.builder()
                    .type("ORDER_CANCELLED")
                    .orderId(order.getId())
                    .userId(order.getUserId())
                    .storeId(order.getStoreId())
                    .message(message)
                    .timestamp(LocalDateTime.now())
                    .build();
            notificationPort.notifyUser(order.getUserId(), event);
            notificationPort.notifyStore(order.getStoreId(), event);
            notificationPort.notifyOrderUpdate(order.getId(), event);
            pushNotificationPort.sendToUser(String.valueOf(order.getUserId()), "Pedido cancelado", message);
        } catch (Exception e) {
            log.warn("No se pudo notificar la cancelación del pedido {}: {}", order.getId(), e.getMessage());
        }
    }
}
