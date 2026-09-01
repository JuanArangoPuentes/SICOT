package co.sena.sicot.controller;

import co.sena.sicot.dto.chequeo.ListaChequeoDetalle;
import co.sena.sicot.dto.chequeo.ListaChequeoResumen;
import co.sena.sicot.dto.chequeo.TipoListaChequeo;
import co.sena.sicot.service.ListaChequeoService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Controlador de listas de chequeo (catálogo de solo lectura).
 * <p>
 * No declara {@code @PreAuthorize}: es un catálogo público institucional
 * (GCCON por modalidad de selección y GRF-F-088 para trámite de pago).
 * Cualquier usuario autenticado puede consultarlo.
 */
@RestController
@RequestMapping("/api/listas-chequeo")
@Tag(name = "Listas de chequeo",
        description = "Catálogo de las listas de chequeo documentales oficiales del SENA "
                + "(GCCON por modalidad de selección y GRF-F-088 para el trámite de pago)")
public class ListaChequeoController {

    private final ListaChequeoService listaChequeoService;

    public ListaChequeoController(ListaChequeoService listaChequeoService) {
        this.listaChequeoService = listaChequeoService;
    }

    @Operation(summary = "Listar el catálogo de listas de chequeo, opcionalmente filtrado por tipo")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Catálogo de listas de chequeo",
                    content = @Content(schema = @Schema(implementation = ListaChequeoResumen.class))),
            @ApiResponse(responseCode = "401", description = "No autenticado",
                    content = @Content(schema = @Schema(implementation = co.sena.sicot.exception.ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Error interno del servidor",
                    content = @Content(schema = @Schema(implementation = co.sena.sicot.exception.ErrorResponse.class)))
    })
    @GetMapping
    public ResponseEntity<List<ListaChequeoResumen>> listar(
            @RequestParam(required = false) TipoListaChequeo tipo) {
        return ResponseEntity.ok(listaChequeoService.listar(tipo));
    }

    @Operation(summary = "Obtener una lista de chequeo completa por su código (ej. GCCON-F-053)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lista de chequeo completa",
                    content = @Content(schema = @Schema(implementation = ListaChequeoDetalle.class))),
            @ApiResponse(responseCode = "401", description = "No autenticado",
                    content = @Content(schema = @Schema(implementation = co.sena.sicot.exception.ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Código no encontrado",
                    content = @Content(schema = @Schema(implementation = co.sena.sicot.exception.ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Error interno del servidor",
                    content = @Content(schema = @Schema(implementation = co.sena.sicot.exception.ErrorResponse.class)))
    })
    @GetMapping("/{codigo}")
    public ResponseEntity<ListaChequeoDetalle> obtener(@PathVariable String codigo) {
        return ResponseEntity.ok(listaChequeoService.obtener(codigo));
    }
}
