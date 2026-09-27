package com.nomi.backend.application.store;

import com.nomi.backend.domain.model.store.Store;
import com.nomi.backend.domain.port.in.store.FindStoreUseCase;
import com.nomi.backend.domain.port.out.StoreRepositoryPort;
import com.nomi.backend.domain.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Consulta de tiendas.
 */
@Service
@RequiredArgsConstructor
public class FindStoreHandler implements FindStoreUseCase {

    private final StoreRepositoryPort storeRepositoryPort;

    @Override
    public Store findById(Long id) {
        return storeRepositoryPort.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Tienda no encontrada"));
    }

    @Override
    public Store findByOwnerId(Long ownerId) {
        return storeRepositoryPort.findByOwnerId(ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("Tienda no encontrada"));
    }

    @Override
    public List<Store> findAll() {
        return storeRepositoryPort.findAll();
    }

    @Override
    public List<Store> findAllActivas() {
        return storeRepositoryPort.findAllActivas();
    }
}
