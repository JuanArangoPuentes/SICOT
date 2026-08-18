package co.sena.sicot.service;

import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

/**
 * Envío real de correo (credenciales de cuentas nuevas). Si spring.mail.username no está
 * configurado, falla de forma honesta en vez de fingir que se envió — ver UsuarioService.
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

    public void enviarCredenciales(String destinatario, String nombre, String password) {
        if (remitenteConfigurado == null || remitenteConfigurado.isBlank()) {
            throw new IllegalStateException(
                    "El correo no está configurado (MAIL_USERNAME vacío). Configure las variables MAIL_* en el .env.");
        }
        try {
            MimeMessage mensaje = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mensaje, "UTF-8");
            helper.setTo(destinatario);
            helper.setFrom(from);
            helper.setSubject("SICOT — Credenciales de acceso");
            helper.setText(
                    "Hola " + nombre + ",\n\n"
                            + "Se creó tu cuenta en SICOT (Sistema Inteligente para la Gestión y Acompañamiento de Contratos).\n\n"
                            + "Correo: " + destinatario + "\n"
                            + "Contraseña temporal: " + password + "\n\n"
                            + "Por seguridad, cambiala la primera vez que ingreses al sistema.\n\n"
                            + "— SICOT · Centro Tecnológico del Mobiliario (SENA)");
            mailSender.send(mensaje);
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo enviar el correo: " + e.getMessage(), e);
        }
    }
}
