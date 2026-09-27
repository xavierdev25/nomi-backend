package integration;

import com.nomi.backend.domain.model.ai.AiRecommendationRequest;
import com.nomi.backend.domain.model.ai.AiRecommendationResponse;
import com.nomi.backend.domain.port.out.AiRecommendationPort;
import com.nomi.backend.infrastructure.ai.AiServiceAdapter;
import com.nomi.backend.infrastructure.config.AiResilienceConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.springboot3.circuitbreaker.autoconfigure.CircuitBreakerAutoConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

/**
 * Circuit breaker del cliente del servicio de IA: tras fallos consecutivos el circuito se abre
 * y se usa la respuesta de degradación sin llamar al servicio. Los 4xx caen en la degradación
 * pero no abren el circuito.
 */
@SpringBootTest(
        classes = {
                AiServiceAdapterTest.TestBeans.class
        },
        properties = {
                "ai.service.url=http://localhost:8001",
                "ai.service.secret-key=test-secret",
                "resilience4j.circuitbreaker.instances.aiService.sliding-window-type=COUNT_BASED",
                "resilience4j.circuitbreaker.instances.aiService.sliding-window-size=3",
                "resilience4j.circuitbreaker.instances.aiService.minimum-number-of-calls=3",
                "resilience4j.circuitbreaker.instances.aiService.failure-rate-threshold=100",
                "resilience4j.circuitbreaker.instances.aiService.wait-duration-in-open-state=60s"
        }
)
@ImportAutoConfiguration({
        AopAutoConfiguration.class,
        CircuitBreakerAutoConfiguration.class
})
class AiServiceAdapterTest {

    @jakarta.annotation.Resource(name = "aiServiceAdapter")
    private AiRecommendationPort aiRecommendationPort;

    @jakarta.annotation.Resource
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @jakarta.annotation.Resource
    private MockRestServiceServer server;

    @BeforeEach
    void resetCircuitAndServer() {
        circuitBreakerRegistry.circuitBreaker("aiService").reset();
        server.reset();
    }

    private static AiRecommendationRequest request() {
        return AiRecommendationRequest.builder()
                .userId(1L)
                .restrictions(List.of())
                .preferences(List.of("pollo"))
                .availableProducts(List.of(new AiRecommendationRequest.ProductInfo(
                        1L, "Menu", BigDecimal.TEN, "CRIOLLA")))
                .maxRecommendations(3)
                .build();
    }

    @Test
    void clientErrorsUseFallbackButDoNotOpenTheCircuit() {
        server.expect(ExpectedCount.times(2), requestTo("http://localhost:8001/api/ai/recommendations"))
                .andRespond(withStatus(HttpStatus.UNPROCESSABLE_ENTITY));
        server.expect(requestTo("http://localhost:8001/api/ai/recommendations"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        server.expect(requestTo("http://localhost:8001/api/ai/recommendations"))
                .andRespond(withStatus(HttpStatus.UNPROCESSABLE_ENTITY));

        for (int attempt = 0; attempt < 4; attempt++) {
            assertEquals("FALLBACK", aiRecommendationPort.getRecommendations(request()).getGeneratedBy());
        }

        assertEquals(CircuitBreaker.State.CLOSED, circuitBreakerRegistry.circuitBreaker("aiService").getState());
        server.verify();
    }

    @Test
    void sendsTheStudentIdSoTheServiceCanRateLimitPerStudent() {
        server.expect(requestTo("http://localhost:8001/api/ai/recommendations"))
                .andExpect(header(AiServiceAdapter.USER_ID_HEADER, "1"))
                .andExpect(header("X-API-Key", "test-secret"))
                .andRespond(withServerError());

        aiRecommendationPort.getRecommendations(request());

        server.verify();
    }

    @Test
    void circuitBreakerOpensAndUsesFallbackOnFourthAttempt() {
        server.expect(ExpectedCount.times(3), requestTo("http://localhost:8001/api/ai/recommendations"))
                .andRespond(withServerError());

        AiRecommendationRequest request = AiRecommendationRequest.builder()
                .userId(1L)
                .restrictions(List.of())
                .preferences(List.of("pollo"))
                .availableProducts(List.of(new AiRecommendationRequest.ProductInfo(
                        1L,
                        "Menu",
                        BigDecimal.TEN,
                        "CRIOLLA"
                )))
                .maxRecommendations(3)
                .build();

        for (int attempt = 0; attempt < 3; attempt++) {
            AiRecommendationResponse response = aiRecommendationPort.getRecommendations(request);
            assertEquals("FALLBACK", response.getGeneratedBy());
        }

        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("aiService");
        assertEquals(CircuitBreaker.State.OPEN, circuitBreaker.getState());

        AiRecommendationResponse fourthResponse = aiRecommendationPort.getRecommendations(request);

        assertEquals("FALLBACK", fourthResponse.getGeneratedBy());
        assertEquals(CircuitBreaker.State.OPEN, circuitBreaker.getState());
        server.verify();
    }

    @Configuration
    @Import(AiResilienceConfig.class)
    static class TestBeans {

        @Bean
        RestClient.Builder restClientBuilder() {
            return RestClient.builder();
        }

        @Bean
        MockRestServiceServer mockRestServiceServer(RestClient.Builder restClientBuilder) {
            return MockRestServiceServer.bindTo(restClientBuilder).build();
        }

        @Bean
        RestClient restClient(RestClient.Builder restClientBuilder, MockRestServiceServer mockRestServiceServer) {
            return restClientBuilder.build();
        }

        @Bean
        AiServiceAdapter aiServiceAdapter(RestClient restClient,
                                          @Value("${ai.service.url}") String aiServiceUrl,
                                          @Value("${ai.service.secret-key}") String aiSecretKey) {
            return new AiServiceAdapter(restClient, aiServiceUrl, aiSecretKey);
        }
    }
}
