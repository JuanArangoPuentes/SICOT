package co.sena.sicot.controller;

import co.sena.sicot.dto.registro.RegistroResponse;
import co.sena.sicot.service.RegistroService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Controlador de registros de auditoría.
 * <p>
 * <b>Control de acceso GET /api/contratos/{contratoId}/registros:</b> No declara {@code @PreAuthorize}.
 * La autorización real se aplica en {@link RegistroService#listarPorContrato(Long)} mediante
 * {@code SecurityUtils.verificarAccesoAlContrato(contratoId)}.
 * Un SUPERVISOR solo alcanza los registros del contrato que tiene asignado.
 * <p>
 * <b>Control de acceso GET /api/registros:</b> {@code @PreAuthorize("hasRole('ADMINISTRADOR')")} —
 * solo ADMINISTRADOR ve el listado global.
 */
@RestController
@RequestMapping("/api")
@Tag(name = "Registros", description = "Auditoría de acciones sobre contratos")
public class RegistroController {

    private final RegistroService registroService;

    public RegistroController(RegistroService registroService) {
        this.registroService = registroService;
    }

    @Operation(summary = "Listar registros de auditoría de un contrato")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lista de registros",
                    content = @Content(schema = @Schema(implementation = RegistroResponse.class))),
            @ApiResponse(responseCode = "401", description = "No autenticado",
                    content = @Content(schema = @Schema(implementation = co.sena.sicot.exception.ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Sin acceso a este contrato",
                    content = @Content(schema = @Schema(implementation = co.sena.sicot.exception.ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Contrato no encontrado",
                    content = @Content(schema = @Schema(implementation = co.sena.sicot.exception.ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Error interno del servidor",
                    content = @Content(schema = @Schema(implementation = co.sena.sicot.exception.ErrorResponse.class)))
    })
    @GetMapping("/contratos/{contratoId}/registros")
    public ResponseEntity<List<RegistroResponse>> listarPorContrato(@PathVariable Long contratoId) {
        return ResponseEntity.ok(registroService.listarPorContrato(contratoId));
    }

    @Operation(summary = "Listar todos los registros de auditoría")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lista global de registros",
                    content = @Content(schema = @Schema(implementation = RegistroResponse.class))),
            @ApiResponse(responseCode = "401", description = "No autenticado",
                    content = @Content(schema = @Schema(implementation = co.sena.sicot.exception.ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Sin rol ADMINISTRADOR",
                    content = @Content(schema = @Schema(implementation = co.sena.sicot.exception.ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Error interno del servidor",
                    content = @Content(schema = @Schema(implementation = co.sena.sicot.exception.ErrorResponse.class)))
    })
    @GetMapping("/registros")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<List<RegistroResponse>> listarTodas() {
        return ResponseEntity.ok(registroService.listarTodos());
    }
}
