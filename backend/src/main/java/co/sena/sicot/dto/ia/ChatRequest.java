package co.sena.sicot.dto.ia;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record ChatRequest(
        @NotBlank(message = "Escriba una pregunta para el Copiloto.")
        String pregunta,
        // Turnos previos de esta conversación (opcional) — le da memoria real
        // al Copiloto para que las preguntas de seguimiento ("¿y después?",
        // "explícamelo distinto") tengan sentido en vez de responderse en el
        // vacío. Rol: "user" o "ai".
        List<ChatTurno> historial
) {
    public record ChatTurno(String rol, String texto) {
    }
}
