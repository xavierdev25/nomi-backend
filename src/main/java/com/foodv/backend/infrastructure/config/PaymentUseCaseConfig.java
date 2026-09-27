package com.foodv.backend.infrastructure.config;

import com.foodv.backend.application.payment.CreatePaymentHandler;
import com.foodv.backend.domain.port.in.payment.CreatePaymentUseCase;
import com.foodv.backend.domain.port.out.OrderRepositoryPort;
import com.foodv.backend.domain.port.out.PaymentGatewayPort;
import com.foodv.backend.domain.port.out.PaymentRepositoryPort;
import com.foodv.backend.domain.port.out.UserRepositoryPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registra {@code CreatePaymentHandler} como bean, inyectando la URL de notificación de
 * MercadoPago desde la configuración.
 */
@Configuration
public class PaymentUseCaseConfig {

    @Bean
    public CreatePaymentUseCase createPaymentUseCase(PaymentRepositoryPort paymentRepositoryPort,
                                                     PaymentGatewayPort paymentGatewayPort,
                                                     OrderRepositoryPort orderRepositoryPort,
                                                     UserRepositoryPort userRepositoryPort,
                                                     @Value("${mercadopago.notification-url:http://localhost:8080/api/payments/webhook}") String notificationUrl) {
        return new CreatePaymentHandler(
                paymentRepositoryPort,
                paymentGatewayPort,
                orderRepositoryPort,
                userRepositoryPort,
                notificationUrl
        );
    }
}
