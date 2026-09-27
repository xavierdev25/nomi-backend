package com.nomi.backend.domain.port.out.notification;

/**
 * Notificaciones push (Firebase Cloud Messaging). Sin Firebase configurado los envíos se
 * omiten en silencio.
 */
public interface PushNotificationPort {
    void sendToUser(String userId, String title, String body);
    void sendToTopic(String topic, String title, String body);
    boolean isAvailable();
}
