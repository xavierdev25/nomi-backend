package com.nomi.backend.infrastructure.persistence.adapter;

import com.nomi.backend.domain.model.payment.Payment;
import com.nomi.backend.infrastructure.persistence.entity.PaymentEntity;
import org.mapstruct.Mapper;

/**
 * Mapeo MapStruct entre {@code Payment} y {@code PaymentEntity}.
 */
@Mapper(componentModel = "spring")
public interface PaymentEntityMapper {

    PaymentEntity toEntity(Payment payment);

    Payment toDomain(PaymentEntity entity);
}
