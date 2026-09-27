package com.nomi.backend.domain.port.in.user;

/**
 * Cambio de contraseña. Cierra todas las sesiones del usuario.
 */
public interface ChangePasswordUseCase {
    void execute(String email, String currentPassword, String newPassword);
}
