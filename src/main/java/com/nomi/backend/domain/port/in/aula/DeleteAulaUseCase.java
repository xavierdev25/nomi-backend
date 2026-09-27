package com.nomi.backend.domain.port.in.aula;

/**
 * Baja de un aula (solo administradores).
 */
public interface DeleteAulaUseCase {

    void execute(Long id);
}
