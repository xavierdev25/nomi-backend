package com.nomi.backend.domain.port.in.aula;

import com.nomi.backend.domain.model.aula.Aula;

import java.util.List;

/**
 * Consulta de aulas.
 */
public interface FindAulaUseCase {

    Aula findById(Long id);

    List<Aula> findAll();

    List<Aula> findAllActivas();
}
