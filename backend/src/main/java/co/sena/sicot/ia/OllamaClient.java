package co.sena.sicot.ia;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

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

    /**
     * El último prompt que este backend le mandó a Ollama: lo que Ollama tiene
     * en la caché del prefijo mientras no descargue el modelo. Ver
     * {@link #tieneEnCache(String)}.
     */
    private volatile String ultimoPrompt;

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

    /**
     * Como {@link #generar(String, boolean)} para texto libre, pero entrega
     * cada trozo a {@code alEscribir} en cuanto el modelo lo escribe, y al
     * final devuelve el texto entero.
     *
     * <h2>Por qué existe</h2>
     * En el portátil del Centro el modelo escribe a unos 3 tokens por segundo:
     * una respuesta de 80 tokens tarda casi medio minuto, y antes el supervisor
     * miraba «Pensando…» todo ese rato. Así ve la respuesta formarse desde la
     * primera palabra (ADR-006, revisión del 18-10-2026).
     *
     * <p>Mismo límite de uso, mismas opciones y los mismos errores honestos
     * que {@link #generar}. {@code alEscribir} no debe lanzar: si el cliente
     * se fue, quien llama sigue leyendo para que la respuesta termine y se
     * pueda recoger con el reintento.
     */
    public String generarEnVivo(String prompt, Consumer<String> alEscribir) {
        return limitador.ejecutar("ollama:generar", () -> llamarEnVivo(prompt, porDefecto, alEscribir));
    }

    private String llamarEnVivo(String prompt, Opciones opciones, Consumer<String> alEscribir) {
        ultimoPrompt = prompt;
        try {
            GenerateRequest request = new GenerateRequest(modelo, prompt, true, null, keepAlive, opcionesDeOllama(opciones));
            return restClient.post()
                    .uri("/api/generate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .exchange((peticion, respuesta) -> {
                        if (respuesta.getStatusCode().isError()) {
                            log.error("Ollama respondió {} con el modelo '{}'", respuesta.getStatusCode().value(), modelo);
                            throw new IaNoDisponibleException("El servicio de IA no está disponible en este momento. "
                                    + "Intente de nuevo en unos minutos; si el problema persiste, avise al área de "
                                    + "sistemas.");
                        }
                        StringBuilder texto = new StringBuilder();
                        String razon = null;
                        try (BufferedReader lector = new BufferedReader(
                                new InputStreamReader(respuesta.getBody(), StandardCharsets.UTF_8))) {
                            String linea;
                            while ((linea = lector.readLine()) != null) {
                                if (linea.isBlank()) {
                                    continue;
                                }
                                Fragmento f = JSON.readValue(linea, Fragmento.class);
                                if (f.error() != null) {
                                    log.error("Ollama cortó la respuesta con el modelo '{}': {}", modelo, f.error());
                                    throw new IaNoDisponibleException("La IA se detuvo a mitad de la respuesta. "
                                            + "Intente de nuevo en unos minutos.");
                                }
                                if (f.response() != null && !f.response().isEmpty()) {
                                    texto.append(f.response());
                                    alEscribir.accept(f.response());
                                }
                                if (f.done()) {
                                    razon = f.doneReason();
                                    break;
                                }
                            }
                        }
                        if ("length".equals(razon)) {
                            log.warn("Ollama llegó al tope de {} tokens con el modelo '{}'; la respuesta quedó cortada.",
                                    opciones.maxTokens(), modelo);
                            throw new IaNoDisponibleException(IaNoDisponibleException.Causa.RESPUESTA_CORTADA,
                                    "La respuesta de la IA superó el largo máximo y quedó cortada. "
                                            + "Intente de nuevo con una consulta más concreta.", null);
                        }
                        if (texto.isEmpty()) {
                            throw new IaNoDisponibleException("Ollama respondió vacío.");
                        }
                        return texto.toString();
                    });
        } catch (IaNoDisponibleException e) {
            throw e;
        } catch (Exception e) {
            throw comoFalloDeOllama(e);
        }
    }

    /**
     * ¿Ollama tiene el modelo cargado y, en su caché, un prompt que empieza por
     * {@code prefijo}? Si es así, una pregunta que empiece igual no vuelve a
     * leerlo: es lo que deja el precalentado.
     *
     * <p>Se pregunta a Ollama si el modelo sigue cargado ({@code /api/ps})
     * porque al descargarlo se pierde la caché aunque el último prompt sea el
     * mismo. Lo que otro programa le mande a Ollama por fuera de SICOT no se ve
     * aquí; en ese caso la pregunta solo es más lenta, nunca distinta.
     */
    public boolean tieneEnCache(String prefijo) {
        String ultimo = ultimoPrompt;
        if (ultimo == null || !ultimo.startsWith(prefijo)) {
            return false;
        }
        try {
            ModelosCargados cargados = restClient.get().uri("/api/ps").retrieve().body(ModelosCargados.class);
            boolean cargado = cargados != null && cargados.models() != null && cargados.models().stream()
                    .anyMatch(m -> mismoModelo(m.model()) || mismoModelo(m.name()));
            if (!cargado) {
                // Se descargó: lo que tenía en caché se fue con él.
                ultimoPrompt = null;
            }
            return cargado;
        } catch (Exception e) {
            log.info("No se pudo preguntar a Ollama qué modelos tiene cargados: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Renueva el tiempo que Ollama mantiene cargado el modelo, sin inferencia:
     * una petición sin prompt, que Ollama contesta con «load» en menos de un
     * segundo y que no toca la caché del prefijo (medido el 20-10-2026 en el
     * portátil del Centro: el mismo prompt de 396 tokens pasó de 32 s de
     * lectura a 0,26 s con una de estas en medio).
     *
     * <h2>Por qué hace falta</h2>
     * El 20-10-2026 el supervisor dejó SICOT abierto con el contrato
     * precalentado y preguntó 36 minutos después: Ollama ya había descargado el
     * modelo a los 30 ({@code sicot.ia.keep-alive}), y la pregunta pagó la
     * carga y la lectura entera del prompt, 224 s. Subir el keep-alive
     * retendría los ~5 GB del modelo también con SICOT cerrado; renovarlo
     * mientras el panel está abierto los retiene solo mientras alguien lo usa.
     *
     * <p>No pasa por {@link LimitadorDeUsoIa}: no ocupa la CPU ni compite con
     * nadie. Las opciones son las de siempre porque un {@code num_ctx}
     * distinto haría que Ollama recargara el modelo. Si falla, se anota y ya.
     */
    public void mantenerCargado() {
        try {
            restClient.post()
                    .uri("/api/generate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new GenerateRequest(modelo, null, false, null, keepAlive, opcionesDeOllama(porDefecto)))
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            log.info("No se pudo renovar el tiempo de carga del modelo '{}': {}", modelo, e.getMessage());
        }
    }

    /** Ollama nombra «modelo» a secas como «modelo:latest». */
    private boolean mismoModelo(String nombre) {
        if (nombre == null) {
            return false;
        }
        String esperado = modelo.contains(":") ? modelo : modelo + ":latest";
        return nombre.equals(modelo) || nombre.equals(esperado);
    }

    private Map<String, Object> opcionesDeOllama(Opciones opciones) {
        Map<String, Object> options = new LinkedHashMap<>();
        options.put("temperature", opciones.temperatura());
        options.put("num_predict", opciones.maxTokens());
        if (numCtx > 0) {
            options.put("num_ctx", numCtx);
        }
        return options;
    }

    private static final ObjectMapper JSON = new ObjectMapper();

    private String llamar(String prompt, boolean formatoJson, Opciones opciones) {
        ultimoPrompt = prompt;
        try {
            Map<String, Object> options = opcionesDeOllama(opciones);
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
            throw comoFalloDeOllama(e);
        }
    }

    /** El fallo de una llamada a Ollama, dicho de forma honesta y sin detalles internos. */
    private IaNoDisponibleException comoFalloDeOllama(Exception e) {
        if (esTiempoAgotado(e)) {
            // Distinto de «no disponible»: el modelo SÍ respondía, solo que
            // más despacio que el límite. Decir «el servicio no está
            // disponible» mandaba al supervisor a avisar a sistemas por un
            // equipo ocupado, y lo animaba a reintentar enseguida, que es
            // justo lo que lo vuelve a saturar (prueba integral del
            // 24-09-2026: el Informe de Supervisión se cortó a los 240 s).
            log.warn("Ollama no respondió en {} s con el modelo '{}'", timeoutSeconds, modelo);
            return new IaNoDisponibleException(IaNoDisponibleException.Causa.TIEMPO_AGOTADO,
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
        return new IaNoDisponibleException(
                "El servicio de IA no está disponible en este momento. "
                        + "Intente de nuevo en unos minutos; si el problema persiste, avise al área de sistemas.", e);
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

    /** Respuesta de {@code GET /api/ps}: los modelos que Ollama tiene cargados. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ModelosCargados(List<ModeloCargado> models) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ModeloCargado(String name, String model) {
    }

    /** Una línea de la respuesta en vivo de Ollama ({@code stream: true}). */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Fragmento(String response, boolean done, @JsonProperty("done_reason") String doneReason,
                             String error) {
    }
}
