package co.sena.sicot.controller;

import co.sena.sicot.dto.seguimiento.SeguimientoResponse;
import co.sena.sicot.service.SeguimientoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Seguimiento de supervisores para el Administrador.
 *
 * <p><b>Control de acceso:</b> {@code @PreAuthorize("hasRole('ADMINISTRADOR')")}. Reúne el
 * avance de los contratos de todos los supervisores, así que un SUPERVISOR no puede
 * alcanzarlo: vería contratos que no son suyos, que es exactamente lo que
 * {@code verificarAccesoAlContrato} impide en cada endpoint por contrato.
 */
@RestController
@RequestMapping("/api/seguimiento")
@Tag(name = "Seguimiento", description = "En qué parte del proceso va cada supervisor")
public class SeguimientoController {

    private final SeguimientoService seguimientoService;

    public SeguimientoController(SeguimientoService seguimientoService) {
        this.seguimientoService = seguimientoService;
    }

    @Operation(summary = "Avance de cada supervisor en sus contratos abiertos")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Seguimiento por supervisor",
                    content = @Content(schema = @Schema(implementation = SeguimientoResponse.class))),
            @ApiResponse(responseCode = "401", description = "No autenticado",
                    content = @Content(schema = @Schema(implementation = co.sena.sicot.exception.ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Solo ADMINISTRADOR",
                    content = @Content(schema = @Schema(implementation = co.sena.sicot.exception.ErrorResponse.class)))
    })
    @GetMapping("/supervisores")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<SeguimientoResponse> supervisores() {
        return ResponseEntity.ok(seguimientoService.supervisores());
    }
}
