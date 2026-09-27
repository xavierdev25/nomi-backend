package com.nomi.backend.domain.port.in.user;

import com.nomi.backend.domain.model.user.User;

/**
 * Edición de nombre, apellidos y teléfono.
 */
public interface UpdateUserUseCase {

    User execute(Long id, UpdateUserCommand command);

    record UpdateUserCommand(
            String nombres,
            String apellidos,
            String telefono
    ) {}
}
