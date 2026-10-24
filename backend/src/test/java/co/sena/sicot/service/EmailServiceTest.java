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
     * SICOT no tiene ninguna pantalla ni endpoint para que una persona cambie su
     * propia contraseña. El correo pedía hacerlo «la primera vez que ingreses»:
     * una instrucción del propio sistema que nadie podía cumplir.
     */
    @Test
    void elCorreoDeCredencialesNoPideAlgoQueElSistemaNoPermite() throws Exception {
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
                .doesNotContainIgnoringCase("cambiala")
                .doesNotContainIgnoringCase("temporal")
                .contains("pídele que te asigne una nueva");
    }
}
