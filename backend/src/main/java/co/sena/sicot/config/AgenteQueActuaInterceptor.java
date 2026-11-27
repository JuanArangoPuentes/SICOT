package co.sena.sicot.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Optional;

/**
 * Lee qué herramienta automática dice estar actuando con la cuenta de una
 * persona, para que la auditoría pueda distinguirlo.
 *
 * <h2>Por qué existe</h2>
 * El servidor MCP de {@code mcp/} inicia sesión con las credenciales de una
 * cuenta real y opera como ella: crea contratos, asigna supervisores, cambia
 * estados, cierra sub-pasos. Hasta el 20-11-2026 todo eso quedaba en
 * {@code registros} con {@code origen = USUARIO} y el {@code usuario_id} del
 * funcionario, es decir <b>indistinguible</b> de lo que esa persona hizo con sus
 * manos en la pantalla.
 *
 * <p>Para un sistema cuyo propósito es producir un expediente auditable de
 * contratación pública, esa es precisamente la distinción que no se puede
 * perder. El proyecto ya tiene la regla escrita —la IA es asesora, nunca
 * autoridad— y la auditoría no la sostenía.
 *
 * <h2>Lo que NO es, dicho aquí</h2>
 * No es un control de acceso. Un cliente que no manda la cabecera queda
 * registrado como {@code USUARIO}, igual que antes, así que esto no impide que
 * alguien use la API de forma automática sin declararse. No pretende impedirlo:
 * sirve para que el agente propio del proyecto sea honesto sobre lo que es, y
 * para que exigirlo mañana sea un cambio de una línea. Un control que depende
 * de que el cliente colabore es documentación, no una barrera, y conviene no
 * confundirlo con una.
 *
 * <h2>Por qué un interceptor y no leer la cabecera en el servicio</h2>
 * El mismo motivo que {@link HechoSinConexionInterceptor}: la cabecera es un
 * dato de la petición, no un parámetro de negocio, y hacerla llegar como
 * argumento obligaría a añadirla a la firma de cada método que registra algo —
 * que son casi todos.
 */
@Component
public class AgenteQueActuaInterceptor implements HandlerInterceptor {

    public static final String CABECERA = "X-SICOT-Agente";

    /**
     * Tope de la columna {@code registros.agente}. El valor lo elige el cliente
     * y acaba en una pantalla: sin recortarlo, una cabecera de miles de
     * caracteres entraría en la auditoría y rompería la tabla al mostrarla.
     *
     * <p>Se <b>recorta</b> en vez de rechazar la petición, al contrario que la
     * hora declarada sin conexión. La diferencia es qué se pierde si el dato
     * viene mal: una hora mal formada haría que el expediente afirmara algo
     * falso, así que ahí vale detener el cambio; un nombre de agente demasiado
     * largo sigue identificando al agente por sus primeros 60 caracteres, y
     * rechazar la operación entera por eso sería desproporcionado.
     */
    public static final int MAXIMO = 60;

    static final String ATRIBUTO = AgenteQueActuaInterceptor.class.getName() + ".agente";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String valor = request.getHeader(CABECERA);
        if (valor == null || valor.isBlank()) {
            return true;
        }
        request.setAttribute(ATRIBUTO, limpiar(valor));
        return true;
    }

    /**
     * Recorta, y quita los caracteres de control: el valor va a una pantalla y
     * a un log, y un salto de línea dentro de una entrada de auditoría permite
     * fabricar una línea de log que parezca otra entrada.
     */
    static String limpiar(String valor) {
        String sinControles = valor.strip().replaceAll("\\p{Cc}", " ").strip();
        return sinControles.length() > MAXIMO ? sinControles.substring(0, MAXIMO) : sinControles;
    }

    /**
     * El agente declarado en la petición que se está atendiendo, si la trae.
     * Fuera de una petición (las tareas del motor corren en otro hilo) no hay
     * ninguno.
     */
    public static Optional<String> enLaSolicitudActual() {
        RequestAttributes atributos = RequestContextHolder.getRequestAttributes();
        if (atributos == null) {
            return Optional.empty();
        }
        return Optional.ofNullable((String) atributos.getAttribute(ATRIBUTO, RequestAttributes.SCOPE_REQUEST))
                .filter(valor -> !valor.isBlank());
    }
}
