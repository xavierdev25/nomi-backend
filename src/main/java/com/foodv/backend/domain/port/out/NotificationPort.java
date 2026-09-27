package com.foodv.backend.domain.port.out;

import com.foodv.backend.domain.model.notification.NotificationEvent;

/**
 * Notificaciones en tiempo real por WebSocket (STOMP) a un usuario, una tienda o los
 * suscriptores de un pedido.
 */
public interface NotificationPort {

    void notifyUser(Long userId, NotificationEvent event);

    void notifyStore(Long storeId, NotificationEvent event);

    void notifyOrderUpdate(Long orderId, NotificationEvent event);
}
