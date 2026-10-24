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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Le recuerda al supervisor cada entrega que acordó con el contratista.
 *
 * <h2>Por qué esta regla existe</h2>
 * La lista de chequeo de Alex Zapata une dos pasos por las fechas de entrega:
 * en el 5 se acuerdan con el contratista, y en el 3 se invita a los
 * profesionales de SST y ambiental «a las entregas». Las fechas quedan
 * registradas en el paso 2.4 (ver {@code AcuerdoDeEntregasService}), y un
 * registro que nadie mira el día que importa no sirve de mucho: lo que el
 * supervisor necesita es enterarse a tiempo de avisarles y de preparar la
 * verificación de lo que se entregue.
 *
 * <h2>Dos avisos por entrega</h2>
 * Uno con {@value #DIAS_DE_ANTICIPACION} días de anticipación —tiempo para
 * recordarles la fecha a SST y ambiental— y otro el mismo día. Con el mismo
 * criterio que {@link AvisoDeVencimientoProximo}: el umbral se cruza, no se
 * acierta, así que una entrega acordada para pasado mañana recibe el primer
 * aviso hoy, y un día con el backend caído no se salta ninguno. El día de la
 * entrega solo sale el aviso del día. Una entrega ya pasada no avisa:
 * recordarla tarde no cambia nada, y lo que haya pasado ese día se registra en
 * la inspección.
 *
 * <h2>La clave es la fecha, no la entrega</h2>
 * El acuerdo se reemplaza entero cada vez que el supervisor lo guarda, y las
 * filas cambian de id. Con el id en la clave, corregir la descripción de una
 * entrega volvería a avisar de ella. Con la fecha, el aviso de un día es uno
 * solo aunque se guarde diez veces; si la fecha se mueve, la nueva sí avisa,
 * que es justo lo que tiene que pasar. Dos entregas el mismo día van en un solo
 * aviso.
 */
@Component
public class AvisoDeEntregaAcordada implements ReglaDeCalendario {

    public static final String CODIGO = "entrega-acordada";

    /** Lo bastante antes para recordarles la fecha a SST y ambiental. */
    static final int DIAS_DE_ANTICIPACION = 3;

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /** Lo que se cita de cada entrega en el aviso. La descripción completa está en el paso 2.4. */
    private static final int LARGO_DE_LA_CITA = 120;

    @Override
    public String codigo() {
        return CODIGO;
    }

    @Override
    public List<TareaSolicitada> evaluar(FotoDelContrato contrato, LocalDate hoy) {
        if (contrato.entregas().isEmpty()) {
            return List.of();
        }
        Map<LocalDate, List<FotoDelContrato.Entrega>> porFecha = contrato.entregas().stream()
                .collect(Collectors.groupingBy(FotoDelContrato.Entrega::fecha, TreeMap::new, Collectors.toList()));

        List<TareaSolicitada> tareas = new ArrayList<>();
        porFecha.forEach((fecha, entregas) -> {
            long faltan = ChronoUnit.DAYS.between(hoy, fecha);
            if (faltan < 0 || faltan > DIAS_DE_ANTICIPACION) {
                return;
            }
            String clave = CODIGO + ":contrato=" + contrato.contratoId() + ":fecha=" + fecha;
            String cuales = cuales(entregas);
            String citas = citas(entregas);
            if (faltan == 0) {
                // El día de la entrega, un solo aviso. Si la entrega se acordó
                // para hoy mismo y nunca hubo aviso de anticipación, este lo
                // reemplaza: dos alertas el mismo día por la misma entrega serían
                // ruido.
                tareas.add(alerta(contrato, clave + ":dia", PrioridadAlerta.ALTA,
                        "Hoy (%s) es %s del contrato %s: %s. Verifique lo que se entregue y cargue la evidencia "
                                .formatted(FECHA.format(fecha), cuales, contrato.numeroContrato(), citas)
                                + "en la inspección (etapa 3)."));
                return;
            }
            String cuando = faltan == 1 ? "Mañana (" + FECHA.format(fecha) + ")"
                    : "El " + FECHA.format(fecha) + " (en " + faltan + " días)";
            tareas.add(alerta(contrato, clave + ":dias=" + DIAS_DE_ANTICIPACION, PrioridadAlerta.MEDIA,
                    "%s es %s del contrato %s: %s. Prepare la verificación de lo que se entregue y recuérdeles la "
                            .formatted(cuando, cuales, contrato.numeroContrato(), citas)
                            + "fecha a los profesionales de SST y ambiental."));
        });
        return tareas;
    }

    private static TareaSolicitada alerta(FotoDelContrato contrato, String clave, PrioridadAlerta prioridad,
                                          String mensaje) {
        return TareaSolicitada.ahora(CODIGO, clave, contrato.contratoId(),
                new PayloadDeTarea.CrearAlerta(TipoAlerta.RECORDATORIO, prioridad, mensaje));
    }

    /** «la entrega acordada n.º 2» o «las entregas acordadas n.º 2 y 3». */
    static String cuales(List<FotoDelContrato.Entrega> entregas) {
        List<String> numeros = entregas.stream().map(e -> String.valueOf(e.numero())).toList();
        if (numeros.size() == 1) {
            return "la entrega acordada n.º " + numeros.getFirst();
        }
        return "las entregas acordadas n.º " + String.join(", ", numeros.subList(0, numeros.size() - 1))
                + " y " + numeros.getLast();
    }

    private static String citas(List<FotoDelContrato.Entrega> entregas) {
        return entregas.stream()
                .map(e -> "«" + recortar(e.descripcion()) + "»")
                .collect(Collectors.joining("; "));
    }

    private static String recortar(String texto) {
        String t = texto == null ? "" : texto.replaceAll("\\s+", " ").strip();
        return t.length() <= LARGO_DE_LA_CITA ? t : t.substring(0, LARGO_DE_LA_CITA - 1).stripTrailing() + "…";
    }
}
