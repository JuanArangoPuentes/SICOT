package co.sena.sicot.ia;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;

/**
 * Comprueba al arrancar que el modelo configurado en {@code OLLAMA_MODEL} existe
 * de verdad en el servidor de Ollama.
 *
 * <h2>Por qué existe esta clase</h2>
 * Porque este fallo ya ocurrió, exactamente así. Al cambiar el modelo por
 * defecto de {@code qwen2.5-coder:7b} a {@code qwen2.5:7b} —una decisión
 * correcta, ver ADR-006— nadie comprobó que el modelo nuevo estuviera descargado
 * en la máquina. Ollama no falla al arrancar por eso: falla en la primera
 * petición real de un usuario, con un 503, horas o días después del despliegue
 * y lejos de la causa.
 *
 * <p>Es un modo de fallo estructural, no un descuido puntual: el nombre del
 * modelo es una cadena de configuración que nada valida, y descargar un modelo
 * de varios gigabytes es un paso manual que se olvida con facilidad. Va a
 * volver a pasar en cada máquina nueva donde se despliegue SICOT.
 *
 * <h2>Qué hace y qué NO hace</h2>
 * Escribe un aviso claro en el log de arranque diciendo qué modelo falta y qué
 * ejecutar para instalarlo. <b>No impide arrancar.</b> La IA es una función
 * valiosa pero no esencial: un contrato se puede supervisar sin copiloto, y
 * tumbar todo el sistema porque falta un modelo opcional cambiaría un fallo
 * parcial por uno total. El resto de la aplicación —contratos, etapas,
 * documentos, firmas— no depende de Ollama en absoluto.
 *
 * <p>Tampoco descarga el modelo por su cuenta. Una descarga de varios GB
 * disparada en silencio durante el arranque de un servicio es exactamente la
 * clase de sorpresa que no debe ocurrir en producción.
 */
@Component
public class VerificacionDelModeloIa {

    private static final Logger log = LoggerFactory.getLogger(VerificacionDelModeloIa.class);

    private final String ollamaUrl;
    private final String modelo;

    public VerificacionDelModeloIa(@Value("${sicot.ia.ollama-url}") String ollamaUrl,
                                   @Value("${sicot.ia.ollama-model}") String modelo) {
        this.ollamaUrl = ollamaUrl;
        this.modelo = modelo;
    }

    /**
     * Se ejecuta cuando la aplicación ya está lista, no durante la construcción
     * de beans: así una consulta lenta a Ollama no retrasa el arranque ni afecta
     * al healthcheck del contenedor.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void verificarModeloConfigurado() {
        List<String> disponibles;
        try {
            disponibles = modelosDisponibles();
        } catch (Exception e) {
            // Ollama apagado es un escenario legítimo y frecuente en desarrollo.
            // No es un error del arranque; el copiloto ya responde 503 con un
            // mensaje honesto cuando alguien lo usa.
            log.info("No se pudo consultar el catálogo de modelos de Ollama en {} ({}). "
                    + "Las funciones de IA responderán 503 hasta que Ollama esté disponible.",
                    ollamaUrl, e.getClass().getSimpleName());
            return;
        }

        if (disponibles.stream().anyMatch(this::coincideConElConfigurado)) {
            log.info("Modelo de IA '{}' disponible en Ollama.", modelo);
            return;
        }

        log.warn("""
                ═══════════════════════════════════════════════════════════════
                 El modelo de IA configurado NO está descargado en Ollama.

                   Configurado (OLLAMA_MODEL): {}
                   Disponibles en {}: {}

                 El copiloto, la extracción de datos de PDF y la generación de
                 documentos van a responder 503 hasta que se instale:

                   ollama pull {}

                 El resto de SICOT funciona con normalidad: la IA es opcional.
                ═══════════════════════════════════════════════════════════════""",
                modelo, ollamaUrl,
                disponibles.isEmpty() ? "(ninguno)" : String.join(", ", disponibles),
                modelo);
    }

    /**
     * Ollama devuelve los nombres con etiqueta explícita ({@code qwen2.5:7b}),
     * pero acepta peticiones sin ella, resolviéndola a {@code :latest}. Se
     * comparan las dos formas para no dar por ausente un modelo que sí está.
     */
    private boolean coincideConElConfigurado(String disponible) {
        if (disponible.equals(modelo)) {
            return true;
        }
        return !modelo.contains(":") && disponible.equals(modelo + ":latest");
    }

    private List<String> modelosDisponibles() {
        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(Duration.ofSeconds(5));
        fabrica.setReadTimeout(Duration.ofSeconds(10));

        RespuestaTags respuesta = RestClient.builder()
                .baseUrl(ollamaUrl)
                .requestFactory(fabrica)
                .build()
                .get()
                .uri("/api/tags")
                .retrieve()
                .body(RespuestaTags.class);

        if (respuesta == null || respuesta.models() == null) {
            return List.of();
        }
        return respuesta.models().stream().map(ModeloOllama::name).toList();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record RespuestaTags(List<ModeloOllama> models) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ModeloOllama(String name) {
    }
}
