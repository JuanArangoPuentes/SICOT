package co.sena.sicot.automatizacion.reglas;

import co.sena.sicot.automatizacion.FotoDelContrato;
import co.sena.sicot.automatizacion.PayloadDeTarea;
import co.sena.sicot.automatizacion.ReglaDeCalendario;
import co.sena.sicot.automatizacion.TareaSolicitada;
import co.sena.sicot.entity.enums.PrioridadAlerta;
import co.sena.sicot.entity.enums.TipoAlerta;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Avisa de un contrato que pasó su fecha de fin y sigue ACTIVO.
 *
 * <h2>Por qué es una alerta y no un cambio de estado automático</h2>
 * Sería fácil que el sistema lo pasara solo a FINALIZADO. No lo hace, y no debe
 * hacerlo. Un contrato que llega a su fecha de fin sin cerrarse puede estar en
 * varias situaciones jurídicas distintas —prórroga en trámite, suspensión, un
 * incumplimiento que hay que documentar— y ninguna de ellas se deduce de que el
 * calendario haya avanzado.
 *
 * <p>Cerrarlo automáticamente cambiaría un dato oficial basándose en una
 * suposición, y el rastro de auditoría diría que el sistema lo finalizó sin que
 * nadie lo decidiera. La regla correcta en contratación pública es la contraria:
 * el sistema señala, la persona resuelve. Ver también la primera regla
 * invariante de ADR-008.
 *
 * <h2>Por qué una sola alerta y no una por día</h2>
 * La clave de idempotencia no incluye la fecha, así que el aviso se emite una
 * vez y no vuelve. Un contrato vencido hace tres meses y sin cerrar es un
 * problema real, pero recordarlo cada mañana no lo resuelve: solo entrena a la
 * gente a saltarse la bandeja.
 */
@Component
public class AvisoDeContratoVencido implements ReglaDeCalendario {

    public static final String CODIGO = "contrato-vencido";
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    @Override
    public String codigo() {
        return CODIGO;
    }

    @Override
    public List<TareaSolicitada> evaluar(FotoDelContrato contrato, LocalDate hoy) {
        if (contrato.fechaFin() == null || !contrato.fechaFin().isBefore(hoy)) {
            return List.of();
        }

        long diasVencido = ChronoUnit.DAYS.between(contrato.fechaFin(), hoy);
        String avance = contrato.subetapasTotales() == 0
                ? "sin subetapas registradas"
                : "%d de %d subetapas completadas".formatted(
                        contrato.subetapasCompletadas(), contrato.subetapasTotales());

        return List.of(TareaSolicitada.ahora(
                CODIGO,
                CODIGO + ":contrato=" + contrato.contratoId(),
                contrato.contratoId(),
                new PayloadDeTarea.CrearAlerta(
                        TipoAlerta.VENCIMIENTO,
                        PrioridadAlerta.ALTA,
                        ("El contrato %s superó su fecha de fin (%s, hace %d día(s)) y sigue en estado ACTIVO. "
                                + "Estado del flujo: %s. Revise si corresponde prórroga, suspensión o cierre; "
                                + "SICOT no cambia el estado por su cuenta.")
                                .formatted(contrato.numeroContrato(), FECHA.format(contrato.fechaFin()),
                                        diasVencido, avance))));
    }
}
