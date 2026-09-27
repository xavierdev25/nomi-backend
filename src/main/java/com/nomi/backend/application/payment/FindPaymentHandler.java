package com.nomi.backend.application.payment;

import com.nomi.backend.domain.model.payment.Payment;
import com.nomi.backend.domain.port.in.payment.FindPaymentUseCase;
import com.nomi.backend.domain.port.out.PaymentRepositoryPort;
import com.nomi.backend.domain.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Consulta de pagos.
 */
@Service
@RequiredArgsConstructor
public class FindPaymentHandler implements FindPaymentUseCase {

    private final PaymentRepositoryPort paymentRepositoryPort;

    @Override
    public Payment findById(Long id) {
        return paymentRepositoryPort.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Pago no encontrado"));
    }

    @Override
    public Payment findByOrderId(Long orderId) {
        return paymentRepositoryPort.findByOrderId(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Pago no encontrado para esta orden"));
    }

    @Override
    public List<Payment> findByUserId(Long userId) {
        return paymentRepositoryPort.findByUserId(userId);
    }
}
