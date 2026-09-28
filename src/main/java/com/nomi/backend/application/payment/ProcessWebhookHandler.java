package com.nomi.backend.application.payment;

import com.nomi.backend.domain.exception.PaymentGatewayUnavailableException;
import com.nomi.backend.domain.model.payment.CheckoutReference;
import com.nomi.backend.domain.model.payment.PaymentStatus;
import com.nomi.backend.domain.port.in.payment.ProcessWebhookUseCase;
import com.nomi.backend.domain.port.out.PaymentGatewayPort;
import com.nomi.backend.domain.port.out.PaymentGatewayPort.GatewayPayment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Aplica una notificación de MercadoPago.
 *
 * <p>No confía en el cuerpo del aviso: consulta el pago en MercadoPago y toma de ahí su estado y
 * su pedido (referencia externa). Si la consulta falla, lanza {@link PaymentGatewayUnavailableException}:
 * el controlador responde error y MercadoPago reenvía el aviso más tarde. Tomar el fallo como
 * "pendiente" podría acabar cancelando un pedido que sí está pagado.
 *
 * <p>Solo un pago aprobado cambia el pedido (ver {@link OrderPaymentSettlement}). Un rechazo, un
 * pago pendiente o en revisión solo actualizan el registro: el estudiante puede reintentar en el
 * mismo checkout y, si nunca paga, el pedido caduca.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProcessWebhookHandler implements ProcessWebhookUseCase {

    private final PaymentGatewayPort paymentGatewayPort;
    private final OrderPaymentSettlement settlement;

    @Override
    @Transactional
    public void execute(WebhookEvent event) {
        Optional<GatewayPayment> found = paymentGatewayPort.getPayment(event.externalId());
        if (found.isEmpty()) {
            log.warn("Webhook: MercadoPago no conoce el pago {}; se ignora", event.externalId());
            return;
        }
        GatewayPayment payment = found.get();

        Long orderId = CheckoutReference.orderIdOf(payment.externalReference()).orElse(null);
        if (orderId == null) {
            log.warn("Pago {} sin referencia a un pedido de Nomi ({}); se ignora",
                    payment.id(), payment.externalReference());
            return;
        }

        if (payment.status() == PaymentStatus.APROBADO) {
            OrderPaymentSettlement.Outcome outcome = settlement.settleApproved(orderId, payment);
            log.info("Webhook: pago {} aprobado para el pedido {} → {}", payment.id(), orderId, outcome);
        } else {
            settlement.recordNotApproved(orderId, payment);
            log.info("Webhook: pago {} del pedido {} en estado {}", payment.id(), orderId, payment.status());
        }
    }
}
