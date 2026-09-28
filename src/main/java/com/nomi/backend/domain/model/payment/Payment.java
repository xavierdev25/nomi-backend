package com.nomi.backend.domain.model.payment;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Pago de un pedido en MercadoPago. Hay como máximo uno por pedido. {@code externalId} es el
 * id de la preferencia (el checkout) y {@code paymentUrl} su URL. {@code externalReference} es la
 * referencia única del checkout ({@link CheckoutReference}); {@code null} en los antiguos.
 * {@code gatewayPaymentId} es el id del pago de MercadoPago que decide el estado: el último
 * intento mientras no hay uno aprobado, y el aprobado a partir de entonces.
 */
@Getter
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Payment {

    @EqualsAndHashCode.Include
    private Long id;

    private Long orderId;
    private Long userId;
    private BigDecimal amount;
    private PaymentStatus status;
    private String externalId;
    private String externalReference;
    private String gatewayPaymentId;
    private String paymentUrl;
    private String failureReason;
    private LocalDateTime creadoEn;
    private LocalDateTime actualizadoEn;

    /** Referencia con la que se buscan sus pagos en la pasarela; los checkouts antiguos usaban el id del pedido. */
    public String gatewayReference() {
        return externalReference != null ? externalReference : String.valueOf(orderId);
    }

    /** Si ya hay un pago aprobado (o reembolsado) que decide el estado. */
    public boolean isSettled() {
        return status == PaymentStatus.APROBADO || status == PaymentStatus.REEMBOLSADO;
    }

    /**
     * Copia con lo que informa la pasarela sobre el pago {@code gatewayId}, o este mismo pago si
     * el aviso no puede cambiarlo: una vez aprobado, solo los avisos de ese mismo pago cuentan, y
     * solo para pasar a reembolsado (ver {@link PaymentStatus#canBeReplacedBy}).
     *
     * @param reason motivo a guardar (rechazo, reembolso); {@code null} conserva el anterior
     */
    public Payment withGatewayStatus(String gatewayId, PaymentStatus next, String reason, LocalDateTime now) {
        boolean otherAttempt = isSettled() && gatewayPaymentId != null && !gatewayPaymentId.equals(gatewayId);
        if (otherAttempt || !status.canBeReplacedBy(next)) {
            return this;
        }
        if (next == status && gatewayId != null && gatewayId.equals(gatewayPaymentId)) {
            return this;
        }
        return Payment.builder()
                .id(id)
                .orderId(orderId)
                .userId(userId)
                .amount(amount)
                .status(next)
                .externalId(externalId)
                .externalReference(externalReference)
                .gatewayPaymentId(gatewayId != null ? gatewayId : gatewayPaymentId)
                .paymentUrl(paymentUrl)
                .failureReason(reason != null ? reason : failureReason)
                .creadoEn(creadoEn)
                .actualizadoEn(now)
                .build();
    }
}
