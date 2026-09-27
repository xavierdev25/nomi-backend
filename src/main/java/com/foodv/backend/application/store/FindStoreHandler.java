package com.foodv.backend.application.store;

import com.foodv.backend.domain.model.store.Store;
import com.foodv.backend.domain.port.in.store.FindStoreUseCase;
import com.foodv.backend.domain.port.out.StoreRepositoryPort;
import com.foodv.backend.domain.exception.ResourceNotFoundException;
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
