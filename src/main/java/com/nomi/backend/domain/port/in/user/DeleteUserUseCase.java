package com.nomi.backend.domain.port.in.user;

/**
 * Borrado lógico de un usuario; cierra sus sesiones.
 */
public interface DeleteUserUseCase {

    void execute(Long id);
}
