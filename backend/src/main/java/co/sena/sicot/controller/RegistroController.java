package co.sena.sicot.controller;

import co.sena.sicot.dto.registro.NovedadesResponse;
import co.sena.sicot.dto.registro.RegistroResponse;
import co.sena.sicot.service.RegistroService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
 * <p>
 * <b>Control de acceso GET /api/contratos/{contratoId}/novedades:</b> igual que el listado por
 * contrato — sin {@code @PreAuthorize}, con la comprobación dentro de
 * {@link RegistroService#novedadesDesde(Long, Long)}. Tiene que ser la misma puerta: un endpoint
 * que dijera «sí, algo cambió» sobre un contrato ajeno filtraría actividad aunque no enseñara ni
 * una línea del expediente. Como en el resto de la API, el contrato ajeno responde <b>404</b> y no
 * 403, para que no se distinga de uno que no existe.
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

    @Operation(summary = "Novedades de un contrato desde la última vez que se preguntó",
            description = """
                    Qué ha pasado en el contrato después de la entrada de auditoría `desdeId`, para \
                    que un equipo se entere de lo que hizo otro sin recargar la pantalla. Sin \
                    `desdeId` solo devuelve la marca con la que preguntar la próxima vez. La marca \
                    es un identificador, no una hora: los relojes de los dos equipos no intervienen.""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Marca y novedades (normalmente vacías)",
                    content = @Content(schema = @Schema(implementation = NovedadesResponse.class))),
            @ApiResponse(responseCode = "400", description = "El parámetro desdeId no es un número",
                    content = @Content(schema = @Schema(implementation = co.sena.sicot.exception.ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "No autenticado",
                    content = @Content(schema = @Schema(implementation = co.sena.sicot.exception.ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Contrato no encontrado, o sin acceso a él",
                    content = @Content(schema = @Schema(implementation = co.sena.sicot.exception.ErrorResponse.class)))
    })
    @GetMapping("/contratos/{contratoId}/novedades")
    public ResponseEntity<NovedadesResponse> novedades(
            @PathVariable Long contratoId,
            @RequestParam(required = false) Long desdeId) {
        // Sin caché: esta respuesta es distinta cada vez que se pide y lo que se
        // está preguntando es justamente «¿cambió algo?». Un intermediario que
        // la guardara dejaría a los dos equipos viéndose con retraso sin que
        // nada pareciera roto.
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(registroService.novedadesDesde(contratoId, desdeId));
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
