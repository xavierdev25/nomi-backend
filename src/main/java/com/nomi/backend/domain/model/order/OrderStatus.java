package com.nomi.backend.domain.model.order;

import java.util.Set;

/**
 * Estados del pedido y sus transiciones válidas:
 * {@code PENDIENTE → PREPARANDO → LISTO_PARA_RECOGER → EN_CAMINO → ENTREGADO}, y
 * {@code PENDIENTE → CANCELADO}. {@code ENTREGADO} y {@code CANCELADO} son finales.
 */
public enum OrderStatus {
    PENDIENTE,
    PREPARANDO,
    LISTO_PARA_RECOGER,
    EN_CAMINO,
    ENTREGADO,
    CANCELADO;

    /**
     * Única fuente de verdad de la máquina de estados del pedido.
     */
    public boolean canTransitionTo(OrderStatus nextStatus) {
        return switch (this) {
            case PENDIENTE -> Set.of(PREPARANDO, CANCELADO).contains(nextStatus);
            case PREPARANDO -> nextStatus == LISTO_PARA_RECOGER;
            case LISTO_PARA_RECOGER -> nextStatus == EN_CAMINO;
            case EN_CAMINO -> nextStatus == ENTREGADO;
            case ENTREGADO, CANCELADO -> false;
        };
    }

    /**
     * Nombre legible para mensajes al usuario.
     */
    public String enEspanol() {
        return switch (this) {
            case PENDIENTE -> "Pendiente";
            case PREPARANDO -> "En preparación";
            case LISTO_PARA_RECOGER -> "Listo para recoger";
            case EN_CAMINO -> "En camino";
            case ENTREGADO -> "Entregado";
            case CANCELADO -> "Cancelado";
        };
    }
}
