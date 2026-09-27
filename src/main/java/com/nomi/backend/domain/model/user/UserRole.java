package com.nomi.backend.domain.model.user;

/**
 * Roles. {@code ADMIN} no se puede auto-registrar; solo lo crea otro administrador.
 */
public enum UserRole {
    ESTUDIANTE,
    REPARTIDOR,
    COMERCIO,
    ADMIN
}
