package co.sena.sicot.controller;

import co.sena.sicot.automatizacion.AlmacenDeTareas;
import co.sena.sicot.automatizacion.MotorDeAutomatizacion;
import co.sena.sicot.automatizacion.ReglaDeCalendario;
import co.sena.sicot.automatizacion.ReglaDeEvento;
import co.sena.sicot.dto.automatizacion.EstadoDelMotorResponse;
import co.sena.sicot.dto.automatizacion.TareaAutomatizadaResponse;
import co.sena.sicot.entity.enums.EstadoTareaAutomatizada;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Ventana de operación del motor de automatizaciones (ADR-008).
 *
 * <h2>Por qué existe</h2>
 * Sin esto, la cola es una tabla que solo se puede inspeccionar entrando a
 * PostgreSQL. En la práctica eso significa que nadie la mira, y el primer aviso
 * de que el motor lleva días atascado llega cuando un supervisor pregunta por
 * qué no le llegó nada. Un componente que trabaja solo necesita una superficie
 * donde comprobar que efectivamente está trabajando.
 *
 * <h2>Por qué solo lectura, y por qué solo ADMINISTRADOR</h2>
 * Solo lectura porque reintentar o cancelar tareas a mano es una herramienta de
 * emergencia que todavía nadie ha necesitado, y una escritura que se expone
 * «por si acaso» es superficie de ataque a cambio de nada.
 *
 * <p>Solo ADMINISTRADOR porque {@code ultimoError} puede contener detalle
 * técnico del servidor de correo, y el listado revela qué contratos están
 * generando avisos — información que no le corresponde ni a GESTION ni a un
 * supervisor. La restricción está además en {@code SecurityConfig}, no solo
 * aquí: dos capas, como el resto de las rutas sensibles del backend.
 */
@RestController
@RequestMapping("/api/automatizaciones")
@PreAuthorize("hasRole('ADMINISTRADOR')")
@Tag(name = "Automatizaciones", description = "Estado del motor de automatizaciones y su cola de trabajo")
public class AutomatizacionController {

    private final AlmacenDeTareas almacen;
    private final MotorDeAutomatizacion motor;
    private final List<ReglaDeEvento> reglasDeEvento;
    private final List<ReglaDeCalendario> reglasDeCalendario;

    @Value("${sicot.automatizacion.habilitado:true}")
    private boolean habilitado;

    public AutomatizacionController(AlmacenDeTareas almacen,
                                    MotorDeAutomatizacion motor,
                                    List<ReglaDeEvento> reglasDeEvento,
                                    List<ReglaDeCalendario> reglasDeCalendario) {
        this.almacen = almacen;
        this.motor = motor;
        this.reglasDeEvento = reglasDeEvento;
        this.reglasDeCalendario = reglasDeCalendario;
    }

    @Operation(summary = "Estado de salud del motor y conteo de la cola por estado")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Estado del motor",
                    content = @Content(schema = @Schema(implementation = EstadoDelMotorResponse.class))),
            @ApiResponse(responseCode = "401", description = "No autenticado",
                    content = @Content(schema = @Schema(implementation = co.sena.sicot.exception.ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Requiere rol ADMINISTRADOR",
                    content = @Content(schema = @Schema(implementation = co.sena.sicot.exception.ErrorResponse.class)))
    })
    @GetMapping("/estado")
    public ResponseEntity<EstadoDelMotorResponse> estado() {
        Map<String, Long> porEstado = new LinkedHashMap<>();
        for (EstadoTareaAutomatizada estado : EstadoTareaAutomatizada.values()) {
            porEstado.put(estado.name(), almacen.contar(estado));
        }
        List<String> reglas = Stream.concat(
                        reglasDeEvento.stream().map(ReglaDeEvento::codigo),
                        reglasDeCalendario.stream().map(ReglaDeCalendario::codigo))
                .sorted()
                .toList();
        return ResponseEntity.ok(new EstadoDelMotorResponse(habilitado, porEstado, reglas));
    }

    @Operation(summary = "Listar tareas de la cola, opcionalmente filtradas por estado")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Tareas de la cola",
                    content = @Content(schema = @Schema(implementation = TareaAutomatizadaResponse.class))),
            @ApiResponse(responseCode = "401", description = "No autenticado",
                    content = @Content(schema = @Schema(implementation = co.sena.sicot.exception.ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Requiere rol ADMINISTRADOR",
                    content = @Content(schema = @Schema(implementation = co.sena.sicot.exception.ErrorResponse.class)))
    })
    @GetMapping("/tareas")
    public ResponseEntity<List<TareaAutomatizadaResponse>> tareas(
            @RequestParam(required = false) EstadoTareaAutomatizada estado) {
        return ResponseEntity.ok(almacen.listar(estado));
    }

    /**
     * Fuerza una evaluación de las reglas de calendario, sin esperar a las 06:00.
     *
     * <h2>Por qué esta escritura sí está expuesta</h2>
     * Es la única, y no produce ningún efecto por sí misma: evalúa reglas y
     * encola lo que corresponda, exactamente igual que la pasada diaria. Todo lo
     * que encole es idempotente, así que invocarla diez veces seguidas da el
     * mismo resultado que invocarla una.
     *
     * <p>Existe porque, sin ella, comprobar que una regla recién desplegada
     * funciona obliga a esperar al día siguiente o a cambiar la hora del
     * servidor. Ese tipo de fricción es lo que hace que las reglas se desplieguen
     * sin comprobar.
     */
    @Operation(summary = "Evaluar ahora las reglas de calendario (idempotente)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Número de tareas nuevas encoladas"),
            @ApiResponse(responseCode = "401", description = "No autenticado",
                    content = @Content(schema = @Schema(implementation = co.sena.sicot.exception.ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Requiere rol ADMINISTRADOR",
                    content = @Content(schema = @Schema(implementation = co.sena.sicot.exception.ErrorResponse.class)))
    })
    @org.springframework.web.bind.annotation.PostMapping("/evaluar")
    public ResponseEntity<Map<String, Integer>> evaluar() {
        int encoladas = motor.evaluarCalendario(LocalDate.now());
        return ResponseEntity.ok(Map.of("tareasEncoladas", encoladas));
    }
}
