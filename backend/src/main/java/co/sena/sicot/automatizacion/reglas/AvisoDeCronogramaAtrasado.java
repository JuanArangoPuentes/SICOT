package co.sena.sicot.automatizacion.reglas;

import co.sena.sicot.automatizacion.FotoDelContrato;
import co.sena.sicot.automatizacion.PayloadDeTarea;
import co.sena.sicot.automatizacion.ReglaDeCalendario;
import co.sena.sicot.automatizacion.TareaSolicitada;
import co.sena.sicot.entity.enums.PrioridadAlerta;
import co.sena.sicot.entity.enums.TipoAlerta;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * Deja constancia cuando el avance real se queda muy por detrás del plazo
 * consumido.
 *
 * <h2>Qué relación tiene con el semáforo de FR-010</h2>
 * Son dos cosas distintas y conviven a propósito. El semáforo del navegador es
 * una <b>vista en vivo</b>: mientras alguien mira la pantalla, el contrato está
 * en rojo. Esta regla produce un <b>hecho registrado</b>: el día que el atraso
 * cruzó el umbral queda escrito en {@code alertas}, con fecha, y sobrevive a
 * cerrar el navegador.
 *
 * <p>Esa diferencia importa para un expediente. «El contrato estaba en rojo»
 * sin fecha no es una afirmación verificable; «el 14 de marzo el sistema
 * registró un atraso de 35 puntos» sí lo es. El semáforo no se retira: se le
 * añade memoria.
 *
 * <h2>Por qué el umbral es una brecha y no un porcentaje de avance</h2>
 * Un contrato con el 20 % de avance no dice nada por sí solo: puede ir
 * perfectamente si lleva un mes de doce. Lo que sí dice algo es la <b>distancia</b>
 * entre plazo consumido y trabajo cerrado. Treinta puntos es una brecha que ya
 * no se explica por el ritmo desigual normal de las etapas de GCCON-P-010.
 *
 * <h2>Por qué se emite una vez por tramo y no una vez en total</h2>
 * La clave incluye el tramo de atraso redondeado a decenas. Un contrato que
 * empeora de 30 a 60 puntos de brecha genera un segundo aviso, porque es
 * información nueva; uno que se mantiene en 32 no genera nada más. Así el aviso
 * escala con el problema sin convertirse en un recordatorio diario.
 */
@Component
public class AvisoDeCronogramaAtrasado implements ReglaDeCalendario {

    public static final String CODIGO = "cronograma-atrasado";

    /** Brecha, en puntos porcentuales, a partir de la cual el atraso deja de ser ritmo normal. */
    private static final double BRECHA_MINIMA = 0.30;

    @Override
    public String codigo() {
        return CODIGO;
    }

    @Override
    public List<TareaSolicitada> evaluar(FotoDelContrato contrato, LocalDate hoy) {
        Double plazo = contrato.fraccionDePlazoTranscurrida(hoy);
        Double avance = contrato.fraccionDeAvance();

        // Sin fechas completas o sin subetapas sembradas no se puede afirmar
        // nada sobre el atraso. Callar es la respuesta correcta: una alerta
        // basada en un dato que no se tiene es peor que no alertar.
        if (plazo == null || avance == null) {
            return List.of();
        }

        double brecha = plazo - avance;
        if (brecha < BRECHA_MINIMA) {
            return List.of();
        }

        int tramo = (int) (Math.floor(brecha * 10) * 10);
        return List.of(TareaSolicitada.ahora(
                CODIGO,
                CODIGO + ":contrato=" + contrato.contratoId() + ":tramo=" + tramo,
                contrato.contratoId(),
                new PayloadDeTarea.CrearAlerta(
                        TipoAlerta.CRONOGRAMA,
                        brecha >= 0.50 ? PrioridadAlerta.ALTA : PrioridadAlerta.MEDIA,
                        ("El contrato %s lleva el %.0f%% del plazo consumido y solo el %.0f%% del flujo "
                                + "completado (%d de %d subetapas): una brecha de %.0f puntos. "
                                + "Revise el avance de las etapas pendientes.")
                                .formatted(contrato.numeroContrato(), plazo * 100, avance * 100,
                                        contrato.subetapasCompletadas(), contrato.subetapasTotales(),
                                        brecha * 100))));
    }
}
