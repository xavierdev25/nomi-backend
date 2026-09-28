package com.nomi.backend.domain.model.order;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collection;

/**
 * Reglas de pago de los pedidos.
 *
 * <p>El stock se reserva al crear el pedido, antes de pagar. Sin un plazo, un estudiante que solo
 * miraba (o que quiere acaparar) retendría stock para siempre. Por eso un pedido sin pago aprobado
 * caduca tras {@code paymentWindow}, y cada estudiante puede tener como mucho
 * {@code maxPendingPerUser} pedidos sin pagar a la vez.
 *
 * @param paymentWindow     tiempo para pagar desde que se crea el pedido
 * @param maxPendingPerUser pedidos sin pagar (y sin caducar) que puede tener un estudiante
 */
public record OrderPaymentPolicy(Duration paymentWindow, int maxPendingPerUser) {

    public OrderPaymentPolicy {
        if (paymentWindow == null || paymentWindow.isNegative() || paymentWindow.isZero()) {
            throw new IllegalArgumentException("El plazo de pago debe ser positivo");
        }
        if (maxPendingPerUser < 1) {
            throw new IllegalArgumentException("Debe permitirse al menos un pedido pendiente");
        }
    }

    /** Hora límite de pago de un pedido creado en {@code createdAt}. */
    public LocalDateTime deadlineFrom(LocalDateTime createdAt) {
        return createdAt.plus(paymentWindow);
    }

    /** Si, con estos pedidos del estudiante, puede crear uno más. Los caducados no cuentan. */
    public boolean allowsAnotherPending(Collection<Order> pendingOrders, LocalDateTime now) {
        long active = pendingOrders.stream()
                .filter(order -> order.getStatus() == OrderStatus.PENDIENTE)
                .filter(order -> !order.isPaymentExpired(now))
                .count();
        return active < maxPendingPerUser;
    }
}
