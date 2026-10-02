package co.sena.sicot.ia;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

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

    /**
     * Cuánto mantiene Ollama el modelo cargado tras responder.
     *
     * <p>Su valor por defecto son 5 minutos, y eso echaba a perder el
     * precalentado: al descargar el modelo se pierde con él la caché del prefijo
     * del prompt, que es todo lo que el precalentado había construido. El
     * supervisor abre la ficha del contrato, la lee con calma, pregunta a los
     * seis minutos y vuelve a pagar los ~160 s enteros como si no se hubiera
     * precalentado nada.
     *
     * <p>Se configura en vez de fijarse porque el precio de subirlo es memoria
     * retenida —unos 5 GB con el modelo por defecto— y esa cuenta no sale igual
     * en el equipo del supervisor que en un servidor.
     */
    private final String keepAlive;

    private final LimitadorDeUsoIa limitador;

    private final int timeoutSeconds;

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
     * Opciones con las que se genera cuando quien llama no da otras.
     *
     * <p>Hasta el 02-10-2026 no se mandaba ninguna y Ollama usaba las suyas:
     * temperatura 0,8 y sin tope de tokens. La temperatura alta es justo lo que
     * la comprobación de fidelidad castiga —sinónimos, valoraciones, cifras
     * completadas— y hace que el mismo prompt dé textos distintos, así que
     * nada se podía medir dos veces igual. Sin tope, un modelo que entra en un
     * bucle de repetición sigue escribiendo hasta el tiempo límite: cuatro
     * minutos de espera para nada.
     */
    private final Opciones porDefecto;

    /**
     * Tamaño del contexto, igual en todas las llamadas o ninguno (0: el de
     * Ollama). Uno distinto por llamada obliga a Ollama a recargar el modelo,
     * y con él se pierde la caché del precalentado.
     */
    private final int numCtx;

    /** Con las opciones por defecto de {@code application.properties}. */
    public OllamaClient(String ollamaUrl, String modelo, int timeoutSeconds, String keepAlive,
                        LimitadorDeUsoIa limitador) {
        this(ollamaUrl, modelo, timeoutSeconds, keepAlive, limitador, 0.2, 1024, 0);
    }

    /**
     * Inyección por constructor, como el resto del backend. Con {@code @Value}
     * sobre campos, los valores no existen todavía cuando corre el constructor,
     * lo que impide precisamente construir aquí el cliente.
     */
    @Autowired
    public OllamaClient(@Value("${sicot.ia.ollama-url}") String ollamaUrl,
                        @Value("${sicot.ia.ollama-model}") String modelo,
                        @Value("${sicot.ia.timeout-seconds}") int timeoutSeconds,
                        @Value("${sicot.ia.keep-alive}") String keepAlive,
                        LimitadorDeUsoIa limitador,
                        @Value("${sicot.ia.temperatura:0.2}") double temperatura,
                        @Value("${sicot.ia.max-tokens:1024}") int maxTokens,
                        @Value("${sicot.ia.num-ctx:0}") int numCtx) {
        this.ollamaUrl = ollamaUrl;
        this.modelo = modelo;
        this.keepAlive = keepAlive;
        this.limitador = limitador;
        this.timeoutSeconds = timeoutSeconds;
        this.porDefecto = new Opciones(temperatura, maxTokens);
        this.numCtx = numCtx;

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
        // Con JSON el modelo copia datos de un documento: no hay nada que
        // variar, y a temperatura 0 la misma extracción da el mismo resultado.
        return generar(prompt, formatoJson, formatoJson ? new Opciones(0.0, porDefecto.maxTokens()) : porDefecto);
    }

    /** Como {@link #generar(String, boolean)}, con las opciones de esta llamada. */
    public String generar(String prompt, boolean formatoJson, Opciones opciones) {
        return limitador.ejecutar("ollama:generar", () -> llamar(prompt, formatoJson, opciones));
    }

    /**
     * Genera texto sin compartir el modelo con otra inferencia: espera hasta
     * {@code espera} a que no haya ninguna en curso y no deja empezar otra
     * mientras dura (ver {@link LimitadorDeUsoIa#ejecutarEnExclusiva}). Va a
     * la temperatura por defecto, con el tope de tokens que da quien llama:
     * el que le corresponde a lo que pide, no el general.
     */
    public String generarSinCompetir(String prompt, int maxTokens, Duration espera) {
        Opciones opciones = new Opciones(porDefecto.temperatura(), maxTokens);
        return limitador.ejecutarEnExclusiva("ollama:redactar", espera, () -> llamar(prompt, false, opciones));
    }

    /**
     * Opciones de generación de una llamada.
     *
     * @param temperatura cuánto varía el modelo al escoger cada palabra (0: siempre la más probable)
     * @param maxTokens   tope de tokens de la respuesta; si lo alcanza, la respuesta está cortada y
     *                    se trata como un fallo ({@link IaNoDisponibleException.Causa#RESPUESTA_CORTADA})
     */
    public record Opciones(double temperatura, int maxTokens) {
    }

    private String llamar(String prompt, boolean formatoJson, Opciones opciones) {
        try {
            Map<String, Object> options = new LinkedHashMap<>();
            options.put("temperature", opciones.temperatura());
            options.put("num_predict", opciones.maxTokens());
            if (numCtx > 0) {
                options.put("num_ctx", numCtx);
            }
            GenerateRequest request =
                    new GenerateRequest(modelo, prompt, false, formatoJson ? "json" : null, keepAlive, options);
            GenerateResponse respuesta = restClient.post()
                    .uri("/api/generate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(GenerateResponse.class);
            if (respuesta == null || respuesta.response() == null) {
                throw new IaNoDisponibleException("Ollama respondió vacío.");
            }
            // «length»: se acabó el tope de tokens antes de que el modelo
            // terminara. Un texto cortado a media frase no se entrega como si
            // fuera la respuesta completa; con el tope por defecto, casi
            // siempre es un bucle de repetición.
            if ("length".equals(respuesta.doneReason())) {
                log.warn("Ollama llegó al tope de {} tokens con el modelo '{}'; la respuesta quedó cortada.",
                        opciones.maxTokens(), modelo);
                throw new IaNoDisponibleException(IaNoDisponibleException.Causa.RESPUESTA_CORTADA,
                        "La respuesta de la IA superó el largo máximo y quedó cortada. "
                                + "Intente de nuevo con una consulta más concreta.", null);
            }
            return respuesta.response();
        } catch (IaNoDisponibleException e) {
            throw e;
        } catch (Exception e) {
            if (esTiempoAgotado(e)) {
                // Distinto de «no disponible»: el modelo SÍ respondía, solo que
                // más despacio que el límite. Decir «el servicio no está
                // disponible» mandaba al supervisor a avisar a sistemas por un
                // equipo ocupado, y lo animaba a reintentar enseguida, que es
                // justo lo que lo vuelve a saturar (prueba integral del
                // 24-09-2026: el Informe de Supervisión se cortó a los 240 s).
                log.warn("Ollama no respondió en {} s con el modelo '{}'", timeoutSeconds, modelo);
                throw new IaNoDisponibleException(IaNoDisponibleException.Causa.TIEMPO_AGOTADO,
                        "La IA tardó más de " + timeoutSeconds + " segundos en responder y la petición se "
                                + "canceló. Suele pasar cuando el equipo está ocupado con otras tareas; "
                                + "intente de nuevo en unos minutos.", e);
            }
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

    private static boolean esTiempoAgotado(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof java.net.SocketTimeoutException) {
                return true;
            }
        }
        return false;
    }

    /**
     * {@code keep_alive} lleva {@code @JsonProperty} porque Ollama espera ese
     * nombre exacto, con guion bajo. Un {@code keepAlive} en camelCase se
     * serializa sin error, Ollama lo ignora en silencio y el modelo se descarga
     * igual a los 5 minutos — un fallo que no deja rastro en ningún log.
     */
    private record GenerateRequest(String model, String prompt, boolean stream, String format,
                                   @JsonProperty("keep_alive") String keepAlive, Map<String, Object> options) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record GenerateResponse(String response, @JsonProperty("done_reason") String doneReason) {
    }
}
