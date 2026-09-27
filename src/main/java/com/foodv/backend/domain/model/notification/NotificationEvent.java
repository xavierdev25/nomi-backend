package com.foodv.backend.domain.model.notification;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * Evento que se publica por WebSocket o push al cambiar un pedido o un pago.
 * {@code payload} lleva el objeto afectado completo.
 */
@Getter
@Builder
public class NotificationEvent {

    private String type;
    private Long orderId;
    private Long userId;
    private Long storeId;
    private String message;
    private Object payload;
    private LocalDateTime timestamp;
}
