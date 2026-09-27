package com.foodv.backend.infrastructure.notification;

import com.foodv.backend.domain.model.notification.NotificationEvent;
import com.foodv.backend.domain.port.out.NotificationPort;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * Publica eventos en los temas STOMP de usuario, tienda y pedido.
 */
@Component
@RequiredArgsConstructor
public class WebSocketNotificationAdapter implements NotificationPort {

    private final SimpMessagingTemplate messagingTemplate;

    @Override
    public void notifyUser(Long userId, NotificationEvent event) {
        messagingTemplate.convertAndSend("/topic/user/" + userId, event);
    }

    @Override
    public void notifyStore(Long storeId, NotificationEvent event) {
        messagingTemplate.convertAndSend("/topic/store/" + storeId, event);
    }

    @Override
    public void notifyOrderUpdate(Long orderId, NotificationEvent event) {
        messagingTemplate.convertAndSend("/topic/order/" + orderId, event);
    }
}
