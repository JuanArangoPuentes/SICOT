package co.sena.sicot.automatizacion;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

/**
 * Traduce entre {@link PayloadDeTarea} y el texto que se guarda en la columna
 * {@code payload}.
 *
 * <h2>Por qué es una clase aparte y no un método del almacén</h2>
 * El almacén es el dueño de las transacciones sobre la cola; la serialización no
 * tiene nada que ver con eso. Tenerla ahí obligaba además a que las acciones
 * —que viven en otro paquete— dependieran del almacén solo para leer su propio
 * payload, cuando no necesitan tocar la base para nada.
 *
 * <h2>Por qué no se usa información de tipo polimórfica de Jackson</h2>
 * Sería la forma habitual de deserializar una jerarquía sellada, pero exigiría
 * escribir el nombre de la clase Java dentro del JSON. Ese dato quedaría
 * guardado en la base de datos: renombrar o mover una clase rompería las filas
 * ya escritas, y un refactor inocente dejaría tareas imposibles de leer.
 *
 * <p>Aquí el tipo lo dice la columna {@code tipo}, que es un enum estable y
 * validado por un CHECK. Quien deserializa ya sabe qué espera, así que basta con
 * pedirlo.
 */
@Component
public class PayloadJson {

    private final ObjectMapper objectMapper;

    public PayloadJson(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String escribir(PayloadDeTarea payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            // Solo puede pasar si alguien añade una variante no serializable.
            // Es un error de programación, no una condición de ejecución.
            throw new IllegalStateException("No se pudo serializar el payload de la tarea.", e);
        }
    }

    /**
     * @throws IllegalStateException si lo guardado no corresponde al tipo pedido.
     *         Es un fallo de datos, no transitorio: reintentarlo dará el mismo
     *         resultado, y el ejecutor acabará marcando la tarea FALLIDA con este
     *         mensaje — que es exactamente lo que hace falta para diagnosticarlo.
     */
    public <T extends PayloadDeTarea> T leer(String json, Class<T> tipo) {
        try {
            return objectMapper.readValue(json, tipo);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(
                    "El payload guardado no corresponde a " + tipo.getSimpleName() + ": " + e.getOriginalMessage(), e);
        }
    }
}
