package com.foodv.backend.infrastructure.web.dto.store;

import java.time.LocalDateTime;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Tienda del campus.")
public record StoreResponse(
        @Schema(description = "Id de la tienda", example = "1")
        Long id,
        @Schema(description = "Nombre", example = "Sabores UCV")
        String nombre,
        @Schema(description = "Descripción")
        String descripcion,
        @Schema(description = "URL de la imagen")
        String imagenUrl,
        @Schema(description = "Teléfono de contacto")
        String telefono,
        @Schema(description = "Usuario COMERCIO dueño", example = "3")
        Long ownerId,
        @Schema(description = "Si está habilitada en la plataforma; no refleja el horario", example = "true")
        boolean activo,
        @Schema(description = "Fecha de alta (hora local del servidor, sin zona)")
        LocalDateTime creadoEn,
        @Schema(description = "Hora de apertura, HH:mm", example = "08:00")
        String horarioApertura,
        @Schema(description = "Hora de cierre, HH:mm", example = "20:00")
        String horarioCierre
) {}
