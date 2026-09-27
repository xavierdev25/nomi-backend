package com.nomi.backend.application.aula;

import com.nomi.backend.domain.model.aula.Aula;
import com.nomi.backend.domain.port.in.aula.UpdateAulaUseCase;
import com.nomi.backend.domain.port.out.AulaRepositoryPort;
import com.nomi.backend.domain.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Actualiza nombre, piso y pabellón; conserva código y estado.
 */
@Service
@RequiredArgsConstructor
public class UpdateAulaHandler implements UpdateAulaUseCase {

    private final AulaRepositoryPort aulaRepositoryPort;

    @Override
    public Aula execute(Long id, UpdateAulaCommand command) {
        Aula existing = aulaRepositoryPort.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Aula no encontrada"));

        Aula aula = Aula.builder()
                .id(existing.getId())
                .codigo(existing.getCodigo())
                .nombre(command.nombre())
                .piso(command.piso())
                .pabellon(command.pabellon())
                .activo(existing.isActivo())
                .build();

        return aulaRepositoryPort.save(aula);
    }
}
