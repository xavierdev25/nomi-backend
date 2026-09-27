package com.foodv.backend.domain.port.out;

/**
 * Revocación de access tokens antes de su expiración.
 */
public interface TokenBlacklistPort {

    /**
     * Revoca un access token concreto (logout). Se guarda solo lo que le queda de vida.
     */
    void blacklist(String token, long expirationMillis);

    boolean isBlacklisted(String token);

    /**
     * Invalida todos los tokens del usuario emitidos antes de {@code timestampMillis} (cambio de
     * contraseña, baja).
     */
    void invalidateAllSessionsBefore(Long userId, long timestampMillis);

    boolean isUserSessionInvalidated(Long userId, long tokenIssuedAtMillis);
}
