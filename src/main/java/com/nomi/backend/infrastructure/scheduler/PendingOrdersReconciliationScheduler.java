package com.nomi.backend.infrastructure.scheduler;

import com.nomi.backend.domain.exception.PaymentGatewayUnavailableException;
import com.nomi.backend.domain.port.in.order.ReconcilePendingOrdersUseCase;
import com.nomi.backend.domain.port.in.order.ReconcilePendingOrdersUseCase.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Cada minuto ({@code nomi.orders.reconcile-interval-ms}) concilia los pedidos pendientes:
 * confirma los pagados y cancela los que vencieron sin pago. Cada pedido va en su propia
 * transacción, así que un fallo de MercadoPago con uno no impide revisar los demás; ese pedido se
 * vuelve a revisar en la siguiente pasada.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PendingOrdersReconciliationScheduler {

    private static final int BATCH_SIZE = 200;

    private final ReconcilePendingOrdersUseCase reconcilePendingOrders;

    @Scheduled(fixedDelayString = "${nomi.orders.reconcile-interval-ms:60000}", initialDelay = 30_000)
    public void reconcilePendingOrders() {
        LocalDateTime now = LocalDateTime.now();
        List<Long> orderIds = reconcilePendingOrders.findOrdersToReconcile(now, BATCH_SIZE);
        Map<Result, Integer> results = new EnumMap<>(Result.class);
        int failed = 0;
        for (Long orderId : orderIds) {
            try {
                results.merge(reconcilePendingOrders.reconcile(orderId, now), 1, Integer::sum);
            } catch (PaymentGatewayUnavailableException e) {
                failed++;
                log.warn("Pedido {}: MercadoPago no respondió, se revisará en la siguiente pasada", orderId);
            } catch (Exception e) {
                failed++;
                log.error("Pedido {}: error al conciliar", orderId, e);
            }
        }
        int paid = results.getOrDefault(Result.PAID, 0);
        int expired = results.getOrDefault(Result.EXPIRED, 0);
        if (paid > 0 || expired > 0 || failed > 0) {
            log.info("Conciliación de pedidos pendientes: {} confirmados, {} caducados, {} con error",
                    paid, expired, failed);
        }
    }
}
