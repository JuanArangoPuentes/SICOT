package co.sena.sicot.controller;

import co.sena.sicot.dto.contrato.ActualizarContratoRequest;
import co.sena.sicot.dto.contrato.AsignarSupervisorRequest;
import co.sena.sicot.dto.contrato.CambiarEstadoContratoRequest;
import co.sena.sicot.dto.contrato.ContratoResponse;
import co.sena.sicot.dto.contrato.CrearContratoRequest;
import co.sena.sicot.entity.enums.EstadoContrato;
import co.sena.sicot.service.ContratoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/contratos")
@Tag(name = "Contratos", description = "Gestión de contratos: consulta, creación y trámites")
public class ContratoController {

    private final ContratoService contratoService;

    public ContratoController(ContratoService contratoService) {
        this.contratoService = contratoService;
    }

    @Operation(summary = "Listar contratos", description = "Filtros opcionales por supervisor y estado.")
    @GetMapping
    public ResponseEntity<List<ContratoResponse>> listar(
            @Parameter(description = "Id del supervisor asignado")
            @RequestParam(required = false) Long supervisorId,
            @Parameter(description = "Estado del contrato")
            @RequestParam(required = false) EstadoContrato estado) {
        return ResponseEntity.ok(contratoService.listar(supervisorId, estado));
    }

    @Operation(summary = "Obtener contrato por id")
    @GetMapping("/{id}")
    public ResponseEntity<ContratoResponse> obtener(@PathVariable Long id) {
        return ResponseEntity.ok(contratoService.obtener(id));
    }

    @Operation(summary = "Crear contrato")
    @PostMapping
    @PreAuthorize("hasAnyRole('GESTION', 'ADMINISTRADOR')")
    public ResponseEntity<ContratoResponse> crear(@Valid @RequestBody CrearContratoRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(contratoService.crear(request));
    }

    @Operation(summary = "Actualizar contrato")
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('GESTION', 'ADMINISTRADOR')")
    public ResponseEntity<ContratoResponse> actualizar(@PathVariable Long id,
                                                       @Valid @RequestBody ActualizarContratoRequest request) {
        return ResponseEntity.ok(contratoService.actualizar(id, request));
    }

    @Operation(summary = "Asignar supervisor al contrato")
    @PatchMapping("/{id}/supervisor")
    @PreAuthorize("hasAnyRole('GESTION', 'ADMINISTRADOR')")
    public ResponseEntity<ContratoResponse> asignarSupervisor(@PathVariable Long id,
                                                              @Valid @RequestBody AsignarSupervisorRequest request) {
        return ResponseEntity.ok(contratoService.asignarSupervisor(id, request));
    }

    @Operation(summary = "Cambiar estado del contrato")
    @PatchMapping("/{id}/estado")
    @PreAuthorize("hasAnyRole('GESTION', 'ADMINISTRADOR')")
    public ResponseEntity<ContratoResponse> cambiarEstado(@PathVariable Long id,
                                                          @Valid @RequestBody CambiarEstadoContratoRequest request) {
        return ResponseEntity.ok(contratoService.cambiarEstado(id, request));
    }
}
