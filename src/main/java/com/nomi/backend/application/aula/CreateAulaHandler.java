package com.nomi.backend.application.aula;

import com.nomi.backend.domain.model.aula.Aula;
import com.nomi.backend.domain.port.in.aula.CreateAulaUseCase;
import com.nomi.backend.domain.port.out.AulaRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Crea un aula activa; el código debe ser único.
 */
@Service
@RequiredArgsConstructor
public class CreateAulaHandler implements CreateAulaUseCase {

    private final AulaRepositoryPort aulaRepositoryPort;

    @Override
    public Aula execute(CreateAulaCommand command) {
        if (aulaRepositoryPort.existsByCodigo(command.codigo())) {
            throw new IllegalArgumentException("Código de aula ya registrado");
        }

        Aula aula = Aula.builder()
                .codigo(command.codigo())
                .nombre(command.nombre())
                .piso(command.piso())
                .pabellon(command.pabellon())
                .activo(true)
                .build();

        return aulaRepositoryPort.save(aula);
    }
}
