package co.sena.sicot.controller;

import co.sena.sicot.dto.ia.ChatRequest;
import co.sena.sicot.dto.ia.ChatResponse;
import co.sena.sicot.ia.CopilotoChatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/contratos/{contratoId}/copiloto")
@Tag(name = "Copiloto IA", description = "Chat conversacional real (Ollama) anclado a los datos reales del contrato")
public class CopilotoController {

    private final CopilotoChatService copilotoChatService;

    public CopilotoController(CopilotoChatService copilotoChatService) {
        this.copilotoChatService = copilotoChatService;
    }

    @Operation(summary = "Preguntar algo al Copiloto IA sobre este contrato",
            description = """
                    Lo que tiene respuesta fija lo contesta SICOT sin modelo (fuente = SISTEMA): las órdenes
                    («llévame al paso 3», «genera el acta de inicio»), la ficha de cada documento formal, el
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

    @Operation(summary = "Precalentar el contexto de este contrato en el modelo",
            description = """
                    Devuelve 202 de inmediato y deja el trabajo en segundo plano. Está pensado para
                    llamarse cuando el supervisor ABRE el contrato, no cuando pregunta.

                    El motivo es medible: la primera pregunta sobre un contrato tarda ~158 s con el
                    modelo por defecto, de los que ~120 s son solo leer el prompt; la segunda pregunta
                    sobre el mismo contrato tarda 0,8 s en esa fase, porque Ollama reutiliza el prefijo
                    cacheado. Llamando aquí al abrir la ficha, esa espera transcurre mientras el
                    supervisor lee la pantalla en vez de mientras espera una respuesta.

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
