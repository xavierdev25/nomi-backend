package com.foodv.backend.domain.port.in.payment;

/**
 * Procesamiento de una notificación de MercadoPago ya verificada.
 */
public interface ProcessWebhookUseCase {

    record WebhookEvent(String externalId, String action, String status) {}

    void execute(WebhookEvent event);
}
