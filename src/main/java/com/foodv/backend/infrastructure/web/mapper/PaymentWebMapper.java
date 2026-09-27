package com.foodv.backend.infrastructure.web.mapper;

import com.foodv.backend.domain.model.payment.Payment;
import com.foodv.backend.domain.port.in.payment.CreatePaymentUseCase;
import com.foodv.backend.infrastructure.web.dto.payment.CreatePaymentRequest;
import com.foodv.backend.infrastructure.web.dto.payment.PaymentResponse;
import org.mapstruct.Mapper;

/**
 * Mapeo MapStruct entre DTOs web y el dominio de pagos.
 */
@Mapper(componentModel = "spring")
public interface PaymentWebMapper {

    PaymentResponse toResponse(Payment payment);

    default CreatePaymentUseCase.CreatePaymentCommand toCommand(CreatePaymentRequest request, String email) {
        return new CreatePaymentUseCase.CreatePaymentCommand(request.orderId(), email);
    }
}
