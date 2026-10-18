package co.sena.sicot.service;

import co.sena.sicot.entity.Contrato;
import co.sena.sicot.entity.Etapa;
import co.sena.sicot.entity.Subetapa;
import co.sena.sicot.entity.enums.EstadoEtapa;
import co.sena.sicot.entity.enums.EstadoSubetapa;

import java.util.ArrayList;
import java.util.List;

/**
 * Plantilla fija de las 6 etapas y 21 subetapas de la supervisión de un
 * contrato. Se usa para poblar etapas/subetapas reales cuando Gestión crea un
 * contrato nuevo — sin esto, un contrato real nunca tenía etapas y el panel de
 * Supervisor quedaba vacío.
 *
 * <p>Las etapas 1 y 2 siguen el proceso real que documentó el supervisor Alex
 * Zapata el 15-10-2026 sobre el contrato CO1.PCCNTR.10057820
 * (docs/producto/LISTA_CHEQUEO_ALEX.md). Antes eran 13 subetapas sacadas de
 * los manuales: la etapa 1 le pedía al supervisor marcar trabajo que no es
 * suyo (estudios previos, CDP, publicación), y la 2 terminaba en que SICOT
 * generaba el acta de inicio, cuando en la realidad la elabora Contratación,
 * llega por correo y el supervisor solo la firma y la devuelve.
 *
 * <p>Las etapas 3 a 6 siguen siendo las de GCCON-P-010 y GCCON-M-002: el
 * supervisor todavía no ha documentado esa parte del proceso. Cuando lo haga,
 * se reemplazan aquí de la misma forma.
 *
 * <p>Solo afecta a los contratos que se crean desde ahora: los que ya existen
 * conservan las subetapas con que se sembraron.
 */
final class GcconP010Plantilla {

    /** Lo que exige el paso del acta antes de cerrarse. Ver {@link Subetapa#getDocumentosRequeridos()}. */
    static final String ACTA_FIRMADA_Y_ENVIO = "ACTA_FIRMADA_Y_ENVIO";

    /**
     * Nombre de la etapa de designación. Sirve para reconocer los contratos
     * sembrados con esta plantilla: a los anteriores no se les completa la
     * etapa 1 al asignarles supervisor, porque su etapa 1 era otra cosa.
     */
    static final String ETAPA_DESIGNACION = "INICIO — Designación de la supervisión (GCCON-F-032)";

    private record SubetapaDef(String codigo, String nombre, String descripcion, String responsable,
                               String documentosRequeridos) {
        SubetapaDef(String codigo, String nombre, String descripcion, String responsable) {
            this(codigo, nombre, descripcion, responsable, null);
        }
    }

    private record EtapaDef(String nombre, List<SubetapaDef> subetapas) {}

