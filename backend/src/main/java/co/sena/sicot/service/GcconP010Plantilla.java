package co.sena.sicot.service;

import co.sena.sicot.entity.Contrato;
import co.sena.sicot.entity.Etapa;
import co.sena.sicot.entity.Subetapa;
import co.sena.sicot.entity.enums.EstadoEtapa;
import co.sena.sicot.entity.enums.EstadoSubetapa;

import java.util.ArrayList;
import java.util.List;

/**
 * Plantilla fija de las 6 etapas / 27 subetapas del procedimiento GCCON-P-010,
 * espejo 1:1 de STEPS_INITIAL en el frontend (contractFlow.ts).
 * Se usa para poblar etapas/subetapas reales cuando Gestión crea un contrato
 * nuevo — sin esto, un contrato real nunca tenía etapas y el panel de
 * Supervisor quedaba vacío.
 */
final class GcconP010Plantilla {

    private record SubetapaDef(String codigo, String nombre, String descripcion, String responsable) {}

    private record EtapaDef(String nombre, List<SubetapaDef> subetapas) {}

    private static final List<EtapaDef> ETAPAS = List.of(
            new EtapaDef("INICIO — Estudios y Suscripción", List.of(
                    new SubetapaDef("1.1", "Identificación de la necesidad institucional", "Planteamiento de la necesidad de bienes y servicios (PAA).", "Área requirente"),
                    new SubetapaDef("1.2", "Conformación de la Unidad de Contratación", "Definición de las personas que conformarán la Unidad de Contratación.", "Ordenador del gasto"),
                    new SubetapaDef("1.3", "Elaboración de estudios previos (GCCON-F-046)", "Estudios previos, ficha técnica y análisis del sector económico.", "Unidad de Contratación"),
                    new SubetapaDef("1.4", "Expedición del CDP y aprobación de garantías", "Certificado de Disponibilidad Presupuestal y garantías exigibles.", "Unidad de Contratación"),
                    new SubetapaDef("1.5", "Suscripción y publicación en SECOP II", "Suscripción del contrato y publicación en la plataforma SECOP II.", "Unidad de Contratación"),
                    new SubetapaDef("1.6", "Designación formal del supervisor", "Comunicación interna de designación del supervisor del contrato.", "Ordenador del gasto")
            )),
            new EtapaDef("INICIO — Acta de Inicio (GCCON-F-018)", List.of(
                    new SubetapaDef("2.1", "Revisión del objeto y alcance del contrato", "Revisión del objeto y alcance contractual por parte del supervisor.", "Supervisor"),
                    new SubetapaDef("2.2", "Verificación de datos del contratista y NIT", "Verificación de RUT y cámara de comercio del contratista.", "Supervisor"),
                    new SubetapaDef("2.3", "Confirmación de fecha de inicio y duración", "Confirmación de cronograma y plazo de ejecución.", "Supervisor"),
                    new SubetapaDef("2.4", "Establecimiento de responsabilidades y puntos de control", "Definición de la matriz de control del contrato.", "Supervisor"),
                    new SubetapaDef("2.5", "Verificación de afiliaciones a seguridad social", "Verificación de planillas PILA del contratista.", "Supervisor"),
                    new SubetapaDef("2.6", "Registro de garantías vigentes", "Registro de pólizas de cumplimiento vigentes.", "Unidad de Contratación"),
                    new SubetapaDef("2.7", "Firma del Acta de Inicio (GCCON-F-018)", "Acta de inicio generada por el Copiloto IA con los datos del contrato; el supervisor solo la firma.", "Supervisor")
            )),
            new EtapaDef("INSPECCIÓN — Monitoreo y Ejecución", List.of(
                    new SubetapaDef("3.1", "Verificación física de la entrega en bodega", "Verificación física de la entrega de materiales en bodega.", "Supervisor"),
                    new SubetapaDef("3.2", "Carga de evidencia fotográfica georreferenciada", "Evidencia fotográfica con georreferenciación activa.", "Supervisor"),
                    new SubetapaDef("3.3", "Comparación cantidad/calidad vs. ficha técnica", "Verificación de cantidades y calidad contra la ficha técnica.", "Supervisor"),
                    new SubetapaDef("3.4", "Firma del Informe de Supervisión (GCCON-F-031)", "Informe de supervisión generado por el Copiloto IA; el supervisor solo lo firma.", "Supervisor")
            )),
            new EtapaDef("RECEPCIÓN — Acta de Recibo a Satisfacción", List.of(
                    new SubetapaDef("4.1", "Verificación de aportes a seguridad social (PILA)", "Verificación de planillas PILA para recepción.", "Supervisor"),
                    new SubetapaDef("4.2", "Verificación de factura electrónica DIAN (FEV)", "Verificación de la factura electrónica de venta en DIAN.", "Supervisor"),
                    new SubetapaDef("4.3", "Firma del Acta de Recibo a Satisfacción (GIL-F-010)", "Acta de recibo generada por el Copiloto IA; el supervisor solo la firma.", "Supervisor")
            )),
            new EtapaDef("CERTIFICACIÓN — Cumplimiento y Trámite de Pago", List.of(
                    new SubetapaDef("5.1", "Verificación de vigencia de garantías", "Verificación de pólizas de vigencia del contrato.", "Supervisor"),
                    new SubetapaDef("5.2", "Revisión de orden de pago y CRP", "Revisión de la orden de pago y Certificado de Registro Presupuestal.", "Supervisor"),
                    new SubetapaDef("5.3", "Firma de la Certificación de cumplimiento", "Certificación de cumplimiento generada por el Copiloto IA; el supervisor solo la firma.", "Supervisor")
            )),
            new EtapaDef("CIERRE — Informe Final y Archivo (GCCON-F-030)", List.of(
                    new SubetapaDef("6.1", "Verificación de cumplimiento total del objeto contractual", "Verificación del cumplimiento integral del objeto contractual.", "Supervisor"),
                    new SubetapaDef("6.2", "Evaluación de modificaciones o adiciones (si aplica)", "Evaluación de adiciones o prórrogas registradas en SECOP II.", "Supervisor"),
                    new SubetapaDef("6.3", "Firma del Informe Final de Supervisión (GCCON-F-030)", "Informe final generado por el Copiloto IA; el supervisor solo lo firma.", "Supervisor"),
                    new SubetapaDef("6.4", "Cierre y archivo del expediente digital en SIGEP", "Cierre y archivo del expediente digital del contrato.", "Unidad de Contratación")
            ))
    );

