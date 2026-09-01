package co.sena.sicot.ia;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * Única puerta de entrada a Ollama (IA local, sin costo de licencia).
 * Nadie más en el backend llama a Ollama directamente — así el modelo se
 * puede cambiar (OLLAMA_MODEL) sin tocar el resto del código. El frontend
 * nunca llama a Ollama; siempre pasa por este backend.
 *
 * <p>Al ser el único punto de paso, es también donde se aplica el límite de uso
 * ({@link LimitadorDeUsoIa}): así queda cubierto el chat, la extracción de
 * datos de un PDF y la generación de documentos sin tener que acordarse de
 * ponerlo en cada uno.
 */
@Component
public class OllamaClient {

    private static final Logger log = LoggerFactory.getLogger(OllamaClient.class);

    private final String ollamaUrl;
    private final String modelo;
    private final LimitadorDeUsoIa limitador;

    /**
     * Cliente construido <b>una sola vez</b>.
     *
     * <p>Antes se creaba uno nuevo en cada llamada, con un
     * {@code SimpleClientHttpRequestFactory} configurado mediante
     * inicialización de doble llave — una subclase anónima por cada invocación,
     * que además retiene una referencia implícita a la instancia que la creó.
     * Los tiempos de espera no cambian entre peticiones, así que no había nada
     * que reconstruir.
     */
    private final RestClient restClient;

    /**
     * Inyección por constructor, como el resto del backend. Con {@code @Value}
     * sobre campos, los valores no existen todavía cuando corre el constructor,
     * lo que impide precisamente construir aquí el cliente.
     */
    public OllamaClient(@Value("${sicot.ia.ollama-url}") String ollamaUrl,
                        @Value("${sicot.ia.ollama-model}") String modelo,
                        @Value("${sicot.ia.timeout-seconds}") int timeoutSeconds,
                        LimitadorDeUsoIa limitador) {
        this.ollamaUrl = ollamaUrl;
        this.modelo = modelo;
        this.limitador = limitador;

        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(Duration.ofSeconds(10));
        fabrica.setReadTimeout(Duration.ofSeconds(timeoutSeconds));
        this.restClient = RestClient.builder()
                .baseUrl(ollamaUrl)
                .requestFactory(fabrica)
                .build();
    }

    /**
     * Genera texto con Ollama. Si formatoJson es true, se le exige al modelo
     * que responda JSON válido (Ollama valida el formato del lado del servidor).
     * Falla honesto (excepción) si Ollama no está disponible — nunca se
     * fabrica una respuesta falsa para disimular que la IA no respondió.
     */
    public String generar(String prompt, boolean formatoJson) {
        return limitador.ejecutar("ollama:generar", () -> llamar(prompt, formatoJson));
    }

    private String llamar(String prompt, boolean formatoJson) {
        try {
            GenerateRequest request = new GenerateRequest(modelo, prompt, false, formatoJson ? "json" : null);
            GenerateResponse respuesta = restClient.post()
                    .uri("/api/generate")
                    .contentType(MediaType.APPLICATION_JSON)
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
