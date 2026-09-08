package co.sena.sicot.dto.registro;

import co.sena.sicot.entity.enums.OrigenRegistro;

import java.time.Instant;

public record RegistroResponse(
        Long id,
        Long contratoId,
        Long usuarioId,
        String usuarioNombre,
        String accion,
        String descripcion,
        Instant fecha,
        /**
         * USUARIO o SISTEMA. Se expone para que la pantalla de auditoría pueda
         * decir «Sistema» donde no hay nombre, en vez de dejar la celda en
         * blanco — que es indistinguible de un dato que falta.
         */
        OrigenRegistro origen
) {
}
