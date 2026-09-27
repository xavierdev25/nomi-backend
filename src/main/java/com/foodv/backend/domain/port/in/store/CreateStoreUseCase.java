package com.foodv.backend.domain.port.in.store;

import com.foodv.backend.domain.model.store.Store;

/**
 * Alta de la tienda de un comercio.
 */
public interface CreateStoreUseCase {

    record CreateStoreCommand(String nombre, String descripcion, String telefono, Long ownerId) {}

    Store execute(CreateStoreCommand command);
}
