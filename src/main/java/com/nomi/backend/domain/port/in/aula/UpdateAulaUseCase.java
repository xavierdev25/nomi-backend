package com.nomi.backend.domain.port.in.aula;

import com.nomi.backend.domain.model.aula.Aula;

/**
 * Edición de un aula; el código no cambia.
 */
public interface UpdateAulaUseCase {

    record UpdateAulaCommand(String nombre, String piso, String pabellon) {}

    Aula execute(Long id, UpdateAulaCommand command);
}
