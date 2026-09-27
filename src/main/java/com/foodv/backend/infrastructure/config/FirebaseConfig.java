package com.foodv.backend.infrastructure.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;
import java.io.FileInputStream;
import java.io.IOException;

/**
 * Inicializa Firebase Admin para las notificaciones push. Con {@code firebase.enabled=false}
 * (valor por defecto) no hace nada y las notificaciones push se omiten.
 */
@Slf4j
@Configuration
public class FirebaseConfig {

    @Value("${firebase.enabled:false}")
    private boolean enabled;

    @Value("${firebase.credentials-path:}")
    private String credentialsPath;

    @Value("${firebase.project-id:}")
    private String projectId;

    @PostConstruct
    public void initialize() {
        if (!enabled) {
            log.info("Firebase FCM deshabilitado (FIREBASE_ENABLED=false)");
            return;
        }
        if (credentialsPath == null || credentialsPath.isBlank()) {
            log.warn("Firebase habilitado pero FIREBASE_CREDENTIALS_PATH no configurado");
            return;
        }
        try {
            if (!FirebaseApp.getApps().isEmpty()) {
                log.info("Firebase ya inicializado");
                return;
            }
            FileInputStream serviceAccount = new FileInputStream(credentialsPath);
            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                    .setProjectId(projectId)
                    .build();
            FirebaseApp.initializeApp(options);
            log.info("Firebase FCM inicializado correctamente para proyecto: {}", projectId);
        } catch (IOException e) {
            log.error("Error inicializando Firebase: {}", e.getMessage());
        }
    }
}
