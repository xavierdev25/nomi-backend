package com.nomi.backend.infrastructure.web.controller;

import com.nomi.backend.domain.port.in.auth.LoginUseCase;
import com.nomi.backend.domain.port.in.auth.LogoutUseCase;
import com.nomi.backend.domain.port.in.auth.RefreshTokenUseCase;
import com.nomi.backend.domain.port.in.auth.RegisterUseCase;
import com.nomi.backend.infrastructure.web.dto.auth.AuthResponse;
import com.nomi.backend.infrastructure.web.dto.auth.LoginRequest;
import com.nomi.backend.infrastructure.web.dto.auth.RegisterRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Registro, login, renovación de tokens y logout.
 */
@Tag(name = "Autenticación")
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final LoginUseCase loginUseCase;
    private final RegisterUseCase registerUseCase;
    private final RefreshTokenUseCase refreshTokenUseCase;
    private final LogoutUseCase logoutUseCase;

    public AuthController(LoginUseCase loginUseCase,
                          RegisterUseCase registerUseCase,
                          RefreshTokenUseCase refreshTokenUseCase,
                          LogoutUseCase logoutUseCase) {
        this.loginUseCase = loginUseCase;
        this.registerUseCase = registerUseCase;
        this.refreshTokenUseCase = refreshTokenUseCase;
        this.logoutUseCase = logoutUseCase;
    }

    @Operation(summary = "Iniciar sesión")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Login exitoso"),
            @ApiResponse(responseCode = "401", description = "Credenciales inválidas"),
            @ApiResponse(responseCode = "429", description = "Demasiados intentos fallidos")
    })
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        LoginUseCase.LoginResult result = loginUseCase.execute(
                new LoginUseCase.LoginCommand(request.email(), request.password())
        );
        return ResponseEntity.ok(toResponse(result));
    }

    @Operation(summary = "Registro público (sólo ESTUDIANTE/REPARTIDOR/COMERCIO)")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Usuario registrado"),
            @ApiResponse(responseCode = "400", description = "Datos inválidos"),
            @ApiResponse(responseCode = "409", description = "Email ya registrado")
    })
    @PostMapping("/register")
    public ResponseEntity<Map<String, String>> register(@Valid @RequestBody RegisterRequest request) {
        registerUseCase.execute(new RegisterUseCase.RegisterCommand(
                request.nombres(),
                request.apellidos(),
                request.email(),
                request.password(),
                request.telefono(),
                request.role(),
                request.preferences(),
                request.restrictions(),
                request.budgetRange(),
                request.cuisineTypes()
        ));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("message", "Usuario registrado exitosamente"));
    }

    @Operation(summary = "Renovar access token (rota refresh token)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Token renovado"),
            @ApiResponse(responseCode = "400", description = "Refresh token inválido o expirado")
    })
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshRequest body) {
        LoginUseCase.LoginResult result = refreshTokenUseCase.execute(body.refreshToken());
        return ResponseEntity.ok(toResponse(result));
    }

    @Operation(summary = "Cerrar sesión (revoca refresh tokens y blacklistea access token)")
    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(
            @RequestBody(required = false) LogoutRequest body,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        String accessToken = (authHeader != null && authHeader.startsWith("Bearer "))
                ? authHeader.substring(7) : null;
        String refreshToken = body != null ? body.refreshToken() : null;

        logoutUseCase.execute(new LogoutUseCase.LogoutCommand(accessToken, refreshToken));

        return ResponseEntity.ok(Map.of("message", "Sesión cerrada exitosamente"));
    }

    private AuthResponse toResponse(LoginUseCase.LoginResult result) {
        return new AuthResponse(
                result.accessToken(),
                result.refreshToken(),
                result.tokenType(),
                result.expiresIn()
        );
    }

    @Schema(description = "Renovación de sesión.")
    public record RefreshRequest(
            @Schema(description = "Refresh token vigente; queda revocado al usarse")
            @NotBlank(message = "refreshToken obligatorio") String refreshToken
    ) {}

    @Schema(description = "Cierre de sesión. El cuerpo es opcional.")
    public record LogoutRequest(
            @Schema(description = "Refresh token a revocar; de todos modos se revocan todos los del usuario")
            String refreshToken
    ) {}
}