    private static final List<EtapaDef> ETAPAS = List.of(
            new EtapaDef(ETAPA_DESIGNACION, List.of(
                    new SubetapaDef("1.1", "Suscripción y publicación del contrato en SECOP II",
                            "La Unidad de Contratación firma el contrato y lo publica en SECOP II antes de que se"
                                    + " designe al supervisor.",
                            "Unidad de Contratación"),
                    new SubetapaDef("1.2", "Designación del supervisor y del apoyo a la supervisión (GCCON-F-032)",
                            "El ordenador del gasto designa por escrito al supervisor y al apoyo a la supervisión."
                                    + " Quien tenga un conflicto de interés debe manifestarlo por escrito a más tardar"
                                    + " el día siguiente a la notificación.",
                            "Ordenador del gasto"),
                    new SubetapaDef("1.3", "Registro del contrato en SICOT y aviso al supervisor",
                            "El apoyo a la supervisión carga en SICOT el acto de designación; el sistema lee los"
                                    + " datos del contrato, completa los que falten desde el SECOP II y le avisa al"
                                    + " supervisor.",
                            "Apoyo a la supervisión")
            )),
            new EtapaDef("INICIO — Acta de Inicio y arranque (GCCON-F-018)", List.of(
                    new SubetapaDef("2.1", "Firma y devolución del Acta de Inicio (GCCON-F-018)",
                            "Contratación le envía el Acta de Inicio por correo. Fírmela y devuélvala respondiendo"
                                    + " ese mismo correo; después cargue aquí el acta firmada (PDF) y la evidencia"
                                    + " del envío (el pantallazo o el PDF del correo). El paso no se cierra sin"
                                    + " los dos.",
                            "Supervisor", ACTA_FIRMADA_Y_ENVIO),
                    new SubetapaDef("2.2", "Aviso a los profesionales SST y ambiental",
                            "Identifique en el estudio previo, publicado en el SECOP II, a los profesionales de SST"
                                    + " y ambiental, y escríbales que el contrato inició, invitándolos a las entregas"
                                    + " o actividades según las obligaciones del contrato.",
                            "Supervisor"),
                    new SubetapaDef("2.3", "Verificación de especificaciones en SECOP II",
                            "Con el enlace del proceso, verifique en el SECOP II las cantidades, especificaciones"
                                    + " y valores del contrato. SICOT le muestra lo que publica el SECOP y le señala"
                                    + " lo que no coincide con lo registrado.",
                            "Supervisor"),
                    new SubetapaDef("2.4", "Contacto con el contratista y acuerdo de entregas",
                            "Escriba al contratista para presentarse y acordar las fechas de entrega dentro del"
                                    + " plazo del contrato. Si es un suministro o una compra, el correo debe llevar"
                                    + " las cantidades exactas de los bienes.",
                            "Supervisor")
            )),
            new EtapaDef("INSPECCIÓN — Monitoreo y Ejecución", List.of(
                    new SubetapaDef("3.1", "Verificación física de la entrega en bodega", "Verificación física de la entrega de materiales en bodega.", "Supervisor"),
                    new SubetapaDef("3.2", "Carga de evidencia fotográfica georreferenciada", "Evidencia fotográfica con georreferenciación activa.", "Supervisor"),
                    new SubetapaDef("3.3", "Comparación cantidad/calidad vs. ficha técnica", "Verificación de cantidades y calidad contra la ficha técnica.", "Supervisor"),
                    new SubetapaDef("3.4", "Firma del Informe de Supervisión (GCCON-F-031)", "SICOT arma el Informe de Supervisión con los datos del contrato; el supervisor lo revisa y lo firma.", "Supervisor")
            )),
            new EtapaDef("RECEPCIÓN — Acta de Recibo a Satisfacción", List.of(
                    new SubetapaDef("4.1", "Verificación de aportes a seguridad social (PILA)", "Verificación de planillas PILA para recepción.", "Supervisor"),
                    new SubetapaDef("4.2", "Verificación de factura electrónica DIAN (FEV)", "Verificación de la factura electrónica de venta en DIAN.", "Supervisor"),
                    new SubetapaDef("4.3", "Firma del Acta de Recibo a Satisfacción (GIL-F-010)", "SICOT arma el Acta de Recibo a Satisfacción con los datos del contrato; el supervisor la revisa y la firma.", "Supervisor")
            )),
            new EtapaDef("CERTIFICACIÓN — Cumplimiento y Trámite de Pago", List.of(
                    new SubetapaDef("5.1", "Verificación de vigencia de garantías", "Verificación de pólizas de vigencia del contrato.", "Supervisor"),
                    new SubetapaDef("5.2", "Revisión de orden de pago y CRP", "Revisión de la orden de pago y Certificado de Registro Presupuestal.", "Supervisor"),
                    new SubetapaDef("5.3", "Firma de la Certificación de cumplimiento", "SICOT arma la Certificación de cumplimiento con los datos del contrato; el supervisor la revisa y la firma.", "Supervisor")
            )),
            new EtapaDef("CIERRE — Informe Final y Archivo (GCCON-F-030)", List.of(
                    new SubetapaDef("6.1", "Verificación de cumplimiento total del objeto contractual", "Verificación del cumplimiento integral del objeto contractual.", "Supervisor"),
                    new SubetapaDef("6.2", "Evaluación de modificaciones o adiciones (si aplica)", "Evaluación de adiciones o prórrogas registradas en SECOP II.", "Supervisor"),
                    new SubetapaDef("6.3", "Firma del Informe Final de Supervisión (GCCON-F-030)", "SICOT arma el Informe Final de Supervisión con los datos del contrato; el supervisor lo revisa y lo firma.", "Supervisor"),
                    new SubetapaDef("6.4", "Cierre y archivo del expediente digital en SIGEP", "Cierre y archivo del expediente digital del contrato.", "Unidad de Contratación")
            ))
    );

