package com.nomi.backend.infrastructure.metrics;

import com.nomi.backend.domain.port.out.BusinessMetricsPort;
import org.springframework.stereotype.Component;

/**
 * Implementación de {@code BusinessMetricsPort} sobre {@code BusinessMetricsService}.
 */
@Component
public class MicrometerBusinessMetricsAdapter implements BusinessMetricsPort {

    private final BusinessMetricsService delegate;

    public MicrometerBusinessMetricsAdapter(BusinessMetricsService delegate) {
        this.delegate = delegate;
    }

    @Override public void recordOrderCreated() { delegate.recordOrderCreated(); }
    @Override public void recordOrderCompleted() { delegate.recordOrderCompleted(); }
    @Override public void recordOrderCancelled() { delegate.recordOrderCancelled(); }
    @Override public void recordPaymentCompleted() { delegate.recordPaymentCompleted(); }
    @Override public void recordPaymentFailed() { delegate.recordPaymentFailed(); }
    @Override public void recordUserRegistered() { delegate.recordUserRegistered(); }
    @Override public void recordStoreCreated() { delegate.recordStoreCreated(); }
}
