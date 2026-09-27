package com.foodv.backend.infrastructure.web.dto.user;

import com.foodv.backend.domain.model.user.BudgetRange;
import com.foodv.backend.domain.model.user.DietaryRestriction;
import com.foodv.backend.domain.model.user.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Alta de un usuario por un administrador; admite cualquier rol.")
public record CreateUserRequest(
        @NotBlank(message = "Nombres obligatorios")
        @Size(min = 2, max = 60, message = "Nombres entre 2 y 60 caracteres")
        @Pattern(regexp = "^[\\p{L} '-]+$", message = "Nombres con caracteres inválidos")
        @Schema(description = "Nombres: 2 a 60 letras, espacios, apóstrofo o guion", example = "Ana María")
        String nombres,

        @NotBlank(message = "Apellidos obligatorios")
        @Size(min = 2, max = 60, message = "Apellidos entre 2 y 60 caracteres")
        @Pattern(regexp = "^[\\p{L} '-]+$", message = "Apellidos con caracteres inválidos")
        @Schema(description = "Apellidos: 2 a 60 letras, espacios, apóstrofo o guion", example = "Pérez López")
        String apellidos,

        @NotBlank(message = "Email obligatorio")
        @Email(message = "Email inválido")
        @Size(max = 120, message = "Email demasiado largo")
        @Schema(description = "Email único", example = "ana@ucv.edu.pe")
        String email,

        @NotBlank(message = "Password obligatorio")
        @Size(min = 8, max = 128, message = "Password entre 8 y 128 caracteres")
        @Pattern(
                regexp = "^(?=.*[A-Za-z])(?=.*\\d).{8,}$",
                message = "Password debe contener al menos una letra y un número"
        )
        @Schema(description = "8 a 128 caracteres con al menos una letra y un número")
        String password,

        @Pattern(regexp = "^[+]?[0-9\\s-]{6,20}$", message = "Teléfono inválido")
        @Schema(description = "Opcional: 6 a 20 dígitos, espacios o guiones, con + inicial opcional")
        String telefono,

        @NotNull(message = "Rol obligatorio")
        @Schema(description = "Rol, incluido ADMIN", example = "COMERCIO")
        UserRole role,

        @Schema(description = "Gustos de comida")
        List<String> preferences,
        @Schema(description = "Restricciones alimentarias: VEGETARIANO, VEGANO, SIN_GLUTEN, SIN_LACTOSA o NINGUNA")
        List<@Pattern(regexp = DietaryRestriction.PATTERN, message = "Restricción no válida: usa VEGETARIANO, VEGANO, SIN_GLUTEN, SIN_LACTOSA o NINGUNA") String> restrictions,
        @Schema(description = "Presupuesto; MEDIO si se omite")
        BudgetRange budgetRange,
        @Schema(description = "Tipos de cocina preferidos")
        List<String> cuisineTypes
) {
        public CreateUserRequest {
                if (preferences == null) preferences = List.of();
                if (restrictions == null) restrictions = List.of();
                if (budgetRange == null) budgetRange = BudgetRange.MEDIO;
                if (cuisineTypes == null) cuisineTypes = List.of();
        }
}
