package com.nomi.backend.domain.port.in.order;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Concilia los pedidos pendientes con la pasarela de pagos: confirma los que ya están pagados
 * (aunque el webhook no haya llegado) y cancela los que vencieron sin pago, devolviendo su stock.
 */
public interface ReconcilePendingOrdersUseCase {

    enum Result {
        /** Tenía un pago aprobado y pasó a preparación. */
        PAID,
        /** Venció sin pago aprobado y se canceló. */
        EXPIRED,
        /** Sigue pendiente y dentro de plazo. */
        STILL_PENDING,
        /** Ya no estaba pendiente cuando se revisó. */
        SKIPPED
    }

    /** Pedidos pendientes que conviene revisar: los vencidos y los que tienen un checkout abierto. */
    List<Long> findOrdersToReconcile(LocalDateTime now, int limit);

    /**
     * Revisa un pedido, en su propia transacción.
     *
     * @throws com.nomi.backend.domain.exception.PaymentGatewayUnavailableException si no se pudo
     *         consultar la pasarela; el pedido no cambia y se revisa en la siguiente pasada
     */
    Result reconcile(Long orderId, LocalDateTime now);
}
