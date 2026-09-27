package com.foodv.backend.application.store;

import com.foodv.backend.domain.model.store.Store;
import com.foodv.backend.domain.model.user.User;
import com.foodv.backend.domain.model.user.UserRole;
import com.foodv.backend.domain.port.in.store.CreateStoreUseCase;
import com.foodv.backend.domain.port.out.BusinessMetricsPort;
import com.foodv.backend.domain.port.out.StoreRepositoryPort;
import com.foodv.backend.domain.port.out.UserRepositoryPort;
import com.foodv.backend.domain.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Crea la tienda de un comercio o administrador; un usuario solo puede tener una tienda.
 */
@Service
@RequiredArgsConstructor
public class CreateStoreHandler implements CreateStoreUseCase {

    private final StoreRepositoryPort storeRepositoryPort;
    private final UserRepositoryPort userRepositoryPort;
    private final BusinessMetricsPort metricsPort;

    @Override
    @Transactional
    public Store execute(CreateStoreCommand command) {
        User owner = userRepositoryPort.findById(command.ownerId())
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));

        if (owner.getRole() != UserRole.COMERCIO && owner.getRole() != UserRole.ADMIN) {
            throw new IllegalArgumentException("El usuario debe tener rol COMERCIO o ADMIN para crear una tienda");
        }

        if (storeRepositoryPort.existsByOwnerId(command.ownerId())) {
            throw new IllegalArgumentException("El usuario ya tiene una tienda registrada");
        }

        Store store = Store.builder()
                .nombre(command.nombre() == null ? null : command.nombre().trim())
                .descripcion(command.descripcion() == null ? null : command.descripcion().trim())
                .telefono(command.telefono() == null ? null : command.telefono().trim())
                .ownerId(command.ownerId())
                .ownerRole(owner.getRole())
                .activo(true)
                .creadoEn(LocalDateTime.now())
                .build();

        Store saved = storeRepositoryPort.save(store);
        metricsPort.recordStoreCreated();
        return saved;
    }
}
