package com.nomi.backend.infrastructure.config;

import com.nomi.backend.domain.model.order.OrderPaymentPolicy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Política de pago de los pedidos, configurable por entorno ({@code ORDER_PAYMENT_WINDOW_MINUTES},
 * {@code ORDER_MAX_PENDING_PER_USER}).
 */
@Configuration
public class OrderPolicyConfig {

    @Bean
    public OrderPaymentPolicy orderPaymentPolicy(
            @Value("${nomi.orders.payment-window-minutes:15}") long paymentWindowMinutes,
            @Value("${nomi.orders.max-pending-per-user:2}") int maxPendingPerUser) {
        return new OrderPaymentPolicy(Duration.ofMinutes(paymentWindowMinutes), maxPendingPerUser);
    }
}
