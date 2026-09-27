package com.nomi.backend.domain.port.out;

import com.nomi.backend.domain.model.payment.Payment;

import java.util.List;
import java.util.Optional;

/**
 * Persistencia de pagos.
 */
public interface PaymentRepositoryPort {

    Payment save(Payment payment);

    Optional<Payment> findById(Long id);

    Optional<Payment> findByOrderId(Long orderId);

    Optional<Payment> findByExternalId(String externalId);

    List<Payment> findByUserId(Long userId);
}
