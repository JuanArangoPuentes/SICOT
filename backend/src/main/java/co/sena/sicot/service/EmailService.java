package co.sena.sicot.service;

import co.sena.sicot.salud.AvisoDeSalud;
import co.sena.sicot.salud.Servicio;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Autowired;
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

    /**
     * Para que un correo que dejó de salir se sepa. Lo nota el supervisor que no
     * recibe el aviso de designación, y él no sabe que debía llegarle: es
     * justamente el tipo de fallo que no se descubre solo.
     */
    private final AvisoDeSalud salud;

    @Value("${spring.mail.username:}")
    private String remitenteConfigurado;

    @Value("${sicot.mail.from:${spring.mail.username:}}")
    private String from;

    /** Sin avisar de la salud a nadie: la forma que usan las pruebas. */
    public EmailService(JavaMailSender mailSender) {
        this(mailSender, AvisoDeSalud.NINGUNO);
    }

    @Autowired
    public EmailService(JavaMailSender mailSender, AvisoDeSalud salud) {
        this.mailSender = mailSender;
        this.salud = salud;
    }

    /** @return si hay un remitente configurado con el que se pueda enviar algo. */
    public boolean estaConfigurado() {
        return remitenteConfigurado != null && !remitenteConfigurado.isBlank();
    }

    /**
     * Correo con los datos de acceso, tanto al crear la cuenta como al
     * restablecer la contraseña.
     *
     * <p>Hasta el 05-11-2026 SICOT no tenía cómo cambiar la contraseña propia, y
     * este correo decía, con razón, que había que pedirle una nueva al
     * administrador. Desde ese día existe «Cambiar contraseña» en el menú del
     * usuario (PUT /api/cuenta/contrasena), pero el correo siguió mandando a
     * pedirla: el supervisor recién invitado se quedaba con la temporal, que
     * además viajó por correo. El texto dice ahora dónde cambiarla al entrar.
     *
     * <p>Dice qué buscar en el teléfono (allí el nombre no se ve, solo el
     * círculo con la inicial) y qué hacer si la opción no aparece: el APK y el
     * instalador publicados hasta la 0.8.0 son anteriores a «Cambiar
     * contraseña», y el aviso de designación enlaza a la última versión.
     *
     * <p>Trata de usted, como el aviso de designación
     * ({@code NotificacionDeSupervisorAsignado}): al supervisor que Gestión
     * invita le llegan los dos a la vez, y uno tuteaba y el otro no.
     */
    public void enviarCredenciales(String destinatario, String nombre, String password) {
        enviar(destinatario,
                "SICOT — Credenciales de acceso",
                "Hola " + nombre + ",\n\n"
                        + "Estos son sus datos de acceso a SICOT (Sistema Inteligente para la Gestión y "
                        + "Acompañamiento de Contratos).\n\n"
                        + "Correo: " + destinatario + "\n"
                        + "Contraseña: " + password + "\n\n"
                        + "Es una contraseña temporal: cámbiela al entrar, desde el menú de su usuario (arriba a la "
                        + "derecha: su nombre, o en el teléfono el círculo con su inicial) → «Cambiar contraseña». "
                        + "Si su SICOT no muestra esa opción, es una versión anterior al 05-11-2026: actualícelo. "
                        + "Si la olvida, o cree que otra persona la conoce, pídale al administrador de SICOT que le "
                        + "asigne una nueva: al hacerlo se cierran las sesiones abiertas con la anterior.\n\n"
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
            salud.exito(Servicio.CORREO);
        } catch (Exception e) {
            // El fallo de envío sí cuenta como caída del servicio: una
            // contraseña de aplicación caducada deja de enviar para siempre y
            // nadie se enteraría, porque quien lo nota es el supervisor que no
            // recibe el aviso de designación, y él no sabe que debía llegarle.
            //
            // Lo que NO se cuenta es el caso de arriba, el de «no está
            // configurado»: eso es una decisión del despliegue —en desarrollo el
            // correo no se configura a propósito— y no una avería. Tratarlo como
            // caída llenaría el log de informes para el técnico en cada equipo de
            // desarrollo.
            salud.fallo(Servicio.CORREO, e.getClass().getSimpleName() + ": " + e.getMessage());
            throw new IllegalStateException("No se pudo enviar el correo: " + e.getMessage(), e);
        }
    }
}
