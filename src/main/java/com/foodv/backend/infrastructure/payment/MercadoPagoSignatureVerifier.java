package com.foodv.backend.infrastructure.payment;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Verifica la firma HMAC-SHA256 de los webhooks de MercadoPago.
 *
 * <p>El mensaje firmado es {@code id:{dataId};request-id:{xRequestId};ts:{ts};}, la comparación
 * es en tiempo constante y el timestamp se acepta con ±5 minutos para limitar la repetición.
 * Sin secreto configurado rechaza todos los webhooks.
 */
@Slf4j
@Component
public class MercadoPagoSignatureVerifier {

    @Value("${MERCADOPAGO_WEBHOOK_SECRET:}")
    private String webhookSecret;

    public boolean isConfigured() {
        return webhookSecret != null && !webhookSecret.isBlank();
    }

    /**
     * @param xSignatureHeader cabecera {@code x-signature}: {@code ts=...,v1=...}
     * @param dataId id del recurso notificado ({@code data.id})
     * @return {@code true} solo si la firma es válida y el timestamp está en tolerancia
     */
    public boolean verify(String xSignatureHeader, String xRequestId, String dataId) {
        if (!isConfigured()) {
            log.error("MercadoPago webhook secret no configurado. Rechazando webhook.");
            return false;
        }

        if (xSignatureHeader == null || xSignatureHeader.isBlank()) {
            return false;
        }

        Map<String, String> parts = parseSignatureHeader(xSignatureHeader);
        String ts = parts.get("ts");
        String v1 = parts.get("v1");

        if (ts == null || v1 == null) {
            log.warn("X-Signature inválida: faltan campos ts o v1");
            return false;
        }

        long tsSeconds;
        try {
            long tsParsed = Long.parseLong(ts);
            tsSeconds = tsParsed > 9_999_999_999L ? tsParsed / 1000L : tsParsed;
        } catch (NumberFormatException e) {
            log.warn("X-Signature ts no numérico");
            return false;
        }
        long nowSeconds = Instant.now().getEpochSecond();
        if (Math.abs(nowSeconds - tsSeconds) > 300L) {
            log.warn("X-Signature ts fuera de tolerancia ({} vs {})", tsSeconds, nowSeconds);
            return false;
        }

        String message = buildManifest(dataId, xRequestId, ts);
        String computed = hmacSha256Hex(webhookSecret, message);
        return constantTimeEquals(computed, v1);
    }

    private String buildManifest(String dataId, String xRequestId, String ts) {
        StringBuilder manifest = new StringBuilder();

        if (dataId != null && !dataId.isBlank()) {
            manifest.append("id:")
                    .append(dataId.trim().toLowerCase(Locale.ROOT))
                    .append(";");
        }

        if (xRequestId != null && !xRequestId.isBlank()) {
            manifest.append("request-id:")
                    .append(xRequestId.trim())
                    .append(";");
        }

        manifest.append("ts:")
                .append(ts.trim())
                .append(";");

        return manifest.toString();
    }

    private Map<String, String> parseSignatureHeader(String header) {
        Map<String, String> result = new HashMap<>();
        for (String part : header.split(",")) {
            String[] kv = part.trim().split("=", 2);
            if (kv.length == 2) {
                result.put(kv[0].trim().toLowerCase(), kv[1].trim());
            }
        }
        return result;
    }

    private String hmacSha256Hex(String secret, String message) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            log.error("Error calculando HMAC: {}", e.getMessage());
            return "";
        }
    }

    private boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) return false;
        return MessageDigest.isEqual(
                a.getBytes(StandardCharsets.UTF_8),
                b.getBytes(StandardCharsets.UTF_8)
        );
    }
}
