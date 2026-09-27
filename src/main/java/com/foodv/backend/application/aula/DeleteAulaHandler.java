package com.foodv.backend.application.aula;

import com.foodv.backend.domain.port.in.aula.DeleteAulaUseCase;
import com.foodv.backend.domain.port.out.AulaRepositoryPort;
import com.foodv.backend.domain.exception.ResourceNotFoundException;
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
