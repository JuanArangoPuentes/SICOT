package co.sena.sicot.automatizacion;

import co.sena.sicot.entity.enums.PrioridadAlerta;
import co.sena.sicot.entity.enums.TipoAlerta;
import co.sena.sicot.entity.enums.TipoTareaAutomatizada;

/**
 * Los datos que necesita cada efecto para ejecutarse.
 *
 * <h2>Por qué sellada y por qué cada variante declara su tipo</h2>
 * La columna {@code tipo} de la cola y la forma del JSON de {@code payload}
 * tienen que coincidir siempre: una fila que dice {@code ENVIAR_CORREO} pero
 * lleva el JSON de una alerta no falla al guardarse, falla al ejecutarse, y para
 * entonces ya está en la base y se reintentará cinco veces.
 *
 * <p>Que cada variante devuelva su propio {@link #tipo()} hace que esa
 * discordancia no se pueda escribir: quien encola no elige el tipo, lo lee del
 * payload. Y al ser sellada, añadir un efecto nuevo sin darle ejecutor rompe la
 * compilación del {@code switch} del ejecutor en vez de fallar en tiempo de
 * ejecución.
 */
public sealed interface PayloadDeTarea {

    TipoTareaAutomatizada tipo();

    /**
     * Persistir una alerta en el contrato.
     *
     * <p>No lleva el contrato: ese ya viaja en la propia tarea, y duplicarlo
     * permitiría que la fila y su payload apuntaran a contratos distintos.
     */
    record CrearAlerta(TipoAlerta tipoAlerta, PrioridadAlerta prioridad, String mensaje)
            implements PayloadDeTarea {

        @Override
        public TipoTareaAutomatizada tipo() {
            return TipoTareaAutomatizada.CREAR_ALERTA;
        }
    }

    /** Enviar un correo real a una persona. */
    record EnviarCorreo(String destinatario, String asunto, String cuerpo)
            implements PayloadDeTarea {

        @Override
        public TipoTareaAutomatizada tipo() {
            return TipoTareaAutomatizada.ENVIAR_CORREO;
        }
    }

    /**
     * Pedir al modelo local que redacte un resumen del periodo.
     *
     * <p>El payload solo dice <b>qué periodo</b>. Los hechos los recoge el
     * ejecutor de la base en el momento de correr la tarea, no la regla al
     * encolarla: entre una cosa y otra pueden pasar horas, y un resumen que
     * describe el contrato tal como estaba cuando se encoló sería un resumen
     * falso presentado como actual.
     */
    record RedactarResumenIa(int diasDelPeriodo) implements PayloadDeTarea {

        @Override
        public TipoTareaAutomatizada tipo() {
            return TipoTareaAutomatizada.REDACTAR_RESUMEN_IA;
        }
    }
}
