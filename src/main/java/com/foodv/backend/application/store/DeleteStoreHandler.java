package com.foodv.backend.application.store;

import com.foodv.backend.domain.port.in.store.DeleteStoreUseCase;
import com.foodv.backend.domain.port.out.StoreRepositoryPort;
import com.foodv.backend.domain.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Borrado lógico de una tienda.
 */
@Service
@RequiredArgsConstructor
public class DeleteStoreHandler implements DeleteStoreUseCase {

    private final StoreRepositoryPort storeRepositoryPort;

    @Override
    public void execute(Long id) {
        storeRepositoryPort.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Tienda no encontrada"));

        storeRepositoryPort.deleteById(id);
    }
}
