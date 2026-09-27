package com.nomi.backend.domain.exception;

/**
 * Violación de una regla de negocio. Hoy no se lanza en ningún lugar: las reglas usan
 * {@link IllegalArgumentException} ({@code 400}) e {@link IllegalStateException} ({@code 409}).
 */
public class BusinessRuleViolationException extends RuntimeException {

    public BusinessRuleViolationException(String message) {
        super(message);
    }
}
