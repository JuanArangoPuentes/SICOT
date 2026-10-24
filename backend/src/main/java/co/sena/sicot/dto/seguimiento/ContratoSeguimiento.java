package co.sena.sicot.dto.seguimiento;

import co.sena.sicot.dto.cronograma.CronogramaResponse;
import co.sena.sicot.dto.etapa.EtapaResponse;
import co.sena.sicot.dto.etapa.SubetapaResponse;
import co.sena.sicot.entity.enums.EstadoContrato;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Cómo va un contrato: dónde está dentro de GCCON-P-010, cómo va contra su
 * plazo, qué documentos tiene y qué fue lo último que pasó.
 *
 * @param cronograma       el mismo cálculo que ve el supervisor en su panel y
 *                         que evalúa el motor (ver {@code Cronograma}).
 * @param etapaActual      número de la primera etapa sin cerrar; {@code null}
 *                         si el flujo está completo.
 * @param subetapaEnCurso  la subetapa en la que está trabajando el supervisor;
 *                         {@code null} si no hay ninguna en curso.
 * @param etapas           las seis etapas con sus subetapas, para el detalle.
 * @param ultimaActividad  el último registro de auditoría del contrato;
 *                         {@code null} si nunca se ha hecho nada en él.
 */
public record ContratoSeguimiento(
        Long id,
        String numeroContrato,
        String objeto,
        String contratista,
        EstadoContrato estado,
        BigDecimal valor,
        LocalDate fechaInicio,
        LocalDate fechaFin,
        CronogramaResponse cronograma,
        long subetapasCompletadas,
        long subetapasTotales,
        Integer etapaActual,
        String etapaActualNombre,
        SubetapaResponse subetapaEnCurso,
        List<EtapaResponse> etapas,
        List<DocumentoResumen> documentos,
        long alertasSinLeer,
        UltimaActividad ultimaActividad
) {
}
