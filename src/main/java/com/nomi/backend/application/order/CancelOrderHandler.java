package com.nomi.backend.application.order;

import com.nomi.backend.application.payment.OrderPaymentSettlement;
import com.nomi.backend.domain.exception.OrderAlreadyPaidException;
import com.nomi.backend.domain.exception.PaymentGatewayUnavailableException;
import com.nomi.backend.domain.exception.ResourceNotFoundException;
import com.nomi.backend.domain.model.order.Order;
import com.nomi.backend.domain.model.order.OrderStatus;
import com.nomi.backend.domain.port.in.order.CancelOrderUseCase;
import com.nomi.backend.domain.port.out.OrderRepositoryPort;
import com.nomi.backend.domain.port.out.PaymentGatewayPort.GatewayPayment;
import com.nomi.backend.domain.port.out.PaymentRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Cancela un pedido pendiente y devuelve su stock.
 *
 * <p>Si el pedido tiene un checkout, antes consulta MercadoPago: el estudiante puede haber pagado
 * y cancelado antes de que llegue el webhook. Si hay un pago aprobado, confirma el pedido (pasa a
 * preparación) y responde {@link OrderAlreadyPaidException} en lugar de cancelarlo sin reembolso.
 * Si MercadoPago no responde, no cancela ({@link PaymentGatewayUnavailableException}): cancelar a
 * ciegas podría dejar al estudiante sin pedido y sin su dinero.
 */
@Service
@RequiredArgsConstructor
public class CancelOrderHandler implements CancelOrderUseCase {

    private final OrderRepositoryPort orderRepositoryPort;
    private final PaymentRepositoryPort paymentRepositoryPort;
    private final OrderPaymentSettlement settlement;
    private final PendingOrderCanceller canceller;

    /**
     * @throws OrderAlreadyPaidException          si el pago ya estaba aprobado (el pedido queda confirmado)
     * @throws PaymentGatewayUnavailableException si no se pudo comprobar el pago
     */
    @Override
    @Transactional(noRollbackFor = OrderAlreadyPaidException.class)
    public Order execute(CancelOrderCommand command) {
        Order existing = orderRepositoryPort.findByIdWithItems(command.orderId())
                .orElseThrow(() -> new ResourceNotFoundException("Orden no encontrada"));

        if (existing.getStatus() != OrderStatus.PENDIENTE) {
            throw new IllegalArgumentException(
                    "La orden no puede cancelarse en estado: " + existing.getStatus().enEspanol());
        }

        if (paymentRepositoryPort.findByOrderId(existing.getId()).isPresent()) {
            Optional<GatewayPayment> approved = settlement.findApprovedPayment(existing.getId());
            if (approved.isPresent()) {
                settlement.settleApproved(existing.getId(), approved.get());
                throw new OrderAlreadyPaidException(
                        "Tu pago ya fue aprobado: el pedido pasó a preparación y ya no se puede cancelar.");
            }
        }

        if (!canceller.cancel(existing, command.motivoCancelacion(), command.canceladoPor())) {
            throw new IllegalStateException("El pedido cambió de estado mientras se cancelaba. Vuelve a cargarlo.");
        }
        return orderRepositoryPort.findByIdWithItems(existing.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Orden no encontrada"));
    }
}
