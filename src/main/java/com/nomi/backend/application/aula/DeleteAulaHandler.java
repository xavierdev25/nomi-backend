package com.nomi.backend.application.aula;

import com.nomi.backend.domain.port.in.aula.DeleteAulaUseCase;
import com.nomi.backend.domain.port.out.AulaRepositoryPort;
import com.nomi.backend.domain.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Elimina un aula.
 */
@Service
@RequiredArgsConstructor
public class DeleteAulaHandler implements DeleteAulaUseCase {

    private final AulaRepositoryPort aulaRepositoryPort;

    @Override
    public void execute(Long id) {
        aulaRepositoryPort.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Aula no encontrada"));

        aulaRepositoryPort.deleteById(id);
    }
}
