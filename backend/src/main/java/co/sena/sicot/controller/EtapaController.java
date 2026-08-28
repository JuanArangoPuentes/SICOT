package co.sena.sicot.controller;

import co.sena.sicot.dto.etapa.EtapaResponse;
import co.sena.sicot.service.EtapaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Controlador de etapas del flujo GCCON-P-010.
 * <p>
 * <b>Control de acceso:</b> Este controlador NO declara {@code @PreAuthorize}.
 * La autorización real se aplica en {@link EtapaService#listarPorContrato(Long)}
 * y {@link EtapaService#obtenerEtapaDeContrato(Long, Long)} mediante
 * {@code SecurityUtils.verificarAccesoAlContrato(contratoId)}.
 * Un SUPERVISOR solo alcanza las etapas del contrato que tiene asignado.
 */
@RestController
@RequestMapping("/api/contratos/{contratoId}/etapas")
@Tag(name = "Etapas", description = "Etapas del flujo GCCON-P-010 de un contrato")
public class EtapaController {

    private final EtapaService etapaService;

    public EtapaController(EtapaService etapaService) {
        this.etapaService = etapaService;
    }

    @Operation(summary = "Listar etapas de un contrato")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lista de etapas",
                    content = @Content(schema = @Schema(implementation = EtapaResponse.class))),
            @ApiResponse(responseCode = "401", description = "No autenticado",
                    content = @Content(schema = @Schema(implementation = co.sena.sicot.exception.ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Sin acceso a este contrato",
                    content = @Content(schema = @Schema(implementation = co.sena.sicot.exception.ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Contrato no encontrado",
                    content = @Content(schema = @Schema(implementation = co.sena.sicot.exception.ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Error interno del servidor",
                    content = @Content(schema = @Schema(implementation = co.sena.sicot.exception.ErrorResponse.class)))
    })
    @GetMapping
    public ResponseEntity<List<EtapaResponse>> listar(@PathVariable Long contratoId) {
        return ResponseEntity.ok(etapaService.listarPorContrato(contratoId));
    }

    @Operation(summary = "Obtener una etapa de un contrato")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Etapa encontrada",
                    content = @Content(schema = @Schema(implementation = EtapaResponse.class))),
            @ApiResponse(responseCode = "401", description = "No autenticado",
                    content = @Content(schema = @Schema(implementation = co.sena.sicot.exception.ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Sin acceso a este contrato",
                    content = @Content(schema = @Schema(implementation = co.sena.sicot.exception.ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Contrato o etapa no encontrados",
                    content = @Content(schema = @Schema(implementation = co.sena.sicot.exception.ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Error interno del servidor",
                    content = @Content(schema = @Schema(implementation = co.sena.sicot.exception.ErrorResponse.class)))
    })
    @GetMapping("/{etapaId}")
    public ResponseEntity<EtapaResponse> obtener(@PathVariable Long contratoId,
                                                 @PathVariable Long etapaId) {
        return ResponseEntity.ok(etapaService.obtenerEtapaDeContrato(contratoId, etapaId));
    }
}
