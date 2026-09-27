package com.foodv.backend.infrastructure.web.dto.auth;

import com.foodv.backend.domain.model.user.BudgetRange;
import com.foodv.backend.domain.model.user.DietaryRestriction;
import com.foodv.backend.domain.model.user.UserRole;
import jakarta.validation.constraints.*;

import java.util.List;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Datos de registro. El email se guarda normalizado (sin espacios y en minúsculas).")
public record RegisterRequest(
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
        @Size(max = 120)
        @Schema(description = "Email único", example = "ana@ucv.edu.pe")
        String email,

        @NotBlank(message = "Password obligatorio")
        @Size(min = 8, max = 72, message = "La contraseña debe tener entre 8 y 72 caracteres")
        @Pattern(
                regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).+$",
                message = "La contraseña debe tener al menos una mayúscula, una minúscula y un número"
        )
        @Schema(description = "8 a 72 caracteres, con al menos una mayúscula, una minúscula y un número", example = "Segura123")
        String password,

        @Pattern(regexp = "^[+]?[0-9\\s-]{6,20}$", message = "Teléfono inválido")
        @Schema(description = "Opcional: 6 a 20 dígitos, espacios o guiones, con + inicial opcional", example = "+51 987 654 321")
        String telefono,

        @NotNull(message = "Rol obligatorio")
        @Schema(description = "ESTUDIANTE, REPARTIDOR o COMERCIO; ADMIN no se puede auto-registrar", example = "ESTUDIANTE")
        UserRole role,

        @Schema(description = "Gustos de comida, para las recomendaciones")
        List<String> preferences,
        @Schema(description = "Restricciones alimentarias: VEGETARIANO, VEGANO, SIN_GLUTEN, SIN_LACTOSA o NINGUNA. Solo se recomiendan productos etiquetados como aptos", example = "[\"VEGETARIANO\"]")
        List<@Pattern(regexp = DietaryRestriction.PATTERN, message = "Restricción no válida: usa VEGETARIANO, VEGANO, SIN_GLUTEN, SIN_LACTOSA o NINGUNA") String> restrictions,
        @Schema(description = "Presupuesto; MEDIO si se omite", example = "MEDIO")
        BudgetRange budgetRange,
        @Schema(description = "Tipos de cocina preferidos")
        List<String> cuisineTypes
) {
        public RegisterRequest {
                if (preferences == null) preferences = List.of();
                if (restrictions == null) restrictions = List.of();
                if (budgetRange == null) budgetRange = BudgetRange.MEDIO;
                if (cuisineTypes == null) cuisineTypes = List.of();
        }
}
