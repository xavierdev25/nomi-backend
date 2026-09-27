package com.nomi.backend.infrastructure.persistence.adapter;

import com.nomi.backend.domain.model.order.Order;
import com.nomi.backend.domain.model.order.OrderItem;
import com.nomi.backend.infrastructure.persistence.entity.OrderEntity;
import com.nomi.backend.infrastructure.persistence.entity.OrderItemEntity;
import org.mapstruct.Mapping;
import org.mapstruct.Mapper;

/**
 * Mapeo MapStruct entre {@code Order}/{@code OrderItem} y sus entidades.
 */
@Mapper(componentModel = "spring")
public interface OrderEntityMapper {

    OrderEntity toEntity(Order order);

    Order toDomain(OrderEntity entity);

    @Mapping(target = "orderId", ignore = true)
    OrderItemEntity toItemEntity(OrderItem item);

    OrderItem toItemDomain(OrderItemEntity entity);
}
