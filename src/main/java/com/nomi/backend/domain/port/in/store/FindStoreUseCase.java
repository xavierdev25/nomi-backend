package com.nomi.backend.domain.port.in.store;

import com.nomi.backend.domain.model.store.Store;

import java.util.List;

/**
 * Consulta de tiendas.
 */
public interface FindStoreUseCase {

    Store findById(Long id);

    Store findByOwnerId(Long ownerId);

    List<Store> findAll();

    List<Store> findAllActivas();
}
