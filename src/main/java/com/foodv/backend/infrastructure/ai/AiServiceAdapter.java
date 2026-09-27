package com.foodv.backend.infrastructure.ai;

import com.foodv.backend.domain.model.ai.AiRecommendationRequest;
import com.foodv.backend.domain.model.ai.AiRecommendationResponse;
import com.foodv.backend.domain.port.out.AiRecommendationPort;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Cliente HTTP del servicio de IA ({@code POST /api/ai/recommendations}), autenticado con la
 * cabecera {@code X-API-Key}. Envía también {@code X-FoodV-User-Id} para que el servicio aplique
 * su límite de peticiones por estudiante y no uno global para todo el backend.
 *
 * <p>Un circuit breaker de Resilience4j protege la llamada: ante error, timeout o circuito
 * abierto se devuelve una respuesta vacía con {@code generatedBy = "FALLBACK"} en lugar de
 * propagar el fallo. Los errores 4xx también caen en esa respuesta, pero no abren el circuito
 * ({@code AiResilienceConfig}). Los timeouts están en {@code RestClientConfig}.
 */
@Slf4j
@Component
public class AiServiceAdapter implements AiRecommendationPort {

    /** Cabecera con el estudiante que pide, para el límite de peticiones del servicio de IA. */
    public static final String USER_ID_HEADER = "X-FoodV-User-Id";

    private final RestClient restClient;
    private final String aiServiceUrl;
    private final String aiSecretKey;

    public AiServiceAdapter(RestClient restClient,
                            @Value("${ai.service.url:http://localhost:8001}") String aiServiceUrl,
                            @Value("${ai.service.secret-key:}") String aiSecretKey) {
        this.restClient = restClient;
        this.aiServiceUrl = aiServiceUrl;
        this.aiSecretKey = aiSecretKey;
    }

    @Override
    @CircuitBreaker(name = "aiService", fallbackMethod = "getRecommendationsFallback")
    public AiRecommendationResponse getRecommendations(AiRecommendationRequest request) {
        List<Map<String, Object>> productsPayload = request.getAvailableProducts().stream()
                .map(p -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("id", p.id());
                    map.put("nombre", p.nombre());
                    map.put("precio", p.precio());
                    map.put("categoria", p.categoria());
                    return map;
                })
                .toList();

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("user_id", request.getUserId());
        requestBody.put("restrictions", request.getRestrictions());
        requestBody.put("preferences", request.getPreferences());
        requestBody.put("available_products", productsPayload);
        requestBody.put("max_recommendations", request.getMaxRecommendations());

        @SuppressWarnings("unchecked")
        Map<String, Object> response = restClient.post()
                .uri(aiServiceUrl + "/api/ai/recommendations")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-API-Key", aiSecretKey)
                .header(USER_ID_HEADER, String.valueOf(request.getUserId()))
                .body(requestBody)
                .retrieve()
                .body(Map.class);

        return parseResponse(response);
    }

    /**
     * Respuesta de degradación: sin recomendaciones, pero la pantalla del cliente sigue funcionando.
     */
    private AiRecommendationResponse getRecommendationsFallback(AiRecommendationRequest request, Throwable throwable) {
        if (throwable instanceof HttpClientErrorException clientError) {
            // Un 4xx no es una caída del servicio: es un contrato roto (422) o una cuota (429).
            String body = clientError.getResponseBodyAsString();
            log.error("Servicio de IA rechazó la petición: {} {}", clientError.getStatusCode(),
                    body.length() > 300 ? body.substring(0, 300) + "…" : body);
        } else {
            log.warn("Fallback IA activado: {}", throwable.getMessage());
        }
        return AiRecommendationResponse.builder()
                .userId(request.getUserId())
                .recommendations(List.of())
                .generatedBy("FALLBACK")
                .build();
    }

    @SuppressWarnings("unchecked")
    private AiRecommendationResponse parseResponse(Map<String, Object> response) {
        List<Map<String, Object>> recs = (List<Map<String, Object>>) response.get("recommendations");

        List<AiRecommendationResponse.ProductRecommendation> recommendations = new ArrayList<>();
        if (recs != null) {
            for (Map<String, Object> rec : recs) {
                recommendations.add(new AiRecommendationResponse.ProductRecommendation(
                        ((Number) rec.get("product_id")).longValue(),
                        (String) rec.get("nombre"),
                        new BigDecimal(rec.get("precio").toString()),
                        (String) rec.get("categoria"),
                        ((Number) rec.get("score")).doubleValue(),
                        (String) rec.get("reason")
                ));
            }
        }

        return AiRecommendationResponse.builder()
                .userId(((Number) response.get("user_id")).longValue())
                .recommendations(recommendations)
                .generatedBy((String) response.get("generated_by"))
                .build();
    }
}
