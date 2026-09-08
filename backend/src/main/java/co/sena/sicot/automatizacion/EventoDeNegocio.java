package co.sena.sicot.automatizacion;

import java.time.Instant;

/**
 * Algo que ocurrió en SICOT y sobre lo que una automatización puede querer
 * reaccionar.
 *
 * <h2>De dónde salen estos eventos</h2>
 * No hay que publicarlos a mano en cada servicio. Los trece puntos del backend
 * que cambian algo relevante ya llaman todos a
 * {@code RegistroService.registrar(contrato, accion, descripcion)} para dejar
 * auditoría, así que ese método es el único sitio donde hay que publicar. El
 * catálogo de acciones que llega aquí es exactamente el catálogo de la
 * auditoría: {@code CONTRATO_CREADO}, {@code SUPERVISOR_ASIGNADO},
 * {@code SUBETAPA_AVANZADA}, {@code DOCUMENTO_FIRMADO},
 * {@code INTEGRIDAD_COMPROMETIDA}, {@code FIRMA_REVOCADA}…
 *
 * <p>La consecuencia práctica importa: una acción nueva que alguien añada a la
 * auditoría el año que viene queda disponible para las reglas sin que nadie
 * tenga que acordarse de publicarla por separado. El modo de fallo que se evita
 * es el de dos listas paralelas que divergen en silencio.
 *
 * <h2>Por qué lleva identificadores y no entidades</h2>
 * El evento se entrega <b>después</b> de confirmar la transacción, cuando la
 * sesión de Hibernate que cargó las entidades puede estar ya cerrada. Un
 * {@code Contrato} viajando aquí sería un proxy que estalla con
 * {@code LazyInitializationException} la primera vez que una regla le pida
 * cualquier campo, y estallaría dentro del motor, lejos de la causa. Con el
 * identificador, la regla recarga lo que necesita en su propia transacción.
 *
 * @param contratoId     contrato afectado, o {@code null} si el hecho no
 *                       pertenece a ninguno (una firma revocada, por ejemplo)
 * @param numeroContrato número legible, capturado mientras la sesión seguía
 *                       abierta, para poder redactar mensajes sin recargar nada
 * @param accion         el mismo código que quedó en la auditoría
 * @param descripcion    el mismo texto que quedó en la auditoría
 * @param ocurridoEn     momento del hecho
 */
public record EventoDeNegocio(
        Long contratoId,
        String numeroContrato,
        String accion,
        String descripcion,
        Instant ocurridoEn
) {

    public boolean esAccion(String esperada) {
        return accion.equals(esperada);
    }

    public boolean tieneContrato() {
        return contratoId != null;
    }
}
