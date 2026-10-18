package co.sena.sicot.controller;

import co.sena.sicot.dto.ia.ChatResponse;
import co.sena.sicot.exception.BusinessException;
import co.sena.sicot.exception.DemasiadasSolicitudesException;
import co.sena.sicot.exception.ResourceNotFoundException;
import co.sena.sicot.ia.IaNoDisponibleException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;

import java.io.IOException;
import java.io.OutputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Escribe la respuesta en vivo del Copiloto: una línea JSON por evento
 * (NDJSON), que el cliente lee a medida que llega.
 *
 * <h2>Los eventos</h2>
 * <ul>
 *   <li>{@code {"tipo":"inicio"}} — en cuanto empieza, para que las cabeceras
 *       salgan ya: un proxy que corta a los 60-100 s si no recibe nada (nginx,
 *       Cloudflare) no corta una respuesta que ya empezó.</li>
 *   <li>{@code {"tipo":"espera"}} — cada 10 s mientras el modelo lee el prompt
 *       y todavía no escribe; en el portátil del Centro eso puede durar
 *       minutos la primera vez.</li>
 *   <li>{@code {"tipo":"texto","t":"…"}} — cada trozo que escribe el modelo.</li>
 *   <li>{@code {"tipo":"fin","respuesta":{…}}} — la {@link ChatResponse}
 *       completa, la misma que da {@code POST /chat}. Manda sobre los trozos:
 *       una respuesta de un atajo, o de un reintento que recoge la del primer
 *       intento, llega solo aquí.</li>
 *   <li>{@code {"tipo":"error","status":503,"mensaje":"…"}} — el mismo código y
 *       el mismo texto que daría {@code POST /chat}.</li>
 * </ul>
 *
 * <h2>Si el cliente se va</h2>
 * En el teléfono, Android cierra las conexiones de la aplicación a los pocos
 * segundos de salir de ella. Entonces se deja de escribir, pero la respuesta se
 * sigue calculando: queda guardada por {@code idSolicitud} y el reintento la
 * recoge sin pagar otra inferencia (ver {@code CopilotoChatService.atender}).
 */
final class RespuestaEnVivoDelCopiloto {

    static final MediaType NDJSON = MediaType.parseMediaType("application/x-ndjson");

    private static final Logger log = LoggerFactory.getLogger(RespuestaEnVivoDelCopiloto.class);

    private static final long SEGUNDOS_ENTRE_ESPERAS = 10;

    /** Un solo hilo para todos los avisos de espera: solo escribe una línea cada 10 s por respuesta abierta. */
    private static final ScheduledExecutorService AVISOS = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "copiloto-en-vivo");
        t.setDaemon(true);
        return t;
    });

    private final OutputStream salida;
    private final ObjectMapper json;
    private boolean cerrada;
    private boolean clienteSeFue;
    private volatile boolean yaEscribe;

    RespuestaEnVivoDelCopiloto(OutputStream salida, ObjectMapper json) {
        this.salida = salida;
        this.json = json;
    }

    /** Corre el trabajo y escribe sus eventos. No lanza: todo fallo termina en un evento de error. */
    void atender(Supplier<ChatResponse> trabajo) {
        evento(Map.of("tipo", "inicio"));
        ScheduledFuture<?> esperas = AVISOS.scheduleAtFixedRate(() -> {
            if (!yaEscribe) {
                evento(Map.of("tipo", "espera"));
            }
        }, SEGUNDOS_ENTRE_ESPERAS, SEGUNDOS_ENTRE_ESPERAS, TimeUnit.SECONDS);
        try {
            ChatResponse respuesta = trabajo.get();
            esperas.cancel(false);
            Map<String, Object> fin = new LinkedHashMap<>();
            fin.put("tipo", "fin");
            fin.put("respuesta", respuesta);
            ultimo(fin);
        } catch (DemasiadasSolicitudesException e) {
            esperas.cancel(false);
            ultimo(error(429, e.getMessage(), e.getSegundosDeEspera()));
        } catch (IaNoDisponibleException e) {
            esperas.cancel(false);
            log.warn("Servicio de IA no disponible en la respuesta en vivo del Copiloto: {}", e.getMessage());
            ultimo(error(503, e.getMessage(), null));
        } catch (ResourceNotFoundException e) {
            esperas.cancel(false);
            ultimo(error(404, e.getMessage(), null));
        } catch (BusinessException e) {
            esperas.cancel(false);
            ultimo(error(400, e.getMessage(), null));
        } catch (AccessDeniedException e) {
            esperas.cancel(false);
            ultimo(error(403, "No tiene permisos para realizar esta operación.", null));
        } catch (RuntimeException e) {
            esperas.cancel(false);
            log.error("Error interno en la respuesta en vivo del Copiloto", e);
            ultimo(error(500, "Ocurrió un error interno del servidor.", null));
        }
    }

    /** Un trozo del texto del modelo. */
    void texto(String trozo) {
        yaEscribe = true;
        evento(Map.of("tipo", "texto", "t", trozo));
    }

    private static Map<String, Object> error(int status, String mensaje, Long segundosDeEspera) {
        Map<String, Object> e = new LinkedHashMap<>();
        e.put("tipo", "error");
        e.put("status", status);
        e.put("mensaje", mensaje);
        if (segundosDeEspera != null) {
            e.put("reintentarEnSegundos", segundosDeEspera);
        }
        return e;
    }

    private synchronized void ultimo(Map<String, Object> evento) {
        evento(evento);
        cerrada = true;
    }

    private synchronized void evento(Map<String, Object> evento) {
        // Tras el último evento la respuesta del servidor ya terminó: un aviso
        // de espera que llegara tarde escribiría en una respuesta cerrada.
        if (cerrada || clienteSeFue) {
            return;
        }
        try {
            salida.write(json.writeValueAsBytes(evento));
            salida.write('\n');
            salida.flush();
        } catch (IOException e) {
            clienteSeFue = true;
            log.info("El cliente cerró la respuesta en vivo del Copiloto; la respuesta se sigue calculando "
                    + "para que el reintento la recoja.");
        }
    }
}
