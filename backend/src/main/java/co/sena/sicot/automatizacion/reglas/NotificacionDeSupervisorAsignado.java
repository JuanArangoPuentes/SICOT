package co.sena.sicot.automatizacion.reglas;

import co.sena.sicot.automatizacion.EventoDeNegocio;
import co.sena.sicot.automatizacion.FotoDelContrato;
import co.sena.sicot.automatizacion.LectorDeContratos;
import co.sena.sicot.automatizacion.PayloadDeTarea;
import co.sena.sicot.automatizacion.ReglaDeEvento;
import co.sena.sicot.automatizacion.TareaSolicitada;
import co.sena.sicot.entity.enums.PrioridadAlerta;
import co.sena.sicot.entity.enums.TipoAlerta;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

/**
 * Avisa al supervisor de que le asignaron un contrato.
 *
 * <h2>Por qué esta regla existe</h2>
 * Hoy, cuando GESTIÓN asigna un supervisor, la asignación queda en la base y en
 * la auditoría — y ahí se acaba. La persona se entera la próxima vez que entra a
 * SICOT y mira, o cuando alguien se lo dice por otro canal. En un proceso donde
 * el reloj del contrato ya está corriendo, «se entera cuando entre» es una
 * ventana de días.
 *
 * <h2>Dos efectos, dos tareas</h2>
 * La alerta en el panel y el correo se encolan por separado a propósito. Son
 * fallos independientes: si el servidor de correo está caído, la alerta ya está
 * puesta y visible; si no hay dirección, el correo se descarta sin afectar a la
 * alerta. Una sola tarea que hiciera las dos cosas obligaría a repetir la
 * primera al reintentar la segunda, y el supervisor acabaría con cinco alertas
 * idénticas por un problema de SMTP.
 */
@Component
public class NotificacionDeSupervisorAsignado implements ReglaDeEvento {

    public static final String CODIGO = "supervisor-asignado";

    /** El mismo código que escribe {@code ContratoService} en la auditoría. */
    private static final String ACCION = "SUPERVISOR_ASIGNADO";
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final LectorDeContratos lectorDeContratos;

    /**
     * Dónde descarga el supervisor la instalación local de SICOT. El paso 1 de
     * la lista de chequeo de Alex pide que el aviso de designación lo lleve
     * («el sistema envía la notificación con el enlace para la descarga del
     * sistema e iniciar el proceso de supervisión»). Desde la versión 0.5.0 hay
     * instalador publicado y es el valor por defecto; vacío, el correo no
     * ofrece ningún enlace, en vez de uno que no lleve a ninguna parte.
     */
    private final String enlaceDescarga;

    /**
     * La dirección del servidor de SICOT, que el supervisor escribe en
     * Configuración la primera vez que abre la instalación de escritorio: el
     * instalador es uno solo para todos los Centros y no la trae dentro (ver
     * {@code apiBase} en el cliente). Sin ella, el enlace de descarga lleva a
     * una aplicación que no sabe a qué servidor conectarse.
     */
    private final String direccionDelServidor;

    public NotificacionDeSupervisorAsignado(LectorDeContratos lectorDeContratos,
                                            @Value("${sicot.supervisor.enlace-descarga:}") String enlaceDescarga,
                                            @Value("${sicot.servidor.direccion-publica:}") String direccionDelServidor) {
        this.lectorDeContratos = lectorDeContratos;
        this.enlaceDescarga = enlaceDescarga == null ? "" : enlaceDescarga.strip();
        this.direccionDelServidor = direccionDelServidor == null ? "" : direccionDelServidor.strip();
    }

    @Override
    public String codigo() {
        return CODIGO;
    }

    @Override
    public boolean aplicaA(EventoDeNegocio evento) {
        return evento.esAccion(ACCION) && evento.tieneContrato();
    }