    private GcconP010Plantilla() {
    }

    /** Crea las 6 etapas (sin persistir) para un contrato nuevo. La primera
     *  subetapa (1.1) queda EN_CURSO para que el proceso tenga un punto de
     *  partida activo; el resto arranca PENDIENTE — sin asumir que ninguna
     *  tarea ya se realizó. */
    static List<Etapa> crearEtapas(Contrato contrato) {
        List<Etapa> etapas = new ArrayList<>();
        boolean primera = true;
        int numero = 1;
        for (EtapaDef etapaDef : ETAPAS) {
            Etapa etapa = new Etapa();
            etapa.setContrato(contrato);
            etapa.setNombre(etapaDef.nombre());
            etapa.setNumero(numero++);
            etapa.setEstado(primera ? EstadoEtapa.EN_CURSO : EstadoEtapa.PENDIENTE);
            etapa.setPorcentaje(0);

            List<Subetapa> subetapas = new ArrayList<>();
            boolean primeraSub = true;
            for (SubetapaDef def : etapaDef.subetapas()) {
                Subetapa sub = new Subetapa();
                sub.setEtapa(etapa);
                sub.setCodigo(def.codigo());
                sub.setNombre(def.nombre());
                sub.setDescripcion(def.descripcion());
                sub.setResponsable(def.responsable());
                sub.setEstado(primera && primeraSub ? EstadoSubetapa.EN_CURSO : EstadoSubetapa.PENDIENTE);
                subetapas.add(sub);
                primeraSub = false;
            }
            etapa.setSubEtapas(subetapas);
            etapas.add(etapa);
            primera = false;
        }
        return etapas;
    }
}
