package com.foodv.backend.infrastructure.notification;

import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import com.foodv.backend.domain.port.out.notification.PushNotificationPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Notificaciones push por Firebase Cloud Messaging, al tema {@code user-{id}}. Si Firebase no
 * está configurado, los envíos se omiten; un error de envío se registra y no se propaga.
 */
@Slf4j
@Component
public class FcmNotificationAdapter implements PushNotificationPort {

    @Value("${firebase.enabled:false}")
    private boolean enabled;

    @Override
    public boolean isAvailable() {
        return enabled && !FirebaseApp.getApps().isEmpty();
    }

    @Override
    public void sendToUser(String userId, String title, String body) {
        if (!isAvailable()) {
            log.debug("FCM no disponible — notificación omitida para usuario {}", userId);
            return;
        }
        try {
            Message message = Message.builder()
                    .setNotification(Notification.builder()
                            .setTitle(title)
                            .setBody(body)
                            .build())
                    .setTopic("user-" + userId)
                    .build();
            String response = FirebaseMessaging.getInstance().send(message);
            log.info("Notificación FCM enviada a usuario {}: {}", userId, response);
        } catch (Exception e) {
            log.error("Error enviando notificación FCM a usuario {}: {}", userId, e.getMessage());
        }
    }

    @Override
    public void sendToTopic(String topic, String title, String body) {
        if (!isAvailable()) {
            log.debug("FCM no disponible — notificación a topic {} omitida", topic);
            return;
        }
        try {
            Message message = Message.builder()
                    .setNotification(Notification.builder()
                            .setTitle(title)
                            .setBody(body)
                            .build())
                    .setTopic(topic)
                    .build();
            String response = FirebaseMessaging.getInstance().send(message);
            log.info("Notificación FCM enviada a topic {}: {}", topic, response);
        } catch (Exception e) {
            log.error("Error enviando notificación FCM a topic {}: {}", topic, e.getMessage());
        }
    }
}
