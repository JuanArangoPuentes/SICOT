package co.sena.sicot.dto.contrato;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Lo que Gestión escribe para poner un supervisor en un contrato cuando ese
 * supervisor todavía no tiene cuenta en SICOT.
 *
 * <h2>Por qué hace falta</h2>
 * Hasta el 26-10-2026 Gestión solo podía asignar un supervisor que ya
 * existiera: crear la cuenta era de ADMINISTRADOR, enviarle la clave era otra
 * acción de ADMINISTRADOR, y asignar el contrato era una tercera. Tres
 * personas-pasos para lo que el Centro vive como uno solo: «a este contrato le
 * pongo a Fulano, que es este correo». Entre medias, el supervisor nuevo no
 * recibía nada y nadie se enteraba de que faltaba un paso.
 *
 * <p>El teléfono se pide porque el usuario lo lleva como dato obligatorio
 * desde siempre. <b>No se le manda nada por ahí</b>: SICOT no envía mensajes
 * de texto y no los va a enviar mientras eso exija una pasarela de pago.
 */
public record InvitarSupervisorRequest(
        @NotBlank(message = "El nombre del supervisor es obligatorio.")
        @Size(max = 150, message = "El nombre no puede superar 150 caracteres.")
        String nombre,

        @NotBlank(message = "El correo del supervisor es obligatorio: es a donde le llegan sus datos de acceso.")
        @Email(message = "Debe ser un correo válido.")
        @Size(max = 150, message = "El correo no puede superar 150 caracteres.")
        String email,

        @NotBlank(message = "El teléfono del supervisor es obligatorio.")
        @Size(max = 20, message = "El teléfono no puede superar 20 caracteres.")
        String telefono
) {
}
