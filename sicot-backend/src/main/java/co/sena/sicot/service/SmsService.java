package co.sena.sicot.service;

import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Envío real de SMS (credenciales de cuentas nuevas) vía Twilio. Si las credenciales de Twilio
 * no están configuradas, falla de forma honesta en vez de fingir que se envió — ver
 * UsuarioService.
 */
@Service
public class SmsService {

    @Value("${sicot.sms.account-sid:}")
    private String accountSid;

    @Value("${sicot.sms.auth-token:}")
    private String authToken;

    @Value("${sicot.sms.from-number:}")
    private String fromNumber;

    public void enviarCredenciales(String telefono, String nombre, String password) {
        if (accountSid == null || accountSid.isBlank()
                || authToken == null || authToken.isBlank()
                || fromNumber == null || fromNumber.isBlank()) {
            throw new IllegalStateException(
                    "El SMS no está configurado (TWILIO_ACCOUNT_SID/TWILIO_AUTH_TOKEN/TWILIO_FROM_NUMBER vacíos). Configure las variables en el .env.");
        }
        if (telefono == null || telefono.isBlank()) {
            throw new IllegalStateException("El usuario no tiene un número de teléfono registrado.");
        }
        try {
            Twilio.init(accountSid, authToken);
            String texto = "SICOT: hola " + nombre + ", tu contraseña temporal es " + password
                    + ". Cambiala al iniciar sesion por primera vez.";
            Message.creator(new PhoneNumber(normalizar(telefono)), new PhoneNumber(fromNumber), texto).create();
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo enviar el SMS: " + e.getMessage(), e);
        }
    }

    /** Convierte a formato E.164 (+57...) el número tal como lo escribió el administrador. */
    private String normalizar(String telefono) {
        String digitos = telefono.replaceAll("[^0-9+]", "");
        if (digitos.startsWith("+")) {
            return digitos;
        }
        return "+57" + digitos;
    }
}
