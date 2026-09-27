package com.foodv.backend.domain.exception;

/**
 * Credenciales inválidas en el login. Se responde {@code 401} con un mensaje genérico para no
 * revelar si el email existe.
 */
public class AuthenticationFailedException extends RuntimeException {

    public AuthenticationFailedException(String message) {
        super(message);
    }
}
