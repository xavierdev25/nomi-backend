package com.nomi.backend.domain.port.in.product;

/**
 * Borrado lógico de un producto. Devuelve el id de su tienda para invalidar la caché.
 */
public interface DeleteProductUseCase {

    Long execute(Long id);
}
