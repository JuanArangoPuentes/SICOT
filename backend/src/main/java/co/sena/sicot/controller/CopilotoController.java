package co.sena.sicot.controller;

import co.sena.sicot.dto.ia.ChatRequest;
import co.sena.sicot.dto.ia.ChatResponse;
import co.sena.sicot.ia.CopilotoChatService;
import tools.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@RestController
@RequestMapping("/api/contratos/{contratoId}/copiloto")
@Tag(name = "Copiloto IA", description = "Chat conversacional real (Ollama) anclado a los datos reales del contrato")
public class CopilotoController {

    private final CopilotoChatService copilotoChatService;
    private final ObjectMapper objectMapper;

    public CopilotoController(CopilotoChatService copilotoChatService, ObjectMapper objectMapper) {
        this.copilotoChatService = copilotoChatService;
        this.objectMapper = objectMapper;
    }

    @Operation(summary = "Preguntar algo al Copiloto IA sobre este contrato",
            description = """
                    Lo que tiene respuesta fija lo contesta SICOT sin modelo (fuente = SISTEMA): las órdenes
                    («llévame al paso 3», «genera el informe de supervisión»), la ficha de cada documento formal, el
                    paso en el que va el supervisor y los datos del contrato (valor, fechas, días que quedan,
                    cronograma). Las preguntas abiertas las responde Ollama (fuente = MODELO), anclado a los
                    datos del contrato y al estado real de sus etapas.

                    `accion`, si viene, es la pantalla que el supervisor puede abrir desde la respuesta.
                    Ninguna acción firma, marca, genera ni modifica nada: la interfaz la muestra como un
                    botón y solo abre esa pantalla cuando él lo toca.

                    `idSolicitud` (opcional): el cliente lo genera por pregunta y lo repite en el reintento;
                    el reintento recibe la respuesta del primer intento en vez de lanzar otra inferencia.
                    `revisarPaso` (opcional, 1..6): la petición es la revisión consultiva antes de cerrar
                    ese paso y `pregunta` trae solo lo que describió el supervisor; las instrucciones las
                    arma el servidor.""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Respuesta del Copiloto",
                    content = @Content(schema = @Schema(implementation = ChatResponse.class))),
            @ApiResponse(responseCode = "400", description = "Pregunta vacía o inválida",
                    content = @Content(schema = @Schema(implementation = co.sena.sicot.exception.ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "No autenticado",
                    content = @Content(schema = @Schema(implementation = co.sena.sicot.exception.ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Sin rol SUPERVISOR/ADMINISTRADOR o no es el supervisor del contrato",
                    content = @Content(schema = @Schema(implementation = co.sena.sicot.exception.ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Contrato no encontrado",
                    content = @Content(schema = @Schema(implementation = co.sena.sicot.exception.ErrorResponse.class))),
            @ApiResponse(responseCode = "503", description = "Servicio de IA (Ollama) no disponible",
                    content = @Content(schema = @Schema(implementation = co.sena.sicot.exception.ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Error interno del servidor",
                    content = @Content(schema = @Schema(implementation = co.sena.sicot.exception.ErrorResponse.class)))
    })
    @PostMapping("/chat")
    @PreAuthorize("hasAnyRole('SUPERVISOR', 'ADMINISTRADOR')")
    public ResponseEntity<ChatResponse> chat(@PathVariable Long contratoId, @Valid @RequestBody ChatRequest request) {
        return ResponseEntity.ok(copilotoChatService.atender(contratoId, request));
    }

    @Operation(summary = "Preguntar al Copiloto y recibir la respuesta a medida que se escribe",
            description = """
                    La misma petición y las mismas reglas que `POST /chat`, pero la respuesta llega como
                    NDJSON (una línea JSON por evento) mientras el modelo escribe, en vez de toda junta al
                    final. En el portátil del Centro el modelo escribe a unos 3 tokens por segundo: con esto
                    el supervisor ve la respuesta formarse desde la primera palabra (ADR-006).

                    Eventos: `inicio` (enseguida), `espera` (cada 10 s mientras el modelo lee el prompt),
                    `texto` (con el trozo en `t`), y para terminar `fin` (con la `ChatResponse` completa en
                    `respuesta`, que manda sobre los trozos) o `error` (con `status` y `mensaje`, los mismos
                    de `POST /chat`).

                    El acceso al contrato se comprueba antes de abrir la respuesta: un contrato ajeno
                    responde 404 con su código, no un error dentro de un 200. Si la conexión se corta, la
                    respuesta se sigue calculando y el reintento con el mismo `idSolicitud` (por aquí o
                    por `POST /chat`) la recoge.""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Eventos de la respuesta, en NDJSON"),
            @ApiResponse(responseCode = "400", description = "Pregunta vacía o inválida",
                    content = @Content(schema = @Schema(implementation = co.sena.sicot.exception.ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "No autenticado",
                    content = @Content(schema = @Schema(implementation = co.sena.sicot.exception.ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Sin rol SUPERVISOR/ADMINISTRADOR",
                    content = @Content(schema = @Schema(implementation = co.sena.sicot.exception.ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Contrato no encontrado o ajeno",
                    content = @Content(schema = @Schema(implementation = co.sena.sicot.exception.ErrorResponse.class)))
    })
    @PostMapping(value = "/chat/en-vivo", produces = "application/x-ndjson")
    @PreAuthorize("hasAnyRole('SUPERVISOR', 'ADMINISTRADOR')")
    public ResponseEntity<StreamingResponseBody> chatEnVivo(@PathVariable Long contratoId,
                                                            @Valid @RequestBody ChatRequest request,
                                                            HttpServletResponse respuestaHttp) {
        copilotoChatService.exigirAcceso(contratoId);
        // La respuesta se escribe en otro hilo, que no hereda la sesión de esta
        // petición: sin ella, ContratoService.buscar no sabría quién pregunta.
        SecurityContext contexto = SecurityContextHolder.getContext();
        // Se escribe en el flujo del servlet y no en el que Spring le pasa al
        // cuerpo. Desde Spring Framework 7 ese flujo ignora flush()
        // (ServletServerHttpResponse lo envuelve con StreamUtils.nonFlushing
        // salvo que se active spring.http.response.flush.enabled), y Tomcat
        // retenía todos los eventos hasta el final: ni el «inicio» salía, y un
        // proxy podía cortar la primera pregunta de un contrato, que en CPU dura
        // minutos (medido el 28-10-2026: 116 eventos en un solo instante, a los
        // 57 s). Es el mismo flujo de debajo, con las cabeceras ya escritas.
        // No se activa la bandera global porque devolvería el vaciado a todas
        // las respuestas JSON, que es justo lo que Spring quitó a propósito.
        StreamingResponseBody cuerpo = salida -> {
            RespuestaEnVivoDelCopiloto respuesta =
                    new RespuestaEnVivoDelCopiloto(respuestaHttp.getOutputStream(), objectMapper);
            SecurityContextHolder.setContext(contexto);
            try {
                respuesta.atender(() -> copilotoChatService.atender(contratoId, request, respuesta::texto));
            } finally {
                SecurityContextHolder.clearContext();
            }
        };
        return ResponseEntity.ok()
                .contentType(RespuestaEnVivoDelCopiloto.NDJSON)
                // Que ningún proxy la guarde ni la comprima: las dos cosas
                // retienen los trozos hasta el final.
                .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-transform")
                .header("X-Accel-Buffering", "no")
                .body(cuerpo);
    }

    @Operation(summary = "Precalentar el contexto de este contrato en el modelo",
            description = """
                    Devuelve 202 de inmediato y deja el trabajo en segundo plano. Está pensado para
                    llamarse cuando el supervisor ABRE el contrato, no cuando pregunta.

                    El motivo es medible: la primera pregunta sobre un contrato tarda ~158 s con el
                    modelo por defecto, de los que ~120 s son solo leer el prompt; la segunda pregunta
                    sobre el mismo contrato tarda 0,8 s en esa fase, porque Ollama reutiliza el prefijo
                    cacheado. Llamando aquí al abrir la ficha, esa espera transcurre mientras el
                    supervisor lee la pantalla en vez de mientras espera una respuesta.

                    El panel lo vuelve a llamar cada 10 minutos mientras está abierto y al volver a la
                    pestaña: si el contexto del contrato sigue en la caché de Ollama, solo se renueva el
                    tiempo que el modelo queda cargado (menos de un segundo, sin inferencia); si no, se
                    precalienta de nuevo.

                    El acceso al contrato se comprueba antes de encolar: un contrato ajeno o inexistente
                    responde 404 y no encola nada.

                    No garantiza nada: si Ollama no está disponible, se registra y ya. El copiloto
                    sigue funcionando sin esto, solo que más lento la primera vez.""")
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "Precalentado encolado"),
            @ApiResponse(responseCode = "401", description = "No autenticado",
                    content = @Content(schema = @Schema(implementation = co.sena.sicot.exception.ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Sin rol SUPERVISOR/ADMINISTRADOR o no es el supervisor del contrato",
                    content = @Content(schema = @Schema(implementation = co.sena.sicot.exception.ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Contrato no encontrado",
                    content = @Content(schema = @Schema(implementation = co.sena.sicot.exception.ErrorResponse.class)))
    })
    @PostMapping("/precalentar")
    @PreAuthorize("hasAnyRole('SUPERVISOR', 'ADMINISTRADOR')")
    public ResponseEntity<Void> precalentar(@PathVariable Long contratoId) {
        copilotoChatService.precalentar(contratoId);
        return ResponseEntity.accepted().build();
    }
}
