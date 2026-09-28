package com.nomi.backend.application.payment;

import com.nomi.backend.domain.exception.PaymentGatewayUnavailableException;
import com.nomi.backend.domain.exception.ResourceNotFoundException;
import com.nomi.backend.domain.model.notification.NotificationEvent;
import com.nomi.backend.domain.model.order.Order;
import com.nomi.backend.domain.model.order.OrderStatus;
import com.nomi.backend.domain.model.payment.Payment;
import com.nomi.backend.domain.model.payment.PaymentStatus;
import com.nomi.backend.domain.port.out.BusinessMetricsPort;
import com.nomi.backend.domain.port.out.NotificationPort;
import com.nomi.backend.domain.port.out.OrderHistoryPort;
import com.nomi.backend.domain.port.out.OrderRepositoryPort;
import com.nomi.backend.domain.port.out.PaymentGatewayPort;
import com.nomi.backend.domain.port.out.PaymentGatewayPort.GatewayPayment;
import com.nomi.backend.domain.port.out.PaymentRepositoryPort;
import com.nomi.backend.domain.port.out.notification.PushNotificationPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Optional;

/**
 * Aplica a un pedido lo que dice la pasarela sobre sus pagos. La usan el webhook, la cancelación y
 * la conciliación de pedidos pendientes, para que los tres decidan igual.
 *
 * <p>Reglas:
 * <ul>
 *   <li>Solo cuenta un pago de este checkout: misma referencia y mismo monto, y en los checkouts
 *       antiguos (referencia = id del pedido) no anterior al checkout. Un pago ajeno con la misma
 *       referencia (otra base de datos, otro entorno) no confirma el pedido ni se reembolsa.</li>
 *   <li>Solo un pago aprobado cambia el pedido: pasa a {@code PREPARANDO} con una actualización
 *       condicional, así que si el webhook, la cancelación y la caducidad coinciden, solo uno gana.</li>
 *   <li>Un pago aprobado para un pedido ya cancelado (caducó o el estudiante lo canceló antes de que
 *       se confirmara) se reembolsa: nunca se cobra un pedido que no se va a entregar.</li>
 *   <li>Un segundo pago aprobado para el mismo pedido es un cobro duplicado y se reembolsa.</li>
 *   <li>Un rechazo no cancela el pedido: en el checkout se puede reintentar con otro medio. Si nunca
 *       se paga, el pedido caduca.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderPaymentSettlement {

    /** Qué pasó al aplicar un pago aprobado. */
    public enum Outcome {
        /** El pedido estaba pendiente y pasó a preparación. */
        CONFIRMED,
        /** El pedido ya estaba confirmado con este mismo pago. */
        ALREADY_CONFIRMED,
        /** El pedido estaba cancelado y el pago se reembolsó. */
        REFUNDED,
        /** El pedido ya tenía otro pago aprobado y este, duplicado, se reembolsó. */
        DUPLICATE_REFUNDED,
        /** No hay registro de pago para el pedido: nada que hacer. */
        NO_PAYMENT_RECORD,
        /** El pago no es de este checkout (referencia, monto o fecha): se ignora. */
        NOT_THIS_CHECKOUT
    }

    /** Margen entre el reloj del servidor y el de la pasarela al comparar fechas. */
    static final Duration CLOCK_SKEW = Duration.ofMinutes(5);

    private final OrderRepositoryPort orderRepositoryPort;
    private final PaymentRepositoryPort paymentRepositoryPort;
    private final PaymentGatewayPort paymentGatewayPort;
    private final OrderHistoryPort orderHistoryPort;
    private final BusinessMetricsPort metricsPort;
    private final NotificationPort notificationPort;
    private final PushNotificationPort pushNotificationPort;

    /**
     * Pago aprobado del checkout de un pedido según la pasarela, si hay alguno.
     *
     * @throws PaymentGatewayUnavailableException si no se pudo consultar
     */
    public Optional<GatewayPayment> findApprovedPayment(Long orderId) {
        Optional<Payment> record = paymentRepositoryPort.findByOrderId(orderId);
        if (record.isEmpty()) {
            return Optional.empty();
        }
        return paymentGatewayPort.findPaymentsByReference(record.get().gatewayReference()).stream()
                .filter(payment -> payment.status() == PaymentStatus.APROBADO)
                .filter(payment -> belongsToCheckout(record.get(), payment))
                .findFirst();
    }

    /**
     * Si un pago de la pasarela es de este checkout. La referencia de los checkouts nuevos es única;
     * la de los antiguos es el id del pedido, que se repite entre bases de datos y entornos, así
     * que además el pago no puede ser anterior al checkout. En ambos casos el monto debe coincidir:
     * se cobra exactamente lo que calculó el servidor.
     */
    static boolean belongsToCheckout(Payment record, GatewayPayment payment) {
        if (!record.gatewayReference().equals(payment.externalReference())) {
            return false;
        }
        if (record.getExternalReference() == null && payment.createdAt() != null && record.getCreadoEn() != null) {
            OffsetDateTime checkoutCreatedAt = record.getCreadoEn().atZone(ZoneId.systemDefault()).toOffsetDateTime();
            if (payment.createdAt().isBefore(checkoutCreatedAt.minus(CLOCK_SKEW))) {
                return false;
            }
        }
        return payment.amount() != null && record.getAmount() != null
                && payment.amount().compareTo(record.getAmount()) == 0;
    }

    /**
     * Aplica un pago aprobado a su pedido.
     *
     * @throws PaymentGatewayUnavailableException si hacía falta reembolsar y la pasarela falló;
     *                                            quien llama debe reintentar (el webhook responde
     *                                            error para que MercadoPago lo reenvíe)
     */
    @Transactional
    public Outcome settleApproved(Long orderId, GatewayPayment approved) {
        LocalDateTime now = LocalDateTime.now();
        Optional<Payment> record = paymentRepositoryPort.findByOrderId(orderId);
        if (record.isEmpty()) {
            log.warn("Pago aprobado {} para el pedido {} sin registro de pago; se ignora", approved.id(), orderId);
            return Outcome.NO_PAYMENT_RECORD;
        }
        Payment payment = record.get();
        if (!belongsToCheckout(payment, approved)) {
            log.warn("Pago {} ({} {}) no es del checkout del pedido {}; se ignora",
                    approved.id(), approved.externalReference(), approved.amount(), orderId);
            return Outcome.NOT_THIS_CHECKOUT;
        }

        if (payment.isSettled()) {
            if (payment.getGatewayPaymentId() == null) {
                // Registro anterior a guardar el id del pago: este aviso es el del pago que ya lo
                // aprobó (se reenvió). Se completa el id; tratarlo como duplicado reembolsaría el bueno.
                paymentRepositoryPort.save(payment.withGatewayStatus(approved.id(), payment.getStatus(), null, now));
                return payment.getStatus() == PaymentStatus.REEMBOLSADO ? Outcome.REFUNDED : Outcome.ALREADY_CONFIRMED;
            }
            if (approved.id().equals(payment.getGatewayPaymentId())) {
                return payment.getStatus() == PaymentStatus.REEMBOLSADO ? Outcome.REFUNDED : Outcome.ALREADY_CONFIRMED;
            }
            paymentGatewayPort.refund(approved.id());
            log.warn("Pago duplicado {} del pedido {} reembolsado (el pedido ya tenía el pago {})",
                    approved.id(), orderId, payment.getGatewayPaymentId());
            notifyUser(orderId, payment.getUserId(), "PAYMENT_REFUNDED",
                    "Pago duplicado reembolsado",
                    "Detectamos un segundo pago del pedido #" + orderId + " y te lo devolvimos.");
            return Outcome.DUPLICATE_REFUNDED;
        }

        if (orderRepositoryPort.markPaidIfPending(orderId, now)) {
            paymentRepositoryPort.save(payment.withGatewayStatus(approved.id(), PaymentStatus.APROBADO, null, now));
            orderHistoryPort.record(orderId, OrderStatus.PREPARANDO, null, "Pago aprobado en MercadoPago");
            metricsPort.recordPaymentCompleted();
            notifyPaid(orderId, payment.getUserId());
            return Outcome.CONFIRMED;
        }

        Order order = orderRepositoryPort.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Orden no encontrada"));
        if (order.getStatus() == OrderStatus.CANCELADO) {
            paymentGatewayPort.refund(approved.id());
            paymentRepositoryPort.save(payment.withGatewayStatus(approved.id(), PaymentStatus.REEMBOLSADO,
                    "Reembolsado: el pedido ya estaba cancelado cuando se aprobó el pago", now));
            log.warn("Pago {} del pedido cancelado {} reembolsado", approved.id(), orderId);
            notifyUser(orderId, payment.getUserId(), "PAYMENT_REFUNDED",
                    "Te devolvimos tu pago",
                    "El pedido #" + orderId + " ya estaba cancelado cuando se aprobó tu pago: te lo devolvimos.");
            return Outcome.REFUNDED;
        }

        // El pedido ya había avanzado sin que el registro reflejara el pago (datos anteriores a esta regla).
        paymentRepositoryPort.save(payment.withGatewayStatus(approved.id(), PaymentStatus.APROBADO, null, now));
        return Outcome.ALREADY_CONFIRMED;
    }

    /**
     * Guarda el estado de un intento no aprobado (pendiente, rechazado, cancelado, reembolsado) y
     * avisa al estudiante de un rechazo. No cambia el pedido.
     */
    @Transactional
    public void recordNotApproved(Long orderId, GatewayPayment attempt) {
        paymentRepositoryPort.findByOrderId(orderId).ifPresent(payment -> {
            if (!payment.gatewayReference().equals(attempt.externalReference())) {
                return;
            }
            Payment updated = payment.withGatewayStatus(attempt.id(), attempt.status(), attempt.statusDetail(),
                    LocalDateTime.now());
            if (updated == payment) {
                return;
            }
            paymentRepositoryPort.save(updated);
            if (attempt.status() == PaymentStatus.RECHAZADO) {
                metricsPort.recordPaymentFailed();
                notifyUser(orderId, payment.getUserId(), "PAYMENT_REJECTED",
                        "Pago rechazado",
                        "Tu pago del pedido #" + orderId + " no se aprobó. Puedes intentarlo de nuevo "
                                + "antes de que venza el plazo.");
            }
        });
    }

    private void notifyPaid(Long orderId, Long userId) {
        notifyUser(orderId, userId, "PAYMENT_APPROVED",
                "¡Pedido confirmado!",
                "Tu pedido #" + orderId + " está siendo preparado.");
    }

    /** Las notificaciones son accesorias: un fallo al enviarlas nunca revierte un cambio de dinero. */
    private void notifyUser(Long orderId, Long userId, String type, String title, String message) {
        try {
            notificationPort.notifyUser(userId, NotificationEvent.builder()
                    .type(type)
                    .orderId(orderId)
                    .userId(userId)
                    .message(message)
                    .timestamp(LocalDateTime.now())
                    .build());
            pushNotificationPort.sendToUser(String.valueOf(userId), title, message);
        } catch (Exception e) {
            log.warn("No se pudo notificar {} del pedido {}: {}", type, orderId, e.getMessage());
        }
    }
}
