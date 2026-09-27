package com.nomi.backend.application.payment;

import com.nomi.backend.domain.exception.AuthorizationException;
import com.nomi.backend.domain.exception.ResourceNotFoundException;
import com.nomi.backend.domain.model.order.Order;
import com.nomi.backend.domain.model.payment.Payment;
import com.nomi.backend.domain.model.payment.PaymentStatus;
import com.nomi.backend.domain.model.user.User;
import com.nomi.backend.domain.model.user.UserRole;
import com.nomi.backend.domain.port.in.payment.CreatePaymentUseCase;
import com.nomi.backend.domain.port.out.OrderRepositoryPort;
import com.nomi.backend.domain.port.out.PaymentGatewayPort;
import com.nomi.backend.domain.port.out.PaymentRepositoryPort;
import com.nomi.backend.domain.port.out.UserRepositoryPort;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Crea el pago de un pedido en MercadoPago.
 *
 * <p>El monto se calcula en el servidor ({@code total + propina + tarifa + comisión}); el
 * cliente solo envía el id del pedido. Solo el dueño del pedido o un administrador pueden
 * pagarlo, y un pedido admite un único pago: un segundo intento responde 400.
 */
public class CreatePaymentHandler implements CreatePaymentUseCase {

    private final PaymentRepositoryPort paymentRepositoryPort;
    private final PaymentGatewayPort paymentGatewayPort;
    private final OrderRepositoryPort orderRepositoryPort;
    private final UserRepositoryPort userRepositoryPort;
    private final String notificationUrl;

    public CreatePaymentHandler(PaymentRepositoryPort paymentRepositoryPort,
                                PaymentGatewayPort paymentGatewayPort,
                                OrderRepositoryPort orderRepositoryPort,
                                UserRepositoryPort userRepositoryPort,
                                String notificationUrl) {
        this.paymentRepositoryPort = paymentRepositoryPort;
        this.paymentGatewayPort = paymentGatewayPort;
        this.orderRepositoryPort = orderRepositoryPort;
        this.userRepositoryPort = userRepositoryPort;
        this.notificationUrl = notificationUrl;
    }

    @Override
    @Transactional
    public Payment execute(CreatePaymentCommand command) {
        if (command.orderId() == null || command.requesterEmail() == null) {
            throw new IllegalArgumentException("Datos de pago incompletos");
        }

        User requester = userRepositoryPort.findByEmail(command.requesterEmail())
                .orElseThrow(() -> new AuthorizationException("Usuario no encontrado"));

        Order order = orderRepositoryPort.findByIdWithItems(command.orderId())
                .orElseThrow(() -> new ResourceNotFoundException("Orden no encontrada"));

        if (requester.getRole() != UserRole.ADMIN && !requester.getId().equals(order.getUserId())) {
            throw new AuthorizationException("No puedes pagar una orden ajena");
        }

        if (paymentRepositoryPort.findByOrderId(command.orderId()).isPresent()) {
            throw new IllegalArgumentException("Ya existe un pago para esta orden");
        }

        BigDecimal amount = computeAmount(order);
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("Monto inválido para la orden");
        }

        String description = "Pago Nomi - Orden #" + order.getId();

        PaymentGatewayPort.PaymentRequest request = new PaymentGatewayPort.PaymentRequest(
                order.getId(),
                order.getUserId(),
                amount,
                description,
                notificationUrl
        );

        PaymentGatewayPort.PaymentResponse response = paymentGatewayPort.createPayment(request);

        Payment payment = Payment.builder()
                .orderId(order.getId())
                .userId(order.getUserId())
                .amount(amount)
                .status(PaymentStatus.PENDIENTE)
                .externalId(response.externalId())
                .paymentUrl(response.paymentUrl())
                .creadoEn(LocalDateTime.now())
                .actualizadoEn(LocalDateTime.now())
                .build();

        return paymentRepositoryPort.save(payment);
    }

    private BigDecimal computeAmount(Order order) {
        BigDecimal total = order.getTotal() == null ? BigDecimal.ZERO : order.getTotal();
        BigDecimal propina = order.getPropina() == null ? BigDecimal.ZERO : order.getPropina();
        BigDecimal tarifa = order.getTarifaServicio() == null ? BigDecimal.ZERO : order.getTarifaServicio();
        BigDecimal comision = order.getComisionNomi() == null ? BigDecimal.ZERO : order.getComisionNomi();
        return total.add(propina).add(tarifa).add(comision);
    }
}
