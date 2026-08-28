package co.sena.sicot.controller;

import co.sena.sicot.dto.etapa.ActualizarEstadoSubetapaRequest;
import co.sena.sicot.dto.etapa.SubetapaResponse;
import co.sena.sicot.service.EtapaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controlador de subetapas del flujo GCCON-P-010.
 * <p>
 * <b>Control de acceso GET /api/etapas/{etapaId}/subetapas:</b> No declara {@code @PreAuthorize}.
 * La autorización real se aplica en {@link EtapaService#listarSubetapas(Long)} mediante
 * {@code SecurityUtils.verificarAccesoAlContrato} a través de la etapa padre.
 * <p>
 * <b>Control de acceso PATCH /api/sutetapas/{id}/estado:</b> {@code @PreAuthorize} por rol
 * + verificación en service de acceso al contrato de la subetapa.
 */
@RestController
@RequestMapping("/api")
@Tag(name = "Subetapas", description = "Subetapas del flujo GCCON-P-010")
public class SubetapaController {

    private final EtapaService etapaService;

    public SubetapaController(EtapaService etapaService) {
        this.etapaService = etapaService;
    }

    @Operation(summary = "Listar subetapas de una etapa")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lista de subetapas",
                    content = @Content(schema = @Schema(implementation = SubetapaResponse.class))),
            @ApiResponse(responseCode = "401", description = "No autenticado",
                    content = @Content(schema = @Schema(implementation = co.sena.sicot.exception.ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Sin acceso al contrato de la etapa",
                    content = @Content(schema = @Schema(implementation = co.sena.sicot.exception.ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Etapa no encontrada",
                    content = @Content(schema = @Schema(implementation = co.sena.sicot.exception.ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Error interno del servidor",
                    content = @Content(schema = @Schema(implementation = co.sena.sicot.exception.ErrorResponse.class)))
    })
    @GetMapping("/etapas/{etapaId}/subetapas")
    public ResponseEntity<List<SubetapaResponse>> listar(@PathVariable Long etapaId) {
        return ResponseEntity.ok(etapaService.listarSubetapas(etapaId));
    }

    @Operation(summary = "Cambiar estado de una subetapa",
            description = "Al completar subetapas se recalcula el porcentaje y estado de la etapa.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Estado cambiado",
                    content = @Content(schema = @Schema(implementation = SubetapaResponse.class))),
            @ApiResponse(responseCode = "400", description = "Estado inválido",
                    content = @Content(schema = @Schema(implementation = co.sena.sicot.exception.ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "No autenticado",
                    content = @Content(schema = @Schema(implementation = co.sena.sicot.exception.ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Sin rol SUPERVISOR/GESTION/ADMINISTRADOR o sin acceso al contrato",
                    content = @Content(schema = @Schema(implementation = co.sena.sicot.exception.ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Subetapa no encontrada",
                    content = @Content(schema = @Schema(implementation = co.sena.sicot.exception.ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Error interno del servidor",
                    content = @Content(schema = @Schema(implementation = co.sena.sicot.exception.ErrorResponse.class)))
    })
    @PatchMapping("/subetapas/{id}/estado")
    @PreAuthorize("hasAnyRole('SUPERVISOR', 'GESTION', 'ADMINISTRADOR')")
    public ResponseEntity<SubetapaResponse> cambiarEstado(@PathVariable Long id,
                                                          @Valid @RequestBody ActualizarEstadoSubetapaRequest request) {
        return ResponseEntity.ok(etapaService.cambiarEstadoSubetapa(id, request.estado()));
    }
}
