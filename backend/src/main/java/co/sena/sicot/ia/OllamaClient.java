package co.sena.sicot.ia;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * Única puerta de entrada a Ollama (IA local, sin costo de licencia).
 * Nadie más en el backend llama a Ollama directamente — así el modelo se
 * puede cambiar (OLLAMA_MODEL) sin tocar el resto del código. El frontend
 * nunca llama a Ollama; siempre pasa por el backend (ver
 * .github/copilot-instructions.md §29).
 */
@Component
public class OllamaClient {

    private static final Logger log = LoggerFactory.getLogger(OllamaClient.class);

    @Value("${sicot.ia.ollama-url}")
    private String ollamaUrl;

    @Value("${sicot.ia.ollama-model}")
    private String modelo;

    @Value("${sicot.ia.timeout-seconds}")
    private int timeoutSeconds;

    private RestClient restClient() {
        return RestClient.builder()
                .baseUrl(ollamaUrl)
                .requestFactory(new org.springframework.http.client.SimpleClientHttpRequestFactory() {{
                    setConnectTimeout((int) Duration.ofSeconds(10).toMillis());
                    setReadTimeout((int) Duration.ofSeconds(timeoutSeconds).toMillis());
                }})
                .build();
    }

    /**
     * Genera texto con Ollama. Si formatoJson es true, se le exige al modelo
     * que responda JSON válido (Ollama valida el formato del lado del servidor).
     * Falla honesto (excepción) si Ollama no está disponible — nunca se
     * fabrica una respuesta falsa para disimular que la IA no respondió.
     */
    public String generar(String prompt, boolean formatoJson) {
        try {
            GenerateRequest request = new GenerateRequest(modelo, prompt, false, formatoJson ? "json" : null);
            GenerateResponse respuesta = restClient().post()
                    .uri("/api/generate")
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(GenerateResponse.class);
            if (respuesta == null || respuesta.response() == null) {
                throw new IaNoDisponibleException("Ollama respondió vacío.");
            }
            return respuesta.response();
        } catch (IaNoDisponibleException e) {
            throw e;
        } catch (Exception e) {
            // El detalle técnico (URL interna, modelo configurado) va SOLO al log
            // del servidor: GlobalExceptionHandler propaga el mensaje de esta
            // excepción al cliente, y esa URL es topología interna que no le
            // corresponde ver a un usuario final. Lo que sí recibe es una
            // explicación honesta de qué pasó y qué se puede hacer.
            log.error("Fallo al contactar Ollama en {} con el modelo '{}'", ollamaUrl, modelo, e);
            throw new IaNoDisponibleException(
                    "El servicio de IA no está disponible en este momento. "
                            + "Intente de nuevo en unos minutos; si el problema persiste, avise al área de sistemas.", e);
        }
    }

    private record GenerateRequest(String model, String prompt, boolean stream, String format) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record GenerateResponse(String response) {
    }
}
