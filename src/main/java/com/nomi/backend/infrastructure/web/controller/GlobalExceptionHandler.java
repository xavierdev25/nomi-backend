package com.nomi.backend.infrastructure.web.controller;

import com.nomi.backend.domain.exception.AuthorizationException;
import com.nomi.backend.domain.exception.AuthenticationFailedException;
import com.nomi.backend.domain.exception.PaymentGatewayUnavailableException;
import com.nomi.backend.domain.exception.ResourceNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Traduce las excepciones a respuestas JSON {@code {timestamp, status, error, message}} (más
 * {@code fields} en errores de validación). Nunca expone trazas ni detalles internos.
 *
 * <p>{@code IllegalArgumentException} → 400, {@code IllegalStateException} → 409,
 * {@code ResourceNotFoundException} → 404, {@code AuthorizationException} y
 * {@code AccessDeniedException} → 403, {@code PaymentGatewayUnavailableException} → 503, y
 * cualquier otra → 500 con mensaje genérico.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleResourceNotFound(ResourceNotFoundException ex) {
        return build(HttpStatus.NOT_FOUND, "No encontrado", safeMessage(ex, "Recurso no encontrado"));
    }

    @ExceptionHandler(AuthorizationException.class)
    public ResponseEntity<Map<String, Object>> handleAuthorization(AuthorizationException ex) {
        return build(HttpStatus.FORBIDDEN, "Acceso denegado",
                safeMessage(ex, "No tienes permisos para realizar esta acción"));
    }

    /**
     * Forma distinta al resto ({@code {"error": "Invalid credentials"}}); los clientes existentes
     * dependen de ella.
     */
    @ExceptionHandler(AuthenticationFailedException.class)
    public ResponseEntity<Map<String, String>> handleAuthenticationFailed(AuthenticationFailedException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("error", "Invalid credentials"));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleBadRequest(IllegalArgumentException ex) {
        return build(HttpStatus.BAD_REQUEST, "Solicitud inválida", safeMessage(ex, "Solicitud inválida"));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalState(IllegalStateException ex) {
        log.warn("Estado inválido: {}", ex.getMessage());
        return build(HttpStatus.CONFLICT, "Conflicto", safeMessage(ex, "Operación no permitida en este estado"));
    }

    /**
     * 503 y no 500: es transitorio y el cliente debe reintentar. En el webhook, cualquier respuesta
     * que no sea 2xx hace que MercadoPago reenvíe la notificación.
     */
    @ExceptionHandler(PaymentGatewayUnavailableException.class)
    public ResponseEntity<Map<String, Object>> handlePaymentGatewayUnavailable(PaymentGatewayUnavailableException ex) {
        log.warn("MercadoPago no disponible: {}", ex.getMessage());
        return build(HttpStatus.SERVICE_UNAVAILABLE, "Pagos no disponibles",
                "No pudimos confirmar el estado de tu pago. Inténtalo en unos segundos.");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            errors.put(error.getField(),
                    error.getDefaultMessage() != null ? error.getDefaultMessage() : "Valor inválido");
        }
        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", LocalDateTime.now());
        body.put("status", HttpStatus.BAD_REQUEST.value());
        body.put("error", "Error de validación");
        body.put("message", "Hay errores en los campos enviados");
        body.put("fields", errors);
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleUnreadable(HttpMessageNotReadableException ex) {
        return build(HttpStatus.BAD_REQUEST, "JSON inválido",
                "El cuerpo de la petición no tiene el formato correcto");
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Map<String, Object>> handleMissingParam(MissingServletRequestParameterException ex) {
        return build(HttpStatus.BAD_REQUEST, "Parámetro faltante",
                "El parámetro '" + ex.getParameterName() + "' es requerido");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String requiredType = ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "valor válido";
        return build(HttpStatus.BAD_REQUEST, "Tipo de parámetro inválido",
                "El parámetro '" + ex.getName() + "' debe ser de tipo " + requiredType);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Map<String, Object>> handleUnauthorized(AuthenticationException ex) {
        return build(HttpStatus.UNAUTHORIZED, "No autenticado",
                "Debes iniciar sesión para acceder a este recurso");
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleForbidden(AccessDeniedException ex) {
        return build(HttpStatus.FORBIDDEN, "Acceso denegado",
                safeMessage(ex, "No tienes permisos para realizar esta acción"));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> handleDataIntegrity(DataIntegrityViolationException ex) {
        log.warn("Violación de integridad: {}", ex.getMostSpecificCause().getMessage());
        return build(HttpStatus.CONFLICT, "Conflicto de datos",
                "Ya existe un registro con esos datos o se viola una restricción de integridad");
    }

    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<Map<String, Object>> handleSecurity(SecurityException ex) {
        log.warn("Violación de seguridad: {}", ex.getMessage());
        return build(HttpStatus.FORBIDDEN, "Operación no permitida",
                safeMessage(ex, "Operación no permitida"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneric(Exception ex) {
        log.error("Error no manejado en la API", ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Error interno del servidor",
                "Ocurrió un error inesperado. Por favor intenta de nuevo.");
    }

    private static String safeMessage(Throwable ex, String fallback) {
        return (ex != null && ex.getMessage() != null && !ex.getMessage().isBlank())
                ? ex.getMessage() : fallback;
    }

    private ResponseEntity<Map<String, Object>> build(HttpStatus status, String error, String message) {
        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", LocalDateTime.now());
        body.put("status", status.value());
        body.put("error", error);
        body.put("message", message);
        return ResponseEntity.status(status).body(body);
    }
}
