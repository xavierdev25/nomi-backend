package com.nomi.backend.application.order;

import com.nomi.backend.application.payment.OrderPaymentSettlement;
import com.nomi.backend.domain.model.order.Order;
import com.nomi.backend.domain.model.order.OrderStatus;
import com.nomi.backend.domain.port.in.order.ReconcilePendingOrdersUseCase;
import com.nomi.backend.domain.port.out.OrderRepositoryPort;
import com.nomi.backend.domain.port.out.PaymentGatewayPort.GatewayPayment;
import com.nomi.backend.domain.port.out.PaymentRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Concilia los pedidos pendientes con MercadoPago.
 *
 * <p>Antes de cancelar un pedido vencido comprueba si en realidad está pagado: el webhook puede
 * retrasarse o perderse (en desarrollo nunca llega, porque MercadoPago no alcanza
 * {@code localhost}). Así la caducidad nunca cancela un pedido pagado.
 */
@Service
@RequiredArgsConstructor
public class ReconcilePendingOrdersHandler implements ReconcilePendingOrdersUseCase {

    static final String EXPIRED_REASON = "El pago no se completó a tiempo";

    private final OrderRepositoryPort orderRepositoryPort;
    private final PaymentRepositoryPort paymentRepositoryPort;
    private final OrderPaymentSettlement settlement;
    private final PendingOrderCanceller canceller;

    @Override
    @Transactional(readOnly = true)
    public List<Long> findOrdersToReconcile(LocalDateTime now, int limit) {
        return orderRepositoryPort.findByStatus(OrderStatus.PENDIENTE).stream()
                .filter(order -> order.isPaymentExpired(now)
                        || paymentRepositoryPort.findByOrderId(order.getId()).isPresent())
                .sorted(Comparator.comparing(Order::getId))
                .limit(limit)
                .map(Order::getId)
                .toList();
    }

    @Override
    @Transactional
    public Result reconcile(Long orderId, LocalDateTime now) {
        Optional<Order> found = orderRepositoryPort.findByIdWithItems(orderId);
        if (found.isEmpty() || found.get().getStatus() != OrderStatus.PENDIENTE) {
            return Result.SKIPPED;
        }
        Order order = found.get();

        if (paymentRepositoryPort.findByOrderId(orderId).isPresent()) {
            Optional<GatewayPayment> approved = settlement.findApprovedPayment(orderId);
            if (approved.isPresent()) {
                settlement.settleApproved(orderId, approved.get());
                return Result.PAID;
            }
        }

        if (order.isPaymentExpired(now)) {
            return canceller.cancel(order, EXPIRED_REASON, null) ? Result.EXPIRED : Result.SKIPPED;
        }
        return Result.STILL_PENDING;
    }
}
