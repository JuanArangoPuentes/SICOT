package co.sena.sicot.service;

import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

/**
 * Envío real de correo. Si {@code spring.mail.username} no está configurado,
 * falla de forma honesta en vez de fingir que se envió — ver UsuarioService.
 *
 * <h2>Por qué existe {@link #estaConfigurado()}</h2>
 * Porque «no configurado» y «falló el envío» son dos cosas distintas y el motor
 * de automatizaciones necesita distinguirlas. Un despliegue sin SMTP es una
 * decisión legítima; si las tareas de correo lo trataran como un fallo, cada una
 * se reintentaría cinco veces con espera creciente y acabaría en FALLIDA,
 * llenando la pantalla de operación de errores rojos que no indican nada roto.
 *
 * <p>Con esta pregunta contestable de antemano, esas tareas se marcan
 * DESCARTADA con un motivo claro y nadie sale a buscar un problema que no
 * existe. Ver {@code EstadoTareaAutomatizada}.
 */
@Service
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:}")
    private String remitenteConfigurado;

    @Value("${sicot.mail.from:${spring.mail.username:}}")
    private String from;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    /** @return si hay un remitente configurado con el que se pueda enviar algo. */
    public boolean estaConfigurado() {
        return remitenteConfigurado != null && !remitenteConfigurado.isBlank();
    }

    public void enviarCredenciales(String destinatario, String nombre, String password) {
        enviar(destinatario,
                "SICOT — Credenciales de acceso",
                "Hola " + nombre + ",\n\n"
                        + "Se creó tu cuenta en SICOT (Sistema Inteligente para la Gestión y Acompañamiento de Contratos).\n\n"
                        + "Correo: " + destinatario + "\n"
                        + "Contraseña temporal: " + password + "\n\n"
                        + "Por seguridad, cambiala la primera vez que ingreses al sistema.\n\n"
                        + "— SICOT · Centro Tecnológico del Mobiliario (SENA)");
    }

    /**
     * Envío genérico. Es el único punto del sistema que habla con el servidor de
     * correo: las credenciales de una cuenta nueva y los avisos del motor de
     * automatizaciones pasan los dos por aquí, de modo que el remitente, la
     * codificación y el modo de fallar se deciden una sola vez.
     */
    public void enviar(String destinatario, String asunto, String cuerpo) {
        if (!estaConfigurado()) {
            throw new IllegalStateException(
                    "El correo no está configurado (MAIL_USERNAME vacío). Configure las variables MAIL_* en el .env.");
        }
        try {
            MimeMessage mensaje = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mensaje, "UTF-8");
            helper.setTo(destinatario);
            helper.setFrom(from);
            helper.setSubject(asunto);
            helper.setText(cuerpo);
            mailSender.send(mensaje);
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo enviar el correo: " + e.getMessage(), e);
        }
    }
}
