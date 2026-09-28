package com.nomi.backend.application.payment;

import com.nomi.backend.domain.model.order.OrderStatus;
import com.nomi.backend.domain.port.in.payment.RefundLatePaymentsUseCase;
import com.nomi.backend.domain.port.out.OrderRepositoryPort;
import com.nomi.backend.domain.port.out.PaymentGatewayPort.GatewayPayment;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Reembolsa el pago aprobado de un pedido cancelado. La decisión es la de siempre
 * ({@link OrderPaymentSettlement}): solo cuenta un pago de ese checkout, y como el pedido está
 * cancelado, se devuelve.
 */
@Service
@RequiredArgsConstructor
public class RefundLatePaymentsHandler implements RefundLatePaymentsUseCase {

    private final OrderRepositoryPort orderRepositoryPort;
    private final OrderPaymentSettlement settlement;

    @Override
    @Transactional(readOnly = true)
    public List<Long> findCancelledOrdersToCheck(LocalDateTime since) {
        return orderRepositoryPort.findCancelledWithOpenCheckoutSince(since);
    }

    @Override
    @Transactional
    public boolean refundIfPaid(Long orderId) {
        boolean cancelled = orderRepositoryPort.findById(orderId)
                .map(order -> order.getStatus() == OrderStatus.CANCELADO)
                .orElse(false);
        if (!cancelled) {
            return false;
        }
        Optional<GatewayPayment> approved = settlement.findApprovedPayment(orderId);
        return approved.isPresent()
                && settlement.settleApproved(orderId, approved.get()) == OrderPaymentSettlement.Outcome.REFUNDED;
    }
}
