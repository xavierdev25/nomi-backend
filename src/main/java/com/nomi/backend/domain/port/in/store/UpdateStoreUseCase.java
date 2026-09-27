package com.nomi.backend.domain.port.in.store;

import com.nomi.backend.domain.model.store.Store;

/**
 * Edición parcial de una tienda: los campos {@code null} no cambian.
 */
public interface UpdateStoreUseCase {

    record UpdateStoreCommand(String nombre, String descripcion, String telefono) {}

    Store execute(Long id, UpdateStoreCommand command);
}
