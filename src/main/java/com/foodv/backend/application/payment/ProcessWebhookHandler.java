package com.foodv.backend.application.payment;

import com.foodv.backend.domain.model.notification.NotificationEvent;
import com.foodv.backend.domain.model.order.Order;
import com.foodv.backend.domain.model.payment.Payment;
import com.foodv.backend.domain.model.payment.PaymentStatus;
import com.foodv.backend.domain.port.in.payment.ProcessWebhookUseCase;
import com.foodv.backend.domain.port.out.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import com.foodv.backend.domain.model.order.OrderStatus;
import com.foodv.backend.domain.port.out.notification.PushNotificationPort;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Aplica una notificación de MercadoPago: actualiza el pago y, según su estado, pasa el pedido
 * a {@code PREPARANDO} (aprobado) o lo cancela devolviendo el stock (rechazado).
 *
 * <p>El pago se busca por id externo y, si no aparece, por la referencia externa (id del
 * pedido) consultada a MercadoPago.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProcessWebhookHandler implements ProcessWebhookUseCase {

    private final PaymentRepositoryPort paymentRepositoryPort;
    private final PaymentGatewayPort paymentGatewayPort;
    private final OrderRepositoryPort orderRepositoryPort;
    private final NotificationPort notificationPort;
    private final PushNotificationPort pushNotificationPort;
    private final ProductRepositoryPort productRepositoryPort;

    @Override
    @Transactional
    public void execute(WebhookEvent event) {
        Optional<Payment> optionalPayment = paymentRepositoryPort.findByExternalId(event.externalId());

        if (optionalPayment.isEmpty()) {
            String orderIdStr = paymentGatewayPort.getExternalReference(event.externalId());
            if (orderIdStr == null) return;
            try {
                Long orderId = Long.parseLong(orderIdStr);
                optionalPayment = paymentRepositoryPort.findByOrderId(orderId);
            } catch (NumberFormatException e) {
                return;
            }
            if (optionalPayment.isEmpty()) return;
        }

        Payment existing = optionalPayment.get();
        PaymentStatus newStatus = paymentGatewayPort.getPaymentStatus(event.externalId());

        Payment updatedPayment = Payment.builder()
                .id(existing.getId())
                .orderId(existing.getOrderId())
                .userId(existing.getUserId())
                .amount(existing.getAmount())
                .status(newStatus)
                .externalId(existing.getExternalId())
                .paymentUrl(existing.getPaymentUrl())
                .failureReason(existing.getFailureReason())
                .creadoEn(existing.getCreadoEn())
                .actualizadoEn(LocalDateTime.now())
                .build();

        paymentRepositoryPort.save(updatedPayment);

        if (newStatus == PaymentStatus.APROBADO) {
            Optional<Order> optionalOrder = orderRepositoryPort.findById(existing.getOrderId());
            optionalOrder.ifPresent(order -> {
                if (order.getStatus() != OrderStatus.PENDIENTE) {
                    log.warn("Orden {} en estado {}, no se aplica PREPARANDO por webhook",
                            order.getId(), order.getStatus());
                    return;
                }
                Order updatedOrder = Order.builder()
                        .id(order.getId())
                        .userId(order.getUserId())
                        .storeId(order.getStoreId())
                        .aulaId(order.getAulaId())
                        .repartidorId(order.getRepartidorId())
                        .items(order.getItems())
                        .total(order.getTotal())
                        .propina(order.getPropina())
                        .tarifaServicio(order.getTarifaServicio())
                        .comisionFoodv(order.getComisionFoodv())
                        .status(OrderStatus.PREPARANDO)
                        .notas(order.getNotas())
                        .motivoCancelacion(order.getMotivoCancelacion())
                        .canceladoPor(order.getCanceladoPor())
                        .codigoConfirmacion(order.getCodigoConfirmacion())
                        .fotoEntregaUrl(order.getFotoEntregaUrl())
                        .creadoEn(order.getCreadoEn())
                        .actualizadoEn(LocalDateTime.now())
                        .build();
                orderRepositoryPort.save(updatedOrder);

                NotificationEvent notificationEvent = NotificationEvent.builder()
                        .type("PAYMENT_APPROVED")
                        .orderId(order.getId())
                        .userId(order.getUserId())
                        .storeId(order.getStoreId())
                        .message("Tu pago ha sido aprobado")
                        .payload(updatedPayment)
                        .timestamp(LocalDateTime.now())
                        .build();
                notificationPort.notifyUser(order.getUserId(), notificationEvent);

                pushNotificationPort.sendToUser(
                        String.valueOf(order.getUserId()),
                        "¡Pedido confirmado! 🎉",
                        "Tu pedido #" + order.getId() + " está siendo preparado. Llegará en 15-25 min."
                );
            });
        }

        // Ojo (auditoría técnica, C1): este bloque no depende del estado del pago y cancela cualquier
        // pedido que siga en PENDIENTE, también con pagos pendientes o en revisión.
        Optional<Order> optionalOrderRejected = orderRepositoryPort.findById(existing.getOrderId());
        optionalOrderRejected.ifPresent(order -> {
            if (order.getStatus() == OrderStatus.PENDIENTE) {
                order.getItems().forEach(item ->
                        productRepositoryPort.incrementStock(item.getProductId(), item.getCantidad())
                );
                Order cancelledOrder = Order.builder()
                        .id(order.getId())
                        .userId(order.getUserId())
                        .storeId(order.getStoreId())
                        .aulaId(order.getAulaId())
                        .repartidorId(order.getRepartidorId())
                        .items(order.getItems())
                        .total(order.getTotal())
                        .propina(order.getPropina())
                        .tarifaServicio(order.getTarifaServicio())
                        .comisionFoodv(order.getComisionFoodv())
                        .status(OrderStatus.CANCELADO)
                        .notas(order.getNotas())
                        .motivoCancelacion("Pago rechazado automáticamente")
                        .canceladoPor(order.getUserId())
                        .codigoConfirmacion(order.getCodigoConfirmacion())
                        .fotoEntregaUrl(order.getFotoEntregaUrl())
                        .creadoEn(order.getCreadoEn())
                        .actualizadoEn(LocalDateTime.now())
                        .build();
                orderRepositoryPort.save(cancelledOrder);
                log.info("Orden {} cancelada y stock restaurado por pago rechazado", order.getId());
            }
        });

        if (newStatus == PaymentStatus.RECHAZADO) {
            Optional<Order> orderToCancel = orderRepositoryPort.findById(existing.getOrderId());
            orderToCancel.ifPresent(order -> {
                if (order.getStatus() == OrderStatus.PENDIENTE) {
                    order.getItems().forEach(item ->
                            productRepositoryPort.incrementStock(item.getProductId(), item.getCantidad())
                    );
                    Order cancelledOrder = Order.builder()
                            .id(order.getId())
                            .userId(order.getUserId())
                            .storeId(order.getStoreId())
                            .aulaId(order.getAulaId())
                            .repartidorId(order.getRepartidorId())
                            .items(order.getItems())
                            .total(order.getTotal())
                            .propina(order.getPropina())
                            .tarifaServicio(order.getTarifaServicio())
                            .comisionFoodv(order.getComisionFoodv())
                            .status(OrderStatus.CANCELADO)
                            .notas(order.getNotas())
                            .motivoCancelacion("Pago rechazado automáticamente")
                            .canceladoPor(order.getUserId())
                            .codigoConfirmacion(order.getCodigoConfirmacion())
                            .fotoEntregaUrl(order.getFotoEntregaUrl())
                            .creadoEn(order.getCreadoEn())
                            .actualizadoEn(LocalDateTime.now())
                            .build();
                    orderRepositoryPort.save(cancelledOrder);
                    log.info("Orden {} cancelada y stock restaurado por pago rechazado", order.getId());
                }
            });

            NotificationEvent notificationEvent = NotificationEvent.builder()
                    .type("PAYMENT_REJECTED")
                    .orderId(existing.getOrderId())
                    .userId(existing.getUserId())
                    .message("Tu pago ha sido rechazado")
                    .payload(updatedPayment)
                    .timestamp(LocalDateTime.now())
                    .build();
            notificationPort.notifyUser(existing.getUserId(), notificationEvent);
            pushNotificationPort.sendToUser(
                    String.valueOf(existing.getUserId()),
                    "Pago rechazado ❌",
                    "Tu pago del pedido #" + existing.getOrderId() + " no pudo procesarse. Inténtalo nuevamente."
            );
        }
    }
}
