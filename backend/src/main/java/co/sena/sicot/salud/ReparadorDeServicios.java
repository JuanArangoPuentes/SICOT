package co.sena.sicot.salud;

import co.sena.sicot.ia.OllamaClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Lo que el sistema intenta por su cuenta antes de molestar a una persona.
 *
 * <h2>Por qué merece la pena intentarlo</h2>
 * Porque la causa más común de que la IA falle en este despliegue no es que
 * Ollama esté roto: es que descargó el modelo de memoria. El keep-alive lo
 * mantiene cargado media hora, y la primera pregunta después de un rato largo
 * paga la carga entera —224 s medidos el 18-10-2026— y se pasa del tiempo
 * límite. Una petición de carga, que es lo que hace
 * {@link OllamaClient#intentarLevantar()}, deja el modelo listo y la siguiente
 * pregunta funciona. Avisar al técnico de eso sería hacerle perder el viaje.
 *
 * <h2>Lo que no hace</h2>
 * No reinicia contenedores ni toca el sistema. Desde dentro de un contenedor,
 * reiniciar al vecino exige el socket de Docker, y darle eso al backend es
 * darle el equipo entero: cualquier fallo de seguridad en la aplicación pasaría
 * a ser control total del servidor. Lo que no se puede arreglar con una
 * petición HTTP se le cuenta al técnico con el comando exacto, y lo decide él.
 *
 * <p>Tampoco reintenta lo que no se puede reparar desde aquí: un servidor de
 * correo que rechaza la conexión o un respaldo que no se está ejecutando no se
 * arreglan insistiendo, así que {@link Servicio#sePuedeIntentarReparar()} dice
 * cuáles tiene sentido tocar.
 *
 * <h2>Cuándo corre</h2>
 * Cada dos minutos, y <b>solo si hay algo abierto</b>: con todo en orden este
 * componente no hace ni una llamada. El intervalo es corto a propósito, porque
 * mientras el servicio esté caído la interfaz le está enseñando un aviso al
 * usuario, y ese aviso tiene que desaparecer solo en cuanto deje de ser verdad.
 */
@Component
public class ReparadorDeServicios {

    private static final Logger log = LoggerFactory.getLogger(ReparadorDeServicios.class);

    private final SaludDelSistema salud;
    private final OllamaClient ollama;

    public ReparadorDeServicios(SaludDelSistema salud, OllamaClient ollama) {
        this.salud = salud;
        this.ollama = ollama;
    }

    @Scheduled(fixedDelay = 2, initialDelay = 2, timeUnit = TimeUnit.MINUTES)
    public void intentarLevantarLoCaido() {
        Map<Servicio, SaludDelSistema.Incidencia> abiertas = salud.incidenciasAbiertas();
        if (abiertas.isEmpty()) {
            return;
        }

        for (Map.Entry<Servicio, SaludDelSistema.Incidencia> entrada : abiertas.entrySet()) {
            Servicio servicio = entrada.getKey();
            if (!servicio.sePuedeIntentarReparar()) {
                continue;
            }
            try {
                reparar(servicio, entrada.getValue());
            } catch (RuntimeException e) {
                // Un reparador que explota deja de reparar el resto. Se anota y
                // se sigue con el siguiente servicio.
                log.warn("El intento de reparar {} falló de forma inesperada: {}",
                        servicio.etiqueta(), e.toString());
            }
        }
    }

    private void reparar(Servicio servicio, SaludDelSistema.Incidencia incidencia) {
        salud.anotarReparacion(servicio);
        log.info("Intentando levantar {} (lleva {} min, {} fallo(s), {} intento(s) antes).",
                servicio.etiqueta(), incidencia.duracion().toMinutes(),
                incidencia.fallosSeguidos(), incidencia.reparaciones());

        boolean levantado = switch (servicio) {
            case IA -> ollama.intentarLevantar();
            // Los demás no declaran que se puedan reparar, así que no se llega
            // aquí. El switch es exhaustivo a propósito: si mañana alguien
            // marca un servicio nuevo como reparable, el compilador obliga a
            // decir CÓMO se repara en vez de dejarlo fallando en silencio.
            case CORREO, RESPALDO, ALMACENAMIENTO -> false;
        };

        if (levantado) {
            // Esto es lo que cierra la incidencia, quita el aviso al usuario y
            // le dice al técnico que ya no hace falta que venga.
            salud.exito(servicio);
        } else {
            salud.fallo(servicio, "el intento de reparación automática tampoco funcionó");
        }
    }
}
