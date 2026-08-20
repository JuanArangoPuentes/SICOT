package co.sena.sicot.controller;

import co.sena.sicot.dto.ia.ChatRequest;
import co.sena.sicot.dto.ia.ChatResponse;
import co.sena.sicot.ia.CopilotoChatService;
import io.swagger.v3.oas.annotations.Operation;
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
            description = "Respuesta real de Ollama, anclada a los datos del contrato y al estado real de sus etapas — no es una respuesta prescrita.")
    @PostMapping("/chat")
    @PreAuthorize("hasAnyRole('SUPERVISOR', 'ADMINISTRADOR')")
    public ResponseEntity<ChatResponse> chat(@PathVariable Long contratoId, @Valid @RequestBody ChatRequest request) {
        String respuesta = copilotoChatService.responder(contratoId, request.pregunta(), request.historial());
        return ResponseEntity.ok(new ChatResponse(respuesta));
    }
}
