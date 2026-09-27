package com.foodv.backend.domain.port.out;

/**
 * Contadores de negocio para monitoreo (Micrometer / Prometheus).
 */
public interface BusinessMetricsPort {

    void recordOrderCreated();

    void recordOrderCompleted();

    void recordOrderCancelled();

    void recordPaymentCompleted();

    void recordPaymentFailed();

    void recordUserRegistered();

    void recordStoreCreated();
}
