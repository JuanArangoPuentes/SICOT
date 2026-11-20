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
         * USUARIO, SISTEMA o AGENTE. Se expone para que la pantalla de
         * auditoría pueda decir «Sistema» donde no hay nombre, en vez de dejar
         * la celda en blanco — que es indistinguible de un dato que falta— y
         * para que una acción hecha por una herramienta automática con la
         * cuenta de alguien no se lea como hecha por esa persona.
         */
        OrigenRegistro origen,
        /**
         * Qué herramienta automática lo hizo, cuando {@link #origen()} es
         * {@code AGENTE}; nula en el resto. El {@code usuarioNombre} sigue
         * siendo la cuenta responsable: lo que esto añade es de quién fue la
         * mano. Ver {@code V26__quien_actuo_fue_un_agente.sql}.
         */
        String agente,
        /**
         * La hora que declaró el equipo si el cambio se hizo sin red y llegó
         * después (ADR-015); nula si se hizo con conexión. {@code fecha} es
         * cuándo lo recibió el servidor: las dos se muestran, cada una con su
         * nombre, porque solo la segunda la garantiza SICOT.
         */
        Instant hechoSinConexionEn
) {
}
