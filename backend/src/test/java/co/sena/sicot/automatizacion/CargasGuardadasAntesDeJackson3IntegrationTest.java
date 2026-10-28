package co.sena.sicot.automatizacion;

import co.sena.sicot.PruebaDeIntegracion;
import co.sena.sicot.entity.enums.PrioridadAlerta;
import co.sena.sicot.entity.enums.TipoAlerta;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Las cargas de las tareas automatizadas que ya están en la base las escribió
 * Jackson 2; desde la migración a Spring Boot 4 (ADR-014) las lee Jackson 3.
 *
 * <p>Las demás pruebas del motor escriben y leen en la misma corrida, con el
 * mismo Jackson, así que no verían un cambio de formato entre versiones. Estas
 * tres cadenas son las formas reales de {@code tareas_automatizadas.payload},
 * copiadas de la base de desarrollo el 28-10-2026 (una por tipo; el correo,
 * recortado). Se leen con el {@link PayloadJson} de la aplicación, es decir con
 * el mapeador que configura Boot, que es el que lee las filas de verdad.
 */
class CargasGuardadasAntesDeJackson3IntegrationTest extends PruebaDeIntegracion {

    @Autowired
    private PayloadJson payloadJson;

    @Test
    void unaAlertaGuardadaSeLeeIgual() {
        PayloadDeTarea.CrearAlerta alerta = payloadJson.leer("""
                {"tipoAlerta":"SOLICITUD","prioridad":"MEDIA","mensaje":"Fue designado supervisor del contrato \
                CO1.PCCNTR.99015001. Su primer paso: firmar y devolver el Acta de Inicio que le envía Contratación, \
                y cargarla aquí con la evidencia del envío."}""", PayloadDeTarea.CrearAlerta.class);

        assertThat(alerta.tipoAlerta()).isEqualTo(TipoAlerta.SOLICITUD);
        assertThat(alerta.prioridad()).isEqualTo(PrioridadAlerta.MEDIA);
        assertThat(alerta.mensaje()).startsWith("Fue designado supervisor").endsWith("evidencia del envío.");
    }

    @Test
    void unCorreoGuardadoSeLeeIgualConSusSaltosDeLinea() {
        PayloadDeTarea.EnviarCorreo correo = payloadJson.leer("""
                {"destinatario":"supervisor@soy.sena.edu.co",\
                "asunto":"SICOT — Se le asignó la supervisión del contrato CO1.PCCNTR.99015001",\
                "cuerpo":"Hola Alex Fernando Zapata,\\n\\nUsted fue designado(a) supervisor(a) del contrato \
                CO1.PCCNTR.99015001."}""", PayloadDeTarea.EnviarCorreo.class);

        assertThat(correo.destinatario()).isEqualTo("supervisor@soy.sena.edu.co");
        assertThat(correo.asunto()).isEqualTo("SICOT — Se le asignó la supervisión del contrato CO1.PCCNTR.99015001");
        assertThat(correo.cuerpo()).startsWith("Hola Alex Fernando Zapata,\n\nUsted fue designado(a)");
    }

    @Test
    void unResumenGuardadoSeLeeIgual() {
        assertThat(payloadJson.leer("{\"diasDelPeriodo\":7}", PayloadDeTarea.RedactarResumen.class).diasDelPeriodo())
                .isEqualTo(7);
    }

    /** Y lo que escribe Jackson 3 vuelve a leerse: las filas nuevas conviven con las viejas. */
    @Test
    void loQueSeEscribeAhoraSeLeeIgual() {
        PayloadDeTarea.CrearAlerta original =
                new PayloadDeTarea.CrearAlerta(TipoAlerta.SOLICITUD, PrioridadAlerta.ALTA, "Mensaje con tilde: acción");

        assertThat(payloadJson.leer(payloadJson.escribir(original), PayloadDeTarea.CrearAlerta.class))
                .isEqualTo(original);
    }
}
