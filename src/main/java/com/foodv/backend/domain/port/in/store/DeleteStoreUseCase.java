package com.foodv.backend.domain.port.in.store;

/**
 * Borrado lógico de una tienda.
 */
public interface DeleteStoreUseCase {

    void execute(Long id);
}
