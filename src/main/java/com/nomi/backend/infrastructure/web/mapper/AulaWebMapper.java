package com.nomi.backend.infrastructure.web.mapper;

import com.nomi.backend.domain.model.aula.Aula;
import com.nomi.backend.domain.port.in.aula.CreateAulaUseCase;
import com.nomi.backend.domain.port.in.aula.UpdateAulaUseCase;
import com.nomi.backend.infrastructure.web.dto.aula.AulaResponse;
import com.nomi.backend.infrastructure.web.dto.aula.CreateAulaRequest;
import com.nomi.backend.infrastructure.web.dto.aula.UpdateAulaRequest;
import org.mapstruct.Mapper;

/**
 * Mapeo MapStruct entre DTOs web y el dominio de aulas.
 */
@Mapper(componentModel = "spring")
public interface AulaWebMapper {

    AulaResponse toResponse(Aula aula);

    CreateAulaUseCase.CreateAulaCommand toCommand(CreateAulaRequest request);

    UpdateAulaUseCase.UpdateAulaCommand toCommand(UpdateAulaRequest request);
}
