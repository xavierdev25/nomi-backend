package com.foodv.backend.application.payment;

import com.foodv.backend.domain.model.payment.Payment;
import com.foodv.backend.domain.port.in.payment.FindPaymentUseCase;
import com.foodv.backend.domain.port.out.PaymentRepositoryPort;
import com.foodv.backend.domain.exception.ResourceNotFoundException;
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
