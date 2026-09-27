package com.foodv.backend.domain.port.out;

/**
 * Contador de intentos fallidos de login por email, para bloquear la fuerza bruta.
 */
public interface LoginAttemptPort {

    boolean isBlocked(String email);

    void recordFailedAttempt(String email);

    void resetAttempts(String email);
}
