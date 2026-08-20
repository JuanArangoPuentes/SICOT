package co.sena.sicot.ia;

import java.util.Map;

/**
 * Catálogo de documentos que el Copiloto IA puede redactar para el
 * supervisor — solo los confirmados contra documentación real (ver memoria
 * de proyecto project_sicot_gccon_p010_grounded). Correcciones aplicadas
 * respecto del guion original del frontend (contractFlow.ts):
 *  - GCCON-F-030 es el Informe Final de Supervisión, no el Acta de Liquidación.
 *  - El Oficio de Pago (GRF-F-089 / "SCM") lo firma el Ordenador del gasto,
 *    no el supervisor — no se incluye aquí.
 *  - "ESUCON" no es un código de formato oficial confirmado; se modela como
 *    "Certificación de cumplimiento" sin código, pendiente de definir.
 */
public record PlantillaDocumentoIA(String clave, String codigo, String nombre, String instruccionesCampos) {

    public static final Map<String, PlantillaDocumentoIA> CATALOGO = Map.of(
            "ACTA_INICIO", new PlantillaDocumentoIA(
                    "ACTA_INICIO", "GCCON-F-018", "Acta de Inicio",
                    """
                    Redacta el Acta de Inicio del contrato con estos apartados, en este orden: \
                    (1) encabezado con Contrato Nro., Tipo de contrato, Objeto, Valor del contrato, Plazo, \
                    Lugar de ejecución, Contratista, CC o NIT, Representante legal, Supervisor designado; \
                    (2) un párrafo narrativo formal donde el supervisor y el representante legal del \
                    contratista dejan constancia de suscribir el acta de inicio, incluyendo número y fecha \
                    del registro presupuestal, fecha de aprobación de garantías, fecha de inicio y fecha de \
                    terminación; (3) constancia de verificación de los documentos/requisitos para iniciar \
                    la ejecución.\
                    """),
            "INFORME_SUPERVISION", new PlantillaDocumentoIA(
                    "INFORME_SUPERVISION", "GCCON-F-031", "Informe de Supervisión",
                    """
                    Redacta el Informe de Supervisión con estos apartados: (1) Aspectos generales \
                    (contratante, contrato nro., objeto, contratista, fechas, valor actual); \
                    (2) Avance financiero (valor cobrado, ejecutado, saldo, % de ejecución); \
                    (3) Relación de pagos de seguridad social — certificación de que el contratista está \
                    al día; (4) Multas y sanciones — certificar que no se han presentado, salvo que el \
                    contexto indique lo contrario; (5) Observaciones relevantes de la ejecución a la fecha \
                    del informe.\
                    """),
            "ACTA_RECIBO", new PlantillaDocumentoIA(
                    "ACTA_RECIBO", "GIL-F-010", "Acta de Recibo a Satisfacción de Bienes",
                    """
                    Redacta el Acta de Recibo a Satisfacción de Bienes con: Acta N°, fecha, ciudad, tipo de \
                    adquisición (usa "CONTRATO"), tipo de entrega, número de acto administrativo (el \
                    contrato), proveedor/contratista, NIT o cédula, valor total, objeto del contrato, y una \
                    declaración formal de "Recibido a satisfacción" certificando que los bienes fueron \
                    verificados conforme a las especificaciones técnicas.\
                    """),
            "CERTIFICACION_CUMPLIMIENTO", new PlantillaDocumentoIA(
                    "CERTIFICACION_CUMPLIMIENTO", "PENDIENTE_DE_DEFINIR", "Certificación de cumplimiento",
                    """
                    Redacta una certificación del supervisor (sin código de formato oficial confirmado — \
                    práctica de la certificación de cumplimiento del contrato) donde el supervisor certifica: \
                    (1) el valor total del contrato; (2) la fecha de inicio de ejecución; (3) que el \
                    contratista ha prestado el servicio/entregado los bienes conforme a lo pactado, según \
                    consta en el informe de supervisión; (4) recomienda el trámite de pago, dejando el saldo \
                    por ejecutar. No incluyas autorización de pago del Ordenador del gasto — eso lo emite un \
                    documento distinto (Oficio de Pago, GRF-F-089), que no redacta el supervisor.\
                    """),
            "INFORME_FINAL", new PlantillaDocumentoIA(
                    "INFORME_FINAL", "GCCON-F-030", "Informe Final de Supervisión",
                    """
                    Redacta el Informe Final de Supervisión (no es el acta de liquidación) con: \
                    (1) Aspectos generales (contratante, contrato nro., objeto, contratista, fechas de inicio \
                    y terminación, valor inicial y valor final); (2) Aspectos técnicos — cumplimiento de las \
                    obligaciones generales y específicas del contrato, cumplimiento del objeto contractual; \
                    (3) Aspectos financieros — pagos realizados, valor ejecutado, valor pagado, valor por \
                    pagar o a liberar; (4) una conclusión certificando el cumplimiento total o parcial del \
                    objeto contractual.\
                    """));
}
