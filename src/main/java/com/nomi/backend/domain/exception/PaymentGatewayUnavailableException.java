package com.nomi.backend.domain.exception;

/**
 * La pasarela de pagos no respondió o respondió con un error ({@code 503}).
 *
 * <p>Se lanza en lugar de suponer un estado: tomar un fallo de consulta como "pago pendiente"
 * llevaría a cancelar pedidos que sí están pagados. Quien la recibe no debe cambiar el pedido: el
 * webhook responde error para que MercadoPago reintente, y la cancelación o la caducidad se
 * reintentan más tarde.
 */
public class PaymentGatewayUnavailableException extends RuntimeException {

    public PaymentGatewayUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
