package com.nomi.backend.domain.port.in.payment;

import com.nomi.backend.domain.model.payment.Payment;

import java.util.List;

/**
 * Consulta de pagos.
 */
public interface FindPaymentUseCase {

    Payment findById(Long id);

    Payment findByOrderId(Long orderId);

    List<Payment> findByUserId(Long userId);
}
