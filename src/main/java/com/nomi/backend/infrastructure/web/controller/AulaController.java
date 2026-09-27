package com.nomi.backend.infrastructure.web.controller;

import com.nomi.backend.domain.model.aula.Aula;
import com.nomi.backend.domain.port.in.aula.CreateAulaUseCase;
import com.nomi.backend.domain.port.in.aula.DeleteAulaUseCase;
import com.nomi.backend.domain.port.in.aula.FindAulaUseCase;
import com.nomi.backend.domain.port.in.aula.UpdateAulaUseCase;
import com.nomi.backend.infrastructure.web.dto.aula.AulaResponse;
import com.nomi.backend.infrastructure.web.dto.aula.CreateAulaRequest;
import com.nomi.backend.infrastructure.web.dto.aula.UpdateAulaRequest;
import com.nomi.backend.infrastructure.web.mapper.AulaWebMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Aulas del campus. La lectura es para usuarios autenticados; el resto, solo para administradores.
 */
@Tag(name = "Aulas")
@RestController
@RequestMapping("/aulas")
@RequiredArgsConstructor
public class AulaController {

    private final CreateAulaUseCase createAulaUseCase;
    private final FindAulaUseCase findAulaUseCase;
    private final UpdateAulaUseCase updateAulaUseCase;
    private final DeleteAulaUseCase deleteAulaUseCase;
    private final AulaWebMapper mapper;

    @Operation(summary = "Crear aula")
    @PostMapping
    public ResponseEntity<AulaResponse> create(@Valid @RequestBody CreateAulaRequest request) {
        Aula aula = createAulaUseCase.execute(mapper.toCommand(request));
        return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toResponse(aula));
    }

    @Operation(summary = "Listar aulas")
    @GetMapping
    public ResponseEntity<List<AulaResponse>> findAll() {
        List<AulaResponse> responses = findAulaUseCase.findAll().stream()
                .map(mapper::toResponse)
                .toList();
        return ResponseEntity.ok(responses);
    }

    @Operation(summary = "Listar aulas activas")
    @GetMapping("/activas")
    public ResponseEntity<List<AulaResponse>> findAllActivas() {
        List<AulaResponse> responses = findAulaUseCase.findAllActivas().stream()
                .map(mapper::toResponse)
                .toList();
        return ResponseEntity.ok(responses);
    }

    @Operation(summary = "Obtener aula por ID")
    @GetMapping("/{id}")
    public ResponseEntity<AulaResponse> findById(@PathVariable Long id) {
        Aula aula = findAulaUseCase.findById(id);
        return ResponseEntity.ok(mapper.toResponse(aula));
    }

    @Operation(summary = "Actualizar aula")
    @PutMapping("/{id}")
    public ResponseEntity<AulaResponse> update(@PathVariable Long id, @Valid @RequestBody UpdateAulaRequest request) {
        Aula aula = updateAulaUseCase.execute(id, mapper.toCommand(request));
        return ResponseEntity.ok(mapper.toResponse(aula));
    }

    @Operation(summary = "Eliminar aula")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        deleteAulaUseCase.execute(id);
        return ResponseEntity.noContent().build();
    }
}
