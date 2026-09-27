package com.nomi.backend.domain.port.in.aula;

import com.nomi.backend.domain.model.aula.Aula;

/**
 * Alta de un aula (solo administradores).
 */
public interface CreateAulaUseCase {

    record CreateAulaCommand(String codigo, String nombre, String piso, String pabellon) {}

    Aula execute(CreateAulaCommand command);
}
