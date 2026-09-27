package com.foodv.backend.infrastructure.web.mapper;

import com.foodv.backend.domain.model.store.Store;
import com.foodv.backend.domain.port.in.store.CreateStoreUseCase;
import com.foodv.backend.domain.port.in.store.UpdateStoreUseCase;
import com.foodv.backend.infrastructure.web.dto.store.CreateStoreRequest;
import com.foodv.backend.infrastructure.web.dto.store.StoreResponse;
import com.foodv.backend.infrastructure.web.dto.store.UpdateStoreRequest;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * Mapeo MapStruct entre DTOs web y el dominio de tiendas.
 */
@Mapper(componentModel = "spring")
public interface StoreWebMapper {

    DateTimeFormatter STORE_SCHEDULE_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    @Mapping(target = "horarioApertura", source = "horarioApertura", qualifiedByName = "formatSchedule")
    @Mapping(target = "horarioCierre", source = "horarioCierre", qualifiedByName = "formatSchedule")
    StoreResponse toResponse(Store store);

    UpdateStoreUseCase.UpdateStoreCommand toCommand(UpdateStoreRequest request);

    default CreateStoreUseCase.CreateStoreCommand toCommandWithOwner(CreateStoreRequest request, Long ownerId) {
        return new CreateStoreUseCase.CreateStoreCommand(
                request.nombre(),
                request.descripcion(),
                request.telefono(),
                ownerId
        );
    }

    @Named("formatSchedule")
    default String formatSchedule(LocalTime schedule) {
        return schedule == null ? null : schedule.format(STORE_SCHEDULE_FORMATTER);
    }
}
