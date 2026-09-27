package com.nomi.backend.domain.port.out;

/**
 * Generación de valores aleatorios criptográficamente seguros.
 */
public interface SecureRandomPort {

    /**
     * Código numérico; longitudes fuera de 1..12 se sustituyen por 4.
     */
    String generateConfirmationCode(int length);

    String generateRandomString(int length);
}