    @Override
    public List<TareaSolicitada> evaluar(EventoDeNegocio evento) {
        Optional<FotoDelContrato> encontrado = lectorDeContratos.porId(evento.contratoId());
        if (encontrado.isEmpty()) {
            return List.of();
        }
        FotoDelContrato contrato = encontrado.get();
        if (contrato.supervisorNombre() == null) {
            // La acción quedó registrada pero el contrato ya no tiene supervisor:
            // pasa si se asignó y se retiró en la misma sesión. No hay a quién avisar.
            return List.of();
        }

        // El hecho es la asignación concreta, y la identifica el instante en que
        // quedó en la auditoría: reprocesar el mismo evento no duplica, pero
        // cada asignación avisa, también la que devuelve el contrato a quien ya
        // lo tuvo. Con el correo del supervisor en la clave, pasar el contrato
        // de A a B y de vuelta a A no avisaba a A la segunda vez, porque su
        // clave seguía en la tabla. (Asignar al que ya es el supervisor no
        // llega aquí: ContratoService no lo registra como asignación.)
        String sufijo = ":contrato=" + contrato.contratoId() + ":asignacion=" + evento.ocurridoEn().toEpochMilli();

        // El primer paso que se le anuncia es el del proceso real: el acta la
        // elabora Contratación y llega por correo; el supervisor la firma, la
        // devuelve y la carga en SICOT con la evidencia del envío.
        String comoEntrar = comoEntrar(enlaceDescarga, direccionDelServidor);
        String cuerpo = ("Hola %s,\n\n"
                + "Usted fue designado(a) supervisor(a) del contrato %s, y la designación ya quedó registrada en "
                + "SICOT.\n\n"
                + "Objeto: %s\n"
                + "Plazo: %s a %s\n\n"
                + "Su primer paso es el Acta de Inicio: Contratación se la enviará por correo. Fírmela, "
                + "devuélvala respondiendo ese mismo correo y cargue en SICOT el acta firmada junto con la "
                + "evidencia del envío.\n\n"
                + "%s"
                + "— SICOT · Centro Tecnológico del Mobiliario (SENA)")
                .formatted(contrato.supervisorNombre(), contrato.numeroContrato(), contrato.objeto(),
                        contrato.fechaInicio() == null ? "sin definir" : FECHA.format(contrato.fechaInicio()),
                        contrato.fechaFin() == null ? "sin definir" : FECHA.format(contrato.fechaFin()),
                        comoEntrar);

        // La alerta no repite cómo instalar SICOT: quien la lee ya lo tiene abierto.
        List<TareaSolicitada> tareas = new java.util.ArrayList<>();
        tareas.add(TareaSolicitada.ahora(
                CODIGO,
                CODIGO + ":alerta" + sufijo,
                contrato.contratoId(),
                new PayloadDeTarea.CrearAlerta(
                        TipoAlerta.SOLICITUD,
                        PrioridadAlerta.MEDIA,
                        ("Fue designado supervisor del contrato %s. Su primer paso: firmar y devolver el Acta "
                                + "de Inicio que le envía Contratación, y cargarla aquí con la evidencia del envío.")
                                .formatted(contrato.numeroContrato()))));

        if (contrato.tieneSupervisorConCorreo()) {
            tareas.add(TareaSolicitada.ahora(
                    CODIGO,
                    CODIGO + ":correo" + sufijo,
                    contrato.contratoId(),
                    new PayloadDeTarea.EnviarCorreo(
                            contrato.supervisorEmail(),
                            "SICOT — Se le asignó la supervisión del contrato " + contrato.numeroContrato(),
                            cuerpo)));
        }
        return tareas;
    }

    /**
     * Cómo llega el supervisor al contrato. Responde la pregunta que Alex dejó
     * en el paso 1 de su hoja —«¿qué pasa si el supervisor tiene instalado el
     * SISTEMA y realizará un nuevo contrato?»—: el contrato nuevo se suma a los
     * que ya tiene, en el selector de contratos de su panel. Solo quien no lo
     * tiene instalado necesita el enlace y la dirección del servidor.
     */
    public static String comoEntrar(String enlaceDescarga, String direccionDelServidor) {
        String enElSelector = "este contrato aparece en el selector de contratos de su panel, junto a los que ya "
                + "supervisa.";
        if (enlaceDescarga.isEmpty()) {
            return "Ingrese a SICOT para ver las etapas del contrato y el paso en curso: " + enElSelector + "\n\n";
        }
        String yaInstalado = "Si ya tiene SICOT instalado, ábralo: " + enElSelector;
        // Cada dirección en su propio renglón y sin punto detrás: pegado a la
        // dirección, el punto termina dentro del enlace en más de un lector de
        // correo.
        String servidor = direccionDelServidor.isEmpty() ? ""
                : "Al abrirlo por primera vez, escriba en Configuración la dirección del servidor:\n"
                        + direccionDelServidor + "\n";
        return yaInstalado + "\n\nSi todavía no lo tiene, descárguelo aquí:\n" + enlaceDescarga + "\n" + servidor
                + "\n";
    }
}
