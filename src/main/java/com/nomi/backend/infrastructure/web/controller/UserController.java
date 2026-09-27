package com.nomi.backend.infrastructure.web.controller;

import com.nomi.backend.domain.common.PageQuery;
import com.nomi.backend.domain.model.user.User;
import com.nomi.backend.domain.model.user.UserRole;
import com.nomi.backend.domain.port.in.user.ChangePasswordUseCase;
import com.nomi.backend.domain.port.in.user.CreateUserUseCase;
import com.nomi.backend.domain.port.in.user.DeleteUserUseCase;
import com.nomi.backend.domain.port.in.user.FindUserUseCase;
import com.nomi.backend.domain.port.in.user.UpdateUserUseCase;
import com.nomi.backend.infrastructure.persistence.adapter.UserEntityMapper;
import com.nomi.backend.infrastructure.persistence.repository.UserJpaRepository;
import com.nomi.backend.infrastructure.security.AuthenticatedUserResolver;
import com.nomi.backend.infrastructure.security.OwnershipService;
import com.nomi.backend.infrastructure.web.dto.common.PageResponse;
import com.nomi.backend.infrastructure.web.dto.user.ChangePasswordRequest;
import com.nomi.backend.infrastructure.web.dto.user.CreateUserRequest;
import com.nomi.backend.infrastructure.web.dto.user.UpdateUserRequest;
import com.nomi.backend.infrastructure.web.dto.user.UserResponse;
import com.nomi.backend.infrastructure.web.mapper.UserWebMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Perfil del usuario autenticado y gestión de usuarios por administradores.
 */
@Tag(name = "Usuarios")
@RestController
@RequestMapping("/users")
public class UserController {

    private final CreateUserUseCase createUserUseCase;
    private final FindUserUseCase findUserUseCase;
    private final UpdateUserUseCase updateUserUseCase;
    private final DeleteUserUseCase deleteUserUseCase;
    private final ChangePasswordUseCase changePasswordUseCase;
    private final UserWebMapper mapper;
    private final UserEntityMapper userEntityMapper;
    private final UserJpaRepository userJpaRepository;
    private final AuthenticatedUserResolver currentUser;
    private final OwnershipService ownershipService;

    public UserController(CreateUserUseCase createUserUseCase,
                          FindUserUseCase findUserUseCase,
                          UpdateUserUseCase updateUserUseCase,
                          DeleteUserUseCase deleteUserUseCase,
                          ChangePasswordUseCase changePasswordUseCase,
                          UserWebMapper mapper,
                          UserEntityMapper userEntityMapper,
                          UserJpaRepository userJpaRepository,
                          AuthenticatedUserResolver currentUser,
                          OwnershipService ownershipService) {
        this.createUserUseCase = createUserUseCase;
        this.findUserUseCase = findUserUseCase;
        this.updateUserUseCase = updateUserUseCase;
        this.deleteUserUseCase = deleteUserUseCase;
        this.changePasswordUseCase = changePasswordUseCase;
        this.mapper = mapper;
        this.userEntityMapper = userEntityMapper;
        this.userJpaRepository = userJpaRepository;
        this.currentUser = currentUser;
        this.ownershipService = ownershipService;
    }

    @Operation(summary = "Crear usuario (sólo ADMIN)")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Usuario creado"),
            @ApiResponse(responseCode = "400", description = "Datos inválidos"),
            @ApiResponse(responseCode = "403", description = "Acceso denegado"),
            @ApiResponse(responseCode = "409", description = "Email ya registrado")
    })
    @PostMapping
    public ResponseEntity<UserResponse> create(@Valid @RequestBody CreateUserRequest request) {
        User user = createUserUseCase.execute(mapper.toCommand(request));
        return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toResponse(user));
    }

    @Operation(summary = "Listar usuarios paginado (sólo ADMIN)")
    @GetMapping
    public ResponseEntity<PageResponse<UserResponse>> findAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "id") String sortBy) {
        return ResponseEntity.ok(PageResponse.from(
                findUserUseCase.findAllPaginated(new PageQuery(page, size, sortBy, true))
                        .map(mapper::toResponse)
        ));
    }

    @Operation(summary = "Obtener usuario (sólo el propio o ADMIN)")
    @GetMapping("/{id}")
    public ResponseEntity<UserResponse> findById(@PathVariable Long id) {
        ownershipService.requireSelfOrAdmin(currentUser.currentUserSummary(), id);
        return ResponseEntity.ok(mapper.toResponse(findUserUseCase.findById(id)));
    }

    @Operation(summary = "Actualizar usuario (sólo el propio o ADMIN)")
    @PutMapping("/{id}")
    public ResponseEntity<UserResponse> update(@PathVariable Long id,
                                               @Valid @RequestBody UpdateUserRequest request) {
        ownershipService.requireSelfOrAdmin(currentUser.currentUserSummary(), id);
        return ResponseEntity.ok(mapper.toResponse(updateUserUseCase.execute(id, mapper.toCommand(request))));
    }

    @Operation(summary = "Eliminar usuario (sólo el propio o ADMIN)")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        ownershipService.requireSelfOrAdmin(currentUser.currentUserSummary(), id);
        deleteUserUseCase.execute(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Devuelve los datos del token, no el registro completo (ver
     * {@code AuthenticatedUserResolver.currentUserSummary}).
     */
    @Operation(summary = "Mi perfil")
    @GetMapping("/me")
    public ResponseEntity<UserResponse> getMe() {
        return ResponseEntity.ok(mapper.toResponse(currentUser.currentUserSummary()));
    }

    @Operation(summary = "Actualizar mi perfil")
    @PutMapping("/me")
    public ResponseEntity<UserResponse> updateMe(@Valid @RequestBody UpdateUserRequest request) {
        Long id = currentUser.currentUserId();
        return ResponseEntity.ok(mapper.toResponse(updateUserUseCase.execute(id, mapper.toCommand(request))));
    }

    @Operation(summary = "Cambiar mi contraseña (revoca todas las sesiones)")
    @PutMapping("/me/password")
    public ResponseEntity<Map<String, String>> changePassword(@RequestBody @Valid ChangePasswordRequest request) {
        String email = currentUser.currentEmail();
        changePasswordUseCase.execute(email, request.currentPassword(), request.newPassword());
        return ResponseEntity.ok(Map.of("message", "Contraseña actualizada exitosamente. Por seguridad debes iniciar sesión nuevamente."));
    }

    @GetMapping("/deleted")
    @Operation(summary = "Listar usuarios eliminados (sólo ADMIN)")
    public ResponseEntity<List<UserResponse>> findDeleted() {
        if (currentUser.currentRole() != UserRole.ADMIN) {
            throw new AccessDeniedException("Sólo ADMIN puede ver usuarios eliminados");
        }
        return ResponseEntity.ok(
                userJpaRepository.findAllByDeletedAtIsNotNull().stream()
                        .map(userEntityMapper::toDomain)
                        .map(mapper::toResponse)
                        .toList()
        );
    }

    @PostMapping("/{id}/restore")
    @Operation(summary = "Restaurar usuario eliminado (sólo ADMIN)")
    public ResponseEntity<Map<String, String>> restore(@PathVariable Long id) {
        if (currentUser.currentRole() != UserRole.ADMIN) {
            throw new AccessDeniedException("Sólo ADMIN puede restaurar usuarios");
        }
        userJpaRepository.findById(id).ifPresent(user -> {
            user.setDeletedAt(null);
            userJpaRepository.save(user);
        });
        return ResponseEntity.ok(Map.of("message", "Usuario restaurado exitosamente"));
    }
}
