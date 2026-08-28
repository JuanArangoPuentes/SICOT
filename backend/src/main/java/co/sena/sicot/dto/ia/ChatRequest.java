package co.sena.sicot.dto.ia;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Petición al Copiloto IA. Tanto la pregunta como el historial los manda el
 * cliente en cada llamada y se interpolan en el prompt de Ollama, así que
 * llevan topes explícitos: sin ellos, un cliente puede mandar un historial
 * arbitrariamente grande (y forjado, para poner palabras en boca de la IA en
 * turnos previos) o una pregunta de megabytes. Los límites son holgados para
 * el uso real —una pregunta larga del supervisor cabe de sobra en 8000
 * caracteres— y solo cortan el caso patológico.
 */
public record ChatRequest(
        @NotBlank(message = "Escriba una pregunta para el Copiloto.")
        @Size(max = 8000, message = "La pregunta no puede superar 8000 caracteres.")
        String pregunta,
        // Turnos previos de esta conversación (opcional) — le da memoria real
        // al Copiloto para que las preguntas de seguimiento ("¿y después?",
        // "explícamelo distinto") tengan sentido en vez de responderse en el
        // vacío. Rol: "user" o "ai".
        @Size(max = 80, message = "El historial de conversación es demasiado largo.")
        List<@Valid ChatTurno> historial
) {
    public record ChatTurno(
            @Pattern(regexp = "user|ai", message = "El rol de cada turno debe ser 'user' o 'ai'.")
            String rol,
            @Size(max = 8000, message = "Un turno del historial no puede superar 8000 caracteres.")
            String texto
    ) {
    }
}
