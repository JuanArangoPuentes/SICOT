package co.sena.sicot.automatizacion;

import co.sena.sicot.entity.enums.EstadoContrato;
import co.sena.sicot.service.Cronograma;

import java.time.LocalDate;

/**
 * Todo lo que una regla de calendario necesita saber de un contrato, ya leído.
 *
 * <h2>Por qué no se les pasa la entidad {@code Contrato}</h2>
 * Por dos motivos, y el segundo es el que de verdad importa.
 *
 * <p><b>Uno: no se puede navegar.</b> Una regla que quisiera contar subetapas
 * completadas tendría que recorrer {@code contrato → etapas → subetapas}, todo
 * perezoso. Fuera de la sesión de Hibernate eso revienta; dentro, dispara una
 * consulta por contrato y por etapa. Con los conteos ya resueltos en una sola
 * consulta agrupada, el problema no existe.
 *
 * <p><b>Dos: las reglas dejan de poder escribir.</b> Con la entidad delante,
 * nada impide que una regla llame a un {@code set} y que Hibernate lo confirme
 * sola al cerrar la sesión — una escritura que no pasó por ningún servicio de
 * dominio, sin validación, sin auditoría y sin que nadie la haya escrito
 * a propósito. Con un record inmutable, la regla invariante «ninguna regla
 * escribe a la base» deja de depender de la disciplina de quien la escribe y
 * pasa a estar garantizada por el tipo.
 *
 * <p>El efecto secundario es que probar una regla es construir uno de estos
 * records y comparar la lista devuelta. Sin Spring, sin base y sin dobles.
 *
 * @param subetapasTotales     las 27 de GCCON-P-010 en un contrato bien sembrado
 * @param subetapasCompletadas cuántas de ellas están cerradas
 */
public record FotoDelContrato(
        Long contratoId,
        String numeroContrato,
        String objeto,
        LocalDate fechaInicio,
        LocalDate fechaFin,
        EstadoContrato estado,
        String supervisorNombre,
        String supervisorEmail,
        long subetapasTotales,
        long subetapasCompletadas,
        Integer etapaActual,
        int totalEtapas
) {

    public boolean tieneSupervisorConCorreo() {
        return supervisorEmail != null && !supervisorEmail.isBlank();
    }

    /**
     * Cómo va el contrato respecto a su plazo.
     *
     * <p>Delega en {@link Cronograma}, que es el único cálculo de cronograma del
     * sistema: el mismo que alimenta el panel del supervisor. Antes esta
     * información se calculaba dos veces con criterios distintos, y SICOT podía
     * contradecirse a sí mismo sobre si un contrato iba atrasado.
     */
    public Cronograma cronograma(LocalDate hoy) {
        return Cronograma.calcular(fechaInicio, fechaFin, subetapasTotales, subetapasCompletadas,
                etapaActual, totalEtapas, hoy);
    }

}
