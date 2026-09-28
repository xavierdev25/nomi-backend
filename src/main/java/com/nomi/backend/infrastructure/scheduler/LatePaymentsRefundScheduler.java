package com.nomi.backend.infrastructure.scheduler;

import com.nomi.backend.domain.exception.PaymentGatewayUnavailableException;
import com.nomi.backend.domain.port.in.payment.RefundLatePaymentsUseCase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Cada 10 minutos ({@code nomi.orders.late-payment-check-interval-ms}) revisa los pedidos cancelados
 * en las últimas 72 horas ({@code nomi.orders.late-payment-window-hours}) que tenían un checkout sin
 * pago aprobado, y reembolsa los pagos que se aprobaron tarde.
 *
 * <p>La ventana cubre los pagos con tarjeta en revisión, que MercadoPago resuelve en hasta dos días
 * hábiles; los medios diferidos (efectivo, cajeros), que tardan más, no se ofrecen. Cada revisión
 * consulta MercadoPago una vez por pedido, así que el coste crece con los pedidos cancelados con
 * checkout, no con todos los pedidos.
 */
@Slf4j
@Component
public class LatePaymentsRefundScheduler {

    private final RefundLatePaymentsUseCase refundLatePayments;
    private final Duration window;

    public LatePaymentsRefundScheduler(RefundLatePaymentsUseCase refundLatePayments,
                                       @Value("${nomi.orders.late-payment-window-hours:72}") long windowHours) {
        this.refundLatePayments = refundLatePayments;
        this.window = Duration.ofHours(windowHours);
    }

    @Scheduled(fixedDelayString = "${nomi.orders.late-payment-check-interval-ms:600000}", initialDelay = 60_000)
    public void refundLatePayments() {
        List<Long> orderIds = refundLatePayments.findCancelledOrdersToCheck(LocalDateTime.now().minus(window));
        int refunded = 0;
        int failed = 0;
        for (Long orderId : orderIds) {
            try {
                if (refundLatePayments.refundIfPaid(orderId)) {
                    refunded++;
                }
            } catch (PaymentGatewayUnavailableException e) {
                failed++;
                log.warn("Pedido {}: MercadoPago no respondió al revisar pagos tardíos; se reintentará", orderId);
            } catch (Exception e) {
                failed++;
                log.error("Pedido {}: error al revisar pagos tardíos", orderId, e);
            }
        }
        if (refunded > 0 || failed > 0) {
            log.info("Pagos tardíos de pedidos cancelados: {} revisados, {} reembolsados, {} con error",
                    orderIds.size(), refunded, failed);
        }
    }
}
