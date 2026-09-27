package com.nomi.backend.infrastructure.config;

import io.github.resilience4j.common.circuitbreaker.configuration.CircuitBreakerConfigCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.HttpClientErrorException;

/**
 * Ajustes del circuit breaker {@code aiService} que no caben en {@code application.yaml}.
 *
 * <p>Los errores 4xx del servicio de IA (petición inválida, límite superado) no cuentan como
 * fallos: el servicio está sano y reintentar no los arregla. Si contaran, un contrato roto o una
 * cuota agotada abrirían el circuito 30 s para todos los estudiantes. La llamada sigue cayendo en
 * la respuesta de degradación, y el adaptador los registra como error.
 */
@Configuration
public class AiResilienceConfig {

    @Bean
    public CircuitBreakerConfigCustomizer aiServiceCircuitBreakerCustomizer() {
        return CircuitBreakerConfigCustomizer.of("aiService",
                builder -> builder.ignoreExceptions(HttpClientErrorException.class));
    }
}
