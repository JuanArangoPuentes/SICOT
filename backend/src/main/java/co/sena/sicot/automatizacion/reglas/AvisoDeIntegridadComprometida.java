package co.sena.sicot.automatizacion.reglas;

import co.sena.sicot.automatizacion.EventoDeNegocio;
import co.sena.sicot.automatizacion.PayloadDeTarea;
import co.sena.sicot.automatizacion.ReglaDeEvento;
import co.sena.sicot.automatizacion.TareaSolicitada;
import co.sena.sicot.entity.enums.PrioridadAlerta;
import co.sena.sicot.entity.enums.TipoAlerta;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * Sube al panel el hallazgo más grave que el sistema sabe detectar: un documento
 * firmado cuyo contenido ya no coincide con la huella que se registró al
 * firmarlo.
 *
 * <h2>Por qué hacía falta</h2>
 * {@code DocumentoService} ya detecta esto y ya lo escribe: un {@code log.error}
 * y una entrada {@code INTEGRIDAD_COMPROMETIDA} en la auditoría. El problema es
 * quién lo lee. El log del servidor lo mira alguien cuando va a buscar otra cosa,
 * y la auditoría de un contrato se consulta cuando ya se sospecha algo. Los dos
 * son sitios donde el hallazgo <b>espera a ser encontrado</b>.
 *
 * <p>Un documento oficial alterado después de la firma no puede depender de que
 * alguien decida mirar. Esta regla lo pone en la bandeja del contrato con
 * prioridad ALTA, que es el único sitio de SICOT que la gente abre sin motivo
 * previo.
 *
 * <h2>Por qué la clave lleva la marca de tiempo</h2>
 * Es la única regla del módulo que <b>no</b> deduplica por hecho, y es
 * deliberado. Cada verificación fallida es un intento distinto de usar un
 * documento que no es válido, y el patrón de repetición —una vez, o quince en
 * una tarde— es en sí mismo la información relevante para quien investigue.
 * Silenciar la segunda ocurrencia sería descartar evidencia.
 */
@Component
public class AvisoDeIntegridadComprometida implements ReglaDeEvento {

    public static final String CODIGO = "integridad-comprometida";

    /** El mismo código que escribe {@code DocumentoService} en la auditoría. */
    private static final String ACCION = "INTEGRIDAD_COMPROMETIDA";

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
        return List.of(TareaSolicitada.ahora(
                CODIGO,
                CODIGO + ":contrato=" + evento.contratoId() + ":en=" + Instant.now().toEpochMilli(),
                evento.contratoId(),
                new PayloadDeTarea.CrearAlerta(
                        TipoAlerta.DOCUMENTO,
                        PrioridadAlerta.ALTA,
                        "ATENCIÓN: se detectó un documento firmado del contrato " + evento.numeroContrato()
                                + " cuyo contenido no coincide con la huella registrada al firmarlo. "
                                + "No lo dé por válido y avise al área de sistemas. Detalle: "
                                + evento.descripcion())));
    }
}
