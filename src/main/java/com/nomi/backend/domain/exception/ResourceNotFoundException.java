package com.nomi.backend.domain.exception;

/**
 * El recurso pedido no existe o está borrado lógicamente ({@code 404}).
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
