package co.sena.sicot.controller;

import co.sena.sicot.dto.alerta.AlertaResponse;
import co.sena.sicot.service.AlertaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controlador de alertas.
 * <p>
 * <b>Control de acceso GET /api/contratos/{contratoId}/alertas:</b> No declara {@code @PreAuthorize}.
 * La autorización real se aplica en {@link AlertaService#listarPorContrato(Long)} mediante
 * {@code SecurityUtils.verificarAccesoAlContrato(contratoId)}.
 * Un SUPERVISOR solo alcanza las alertas del contrato que tiene asignado.
 * <p>
 * <b>Control de acceso GET /api/alertas:</b> {@code @PreAuthorize("hasAnyRole('GESTION', 'ADMINISTRADOR')")} —
 * solo GESTION y ADMINISTRADOR ven el listado global.
 */
@RestController
@RequestMapping("/api")
@Tag(name = "Alertas", description = "Alertas y recordatorios del sistema")
public class AlertaController {

    private final AlertaService alertaService;

    public AlertaController(AlertaService alertaService) {
        this.alertaService = alertaService;
    }

    @Operation(summary = "Listar alertas de un contrato")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lista de alertas",
                    content = @Content(schema = @Schema(implementation = AlertaResponse.class))),
            @ApiResponse(responseCode = "401", description = "No autenticado",
                    content = @Content(schema = @Schema(implementation = co.sena.sicot.exception.ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Sin acceso a este contrato",
                    content = @Content(schema = @Schema(implementation = co.sena.sicot.exception.ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Contrato no encontrado",
                    content = @Content(schema = @Schema(implementation = co.sena.sicot.exception.ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Error interno del servidor",
                    content = @Content(schema = @Schema(implementation = co.sena.sicot.exception.ErrorResponse.class)))
    })
    @GetMapping("/contratos/{contratoId}/alertas")
    public ResponseEntity<List<AlertaResponse>> listarPorContrato(@PathVariable Long contratoId) {
        return ResponseEntity.ok(alertaService.listarPorContrato(contratoId));
    }

    @Operation(summary = "Listar todas las alertas")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lista global de alertas",
                    content = @Content(schema = @Schema(implementation = AlertaResponse.class))),
            @ApiResponse(responseCode = "401", description = "No autenticado",
                    content = @Content(schema = @Schema(implementation = co.sena.sicot.exception.ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Sin rol GESTION/ADMINISTRADOR",
                    content = @Content(schema = @Schema(implementation = co.sena.sicot.exception.ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Error interno del servidor",
                    content = @Content(schema = @Schema(implementation = co.sena.sicot.exception.ErrorResponse.class)))
    })
    @GetMapping("/alertas")
    @PreAuthorize("hasAnyRole('GESTION', 'ADMINISTRADOR')")
    public ResponseEntity<List<AlertaResponse>> listarTodas() {
        return ResponseEntity.ok(alertaService.listarTodas());
    }

    @Operation(summary = "Marcar alerta como leída")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Alerta marcada como leída",
                    content = @Content(schema = @Schema(implementation = AlertaResponse.class))),
            @ApiResponse(responseCode = "401", description = "No autenticado",
                    content = @Content(schema = @Schema(implementation = co.sena.sicot.exception.ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Sin rol SUPERVISOR/GESTION/ADMINISTRADOR",
                    content = @Content(schema = @Schema(implementation = co.sena.sicot.exception.ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Alerta no encontrada",
                    content = @Content(schema = @Schema(implementation = co.sena.sicot.exception.ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Error interno del servidor",
                    content = @Content(schema = @Schema(implementation = co.sena.sicot.exception.ErrorResponse.class)))
    })
    @PatchMapping("/alertas/{id}/leida")
    @PreAuthorize("hasAnyRole('SUPERVISOR', 'GESTION', 'ADMINISTRADOR')")
    public ResponseEntity<AlertaResponse> marcarLeida(@PathVariable Long id) {
        return ResponseEntity.ok(alertaService.marcarLeida(id));
    }
}
