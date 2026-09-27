package com.foodv.backend.infrastructure.web.mapper;

import com.foodv.backend.domain.common.PagedResult;
import com.foodv.backend.domain.model.order.Order;
import com.foodv.backend.domain.model.order.OrderItem;
import com.foodv.backend.domain.port.in.order.CreateOrderUseCase;
import com.foodv.backend.infrastructure.web.dto.common.PageResponse;
import com.foodv.backend.infrastructure.web.dto.order.CreateOrderRequest;
import com.foodv.backend.infrastructure.web.dto.order.OrderItemRequest;
import com.foodv.backend.infrastructure.web.dto.order.OrderItemResponse;
import com.foodv.backend.infrastructure.web.dto.order.OrderResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

/**
 * Mapeo MapStruct entre DTOs web y el dominio de pedidos.
 */
@Mapper(componentModel = "spring")
public interface OrderWebMapper {

    @Mapping(target = "statusDescripcion", expression = "java(order.getStatus().enEspanol())")
    OrderResponse toResponse(Order order);

    OrderItemResponse toItemResponse(OrderItem item);

    CreateOrderUseCase.CreateOrderCommand toCommand(CreateOrderRequest request);

    CreateOrderUseCase.OrderItemCommand toItemCommand(OrderItemRequest request);

    default CreateOrderUseCase.CreateOrderCommand toCommandWithUser(CreateOrderRequest request, Long userId) {
        List<CreateOrderUseCase.OrderItemCommand> items = request.items().stream()
                .map(i -> new CreateOrderUseCase.OrderItemCommand(i.productId(), i.cantidad()))
                .toList();
        return new CreateOrderUseCase.CreateOrderCommand(
                userId,
                request.storeId(),
                request.aulaId(),
                items,
                request.notas(),
                request.propina()
        );
    }

    default PageResponse<OrderResponse> toPageResponse(PagedResult<Order> page) {
        return PageResponse.from(page.map(this::toResponse));
    }
}
