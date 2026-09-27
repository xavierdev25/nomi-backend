package com.foodv.backend.infrastructure.web.dto.user;

import com.foodv.backend.domain.model.user.BudgetRange;
import com.foodv.backend.domain.model.user.UserRole;
import java.time.LocalDateTime;
import java.util.List;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Usuario. En GET /users/me solo llegan id, nombres, email, rol y activo: son los datos del token.")
public record UserResponse(
        @Schema(description = "Id del usuario", example = "5")
        Long id,
        @Schema(description = "Nombres", example = "Ana")
        String nombres,
        @Schema(description = "Apellidos")
        String apellidos,
        @Schema(description = "Email", example = "ana@ucv.edu.pe")
        String email,
        @Schema(description = "Teléfono")
        String telefono,
        @Schema(description = "Rol", example = "ESTUDIANTE")
        UserRole role,
        @Schema(description = "Si además hace entregas")
        Boolean esRepartidor,
        @Schema(description = "Campus al que pertenece")
        Long campusId,
        @Schema(description = "Si la cuenta está activa", example = "true")
        boolean activo,
        @Schema(description = "Fecha de registro (hora local del servidor, sin zona)")
        LocalDateTime creadoEn,
        @Schema(description = "Gustos de comida")
        List<String> preferences,
        @Schema(description = "Restricciones alimentarias")
        List<String> restrictions,
        @Schema(description = "Presupuesto", example = "MEDIO")
        BudgetRange budgetRange,
        @Schema(description = "Tipos de cocina preferidos")
        List<String> cuisineTypes
) {}
