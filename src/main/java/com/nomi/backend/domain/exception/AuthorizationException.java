package com.nomi.backend.domain.exception;

/**
 * El usuario autenticado no tiene permiso sobre el recurso ({@code 403}).
 */
public class AuthorizationException extends RuntimeException {

    public AuthorizationException(String message) {
        super(message);
    }
}
