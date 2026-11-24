package co.sena.sicot.service;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EmailServiceTest {

    /**
     * Desde el 05-11-2026 cada cuenta cambia su propia contraseña desde el menú
     * de su usuario (PUT /api/cuenta/contrasena). El correo seguía diciendo que
     * había que pedírsela al administrador, y quien la recibía por correo se
     * quedaba con esa. Ahora dice dónde cambiarla, y sigue diciendo a quién
     * acudir si la olvida.
     */
    @Test
    void elCorreoDeCredencialesDiceDondeCambiarLaContrasenaTemporal() throws Exception {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        when(mailSender.createMimeMessage()).thenReturn(new MimeMessage(Session.getInstance(new Properties())));
        EmailService servicio = new EmailService(mailSender);
        ReflectionTestUtils.setField(servicio, "remitenteConfigurado", "sicot@sena.edu.co");
        ReflectionTestUtils.setField(servicio, "from", "sicot@sena.edu.co");

        servicio.enviarCredenciales("sup@soy.sena.edu.co", "Ana", "ClaveTest123");

        ArgumentCaptor<MimeMessage> enviado = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(enviado.capture());
        String cuerpo = (String) enviado.getValue().getContent();
        assertThat(cuerpo)
                .contains("ClaveTest123")
                .contains("«Cambiar contraseña»")
                // En el teléfono el nombre no se ve; y la 0.8.0 publicada no trae la opción.
                .contains("el círculo con su inicial")
                .contains("es una versión anterior al 05-11-2026: actualícelo")
                .contains("pídale al administrador de SICOT que le asigne una nueva")
                // De usted, como el aviso de designación que llega junto con él.
                .doesNotContain("tus datos")
                .doesNotContain("cámbiala");
    }

    /**
     * El servidor del Centro llevaba desde el primer despliegue con
     * {@code MAIL_USERNAME=replace_me@gmail.com}, el marcador de
     * {@code .env.example}. No está en blanco, así que pasaba por configuración:
     * el sistema se creía con correo y, en vez de descartar las tareas con un
     * motivo claro, habría intentado autenticarse en Gmail con una contraseña
     * falsa — y el primero en notarlo habría sido un supervisor esperando unas
     * credenciales que nunca iban a llegar.
     */
    @Test
    void elMarcadorDeEjemploSinRellenarNoCuentaComoCorreoConfigurado() {
        EmailService servicio = new EmailService(mock(JavaMailSender.class));
        ReflectionTestUtils.setField(servicio, "remitenteConfigurado", "replace_me@gmail.com");

        assertThat(servicio.estaConfigurado()).isFalse();
    }

    @Test
    void sinRemitenteTampocoCuenta() {
        EmailService servicio = new EmailService(mock(JavaMailSender.class));
        ReflectionTestUtils.setField(servicio, "remitenteConfigurado", "   ");

        assertThat(servicio.estaConfigurado()).isFalse();
    }

    @Test
    void unaCuentaDeVerdadSiCuenta() {
        EmailService servicio = new EmailService(mock(JavaMailSender.class));
        ReflectionTestUtils.setField(servicio, "remitenteConfigurado", "notificaciones@sena.edu.co");

        assertThat(servicio.estaConfigurado()).isTrue();
    }
}
