package com.foodv.backend.infrastructure.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Contadores y temporizadores de negocio en Micrometer ({@code foodv.orders.*},
 * {@code foodv.payments.*}, {@code foodv.users.registered}, {@code foodv.stores.created}),
 * expuestos en {@code /actuator/prometheus}.
 */
@Slf4j
@Service
public class BusinessMetricsService {

    private final Counter ordersCreated;
    private final Counter ordersCompleted;
    private final Counter ordersCancelled;
    private final Counter paymentsCompleted;
    private final Counter paymentsFailed;
    private final Counter usersRegistered;
    private final Counter storesCreated;
    private final Timer orderProcessingTime;

    public BusinessMetricsService(MeterRegistry registry) {
        this.ordersCreated = Counter.builder("foodv.orders.created")
                .description("Total de órdenes creadas")
                .register(registry);

        this.ordersCompleted = Counter.builder("foodv.orders.completed")
                .description("Total de órdenes completadas")
                .register(registry);

        this.ordersCancelled = Counter.builder("foodv.orders.cancelled")
                .description("Total de órdenes canceladas")
                .register(registry);

        this.paymentsCompleted = Counter.builder("foodv.payments.completed")
                .description("Total de pagos completados")
                .register(registry);

        this.paymentsFailed = Counter.builder("foodv.payments.failed")
                .description("Total de pagos fallidos")
                .register(registry);

        this.usersRegistered = Counter.builder("foodv.users.registered")
                .description("Total de usuarios registrados")
                .register(registry);

        this.storesCreated = Counter.builder("foodv.stores.created")
                .description("Total de tiendas creadas")
                .register(registry);

        this.orderProcessingTime = Timer.builder("foodv.orders.processing.time")
                .description("Tiempo de procesamiento de órdenes")
                .register(registry);
    }

    public void recordOrderCreated() { ordersCreated.increment(); }
    public void recordOrderCompleted() { ordersCompleted.increment(); }
    public void recordOrderCancelled() { ordersCancelled.increment(); }
    public void recordPaymentCompleted() { paymentsCompleted.increment(); }
    public void recordPaymentFailed() { paymentsFailed.increment(); }
    public void recordUserRegistered() { usersRegistered.increment(); }
    public void recordStoreCreated() { storesCreated.increment(); }
    public Timer.Sample startOrderTimer() { return Timer.start(); }
    public void stopOrderTimer(Timer.Sample sample) { sample.stop(orderProcessingTime); }
}