    private GcconP010Plantilla() {
    }

    /**
     * Crea las 6 etapas (sin persistir) para un contrato nuevo.
     *
     * <p>Si el contrato ya llega con supervisor, la designación está hecha: la
     * etapa 1 nace completa y el proceso arranca en el paso del acta (2.1), que
     * es donde empieza el trabajo del supervisor según el proceso real. Sin
     * supervisor, la etapa 1 queda en curso hasta que se le asigne.
     */
    static List<Etapa> crearEtapas(Contrato contrato) {
        List<Etapa> etapas = new ArrayList<>();
        int numero = 1;
        for (EtapaDef etapaDef : ETAPAS) {
            Etapa etapa = new Etapa();
            etapa.setContrato(contrato);
            etapa.setNombre(etapaDef.nombre());
            etapa.setNumero(numero++);
            etapa.setEstado(EstadoEtapa.PENDIENTE);
            etapa.setPorcentaje(0);

            List<Subetapa> subetapas = new ArrayList<>();
            for (SubetapaDef def : etapaDef.subetapas()) {
                Subetapa sub = new Subetapa();
                sub.setEtapa(etapa);
                sub.setCodigo(def.codigo());
                sub.setNombre(def.nombre());
                sub.setDescripcion(def.descripcion());
                sub.setResponsable(def.responsable());
                sub.setDocumentosRequeridos(def.documentosRequeridos());
                sub.setEstado(EstadoSubetapa.PENDIENTE);
                subetapas.add(sub);
            }
            etapa.setSubEtapas(subetapas);
            etapas.add(etapa);
        }
        if (contrato.getSupervisor() != null) {
            completarDesignacion(etapas);
        } else {
            arrancar(etapas.get(0));
        }
        return etapas;
    }

    /**
     * Da por hecha la designación cuando se le asigna supervisor a un contrato
     * que nació sin él: la etapa 1 queda completa y el proceso pasa al acta.
     *
     * @return {@code false} si no había nada que hacer: la etapa 1 ya estaba
     *         completa, o el contrato se sembró con la plantilla anterior, cuya
     *         etapa 1 era otra cosa y no se toca
     */
    static boolean completarDesignacion(List<Etapa> etapas) {
        if (etapas.size() < 2 || !ETAPA_DESIGNACION.equals(etapas.get(0).getNombre())
                || etapas.get(0).getEstado() == EstadoEtapa.COMPLETADA) {
            return false;
        }
        Etapa designacion = etapas.get(0);
        designacion.getSubEtapas().forEach(s -> s.setEstado(EstadoSubetapa.COMPLETADA));
        designacion.setEstado(EstadoEtapa.COMPLETADA);
        designacion.setPorcentaje(100);

        Etapa siguiente = etapas.get(1);
        if (siguiente.getEstado() == EstadoEtapa.PENDIENTE) {
            arrancar(siguiente);
        }
        return true;
    }

    /** La etapa queda en curso con su primera subetapa como punto de partida. */
    private static void arrancar(Etapa etapa) {
        etapa.setEstado(EstadoEtapa.EN_CURSO);
        List<Subetapa> subetapas = etapa.getSubEtapas();
        if (!subetapas.isEmpty() && subetapas.get(0).getEstado() == EstadoSubetapa.PENDIENTE) {
            subetapas.get(0).setEstado(EstadoSubetapa.EN_CURSO);
        }
    }
}
