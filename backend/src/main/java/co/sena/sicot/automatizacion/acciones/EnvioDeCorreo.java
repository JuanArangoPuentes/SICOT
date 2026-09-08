package co.sena.sicot.automatizacion.acciones;

import co.sena.sicot.automatizacion.AccionDeTarea;
import co.sena.sicot.automatizacion.PayloadJson;
import co.sena.sicot.automatizacion.PayloadDeTarea;
import co.sena.sicot.automatizacion.ResultadoDeAccion;
import co.sena.sicot.automatizacion.TareaEnEjecucion;
import co.sena.sicot.entity.enums.TipoTareaAutomatizada;
import co.sena.sicot.service.EmailService;
import org.springframework.stereotype.Component;

/**
 * Envía el correo que pidió una regla.
 *
 * <h2>Por qué comprueba la configuración antes de intentarlo</h2>
 * Un despliegue sin SMTP es una decisión legítima, y hoy es lo normal en las
 * máquinas de desarrollo del equipo. Sin esta comprobación, cada aviso al
 * supervisor se intentaría cinco veces con espera creciente y terminaría en
 * FALLIDA: la pantalla de operación se llenaría de errores rojos que no
 * corresponden a nada roto, y el primer fallo real quedaría enterrado entre
 * ellos.
 *
 * <p>Marcarlas DESCARTADA dice la verdad —«no se envió, y esta es la razón»—
 * sin fabricar una alarma. Es la misma línea de honestidad que el resto del
 * sistema: {@code EmailService} ya falla explícitamente en vez de fingir que
 * envió, y {@code OllamaClient} responde 503 en vez de inventar una respuesta.
 */
@Component
public class EnvioDeCorreo implements AccionDeTarea {

    private final EmailService emailService;
    private final PayloadJson payloadJson;

    public EnvioDeCorreo(EmailService emailService, PayloadJson payloadJson) {
        this.emailService = emailService;
        this.payloadJson = payloadJson;
    }

    @Override
    public TipoTareaAutomatizada tipo() {
        return TipoTareaAutomatizada.ENVIAR_CORREO;
    }

    @Override
    public ResultadoDeAccion ejecutar(TareaEnEjecucion tarea) {
        if (!emailService.estaConfigurado()) {
            return ResultadoDeAccion.descartada(
                    "No se envió porque el correo saliente no está configurado (MAIL_USERNAME vacío). "
                            + "No es un fallo del sistema: configure las variables MAIL_* para activarlo.");
        }

        PayloadDeTarea.EnviarCorreo datos =
                payloadJson.leer(tarea.payload(), PayloadDeTarea.EnviarCorreo.class);

        // Sin captura: un fallo del servidor SMTP suele ser transitorio y el
        // ejecutor ya sabe reintentarlo con espera creciente. Capturarlo aquí
        // para devolver "descartada" convertiría un mal minuto del correo en un
        // aviso al supervisor que nadie volverá a intentar.
        emailService.enviar(datos.destinatario(), datos.asunto(), datos.cuerpo());
        return ResultadoDeAccion.hecha();
    }
}
