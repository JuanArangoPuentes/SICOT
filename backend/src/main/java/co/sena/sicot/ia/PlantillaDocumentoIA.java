package co.sena.sicot.ia;

import co.sena.sicot.ia.FormatoInstitucional.Encabezado;
import co.sena.sicot.ia.FormatoInstitucional.EstiloPie;
import co.sena.sicot.ia.FormatoInstitucional.Familia;
import co.sena.sicot.ia.FormatoInstitucional.Imagen;
import co.sena.sicot.ia.FormatoInstitucional.Logo;
import co.sena.sicot.ia.FormatoInstitucional.Pagina;

import java.util.List;
import java.util.Map;

/**
 * Catálogo de los documentos formales que SICOT arma para el supervisor —
 * solo los confirmados contra documentación real (ver memoria de proyecto
 * project_sicot_gccon_p010_grounded). Correcciones aplicadas respecto del guion
 * original del frontend (contractFlow.ts):
 *  - GCCON-F-030 es el Informe Final de Supervisión, no el Acta de Liquidación.
 *  - El Oficio de Pago (GRF-F-089 / "SCM") lo firma el Ordenador del gasto,
 *    no el supervisor — no se incluye aquí.
 *  - "ESUCON" no es un código de formato oficial confirmado; se modela como
 *    "Certificación de cumplimiento" sin código, pendiente de definir.
 *
 * <p>Cada entrada dice además cómo se presenta el formato real
 * ({@link FormatoInstitucional}) y qué datos del documento no están en el
 * contrato ({@link #campos}): SICOT no registra facturas, pólizas ni cédulas,
 * y el formato los pide. Las medidas salen de los formatos de la carpeta
 * «Documentos/SICOT» del equipo, medidas el 28-09-2026.
 *
 * @param campos             datos que el documento pide y el contrato no tiene.
 *                           El supervisor puede darlos al generar; los que falten
 *                           salen como «[dato pendiente…]».
 * @param llevaObservaciones si el formato tiene un apartado donde van las notas
 *                           del supervisor. El Acta de Inicio y el certificado no
 *                           lo tienen: ahí las notas no se piden al modelo.
 * @param tablas             tablas del formato que se diligencian fila por fila
 *                           (obligaciones, amparos, órdenes de pago). Las filas
 *                           que el supervisor no dé salen como «[dato pendiente…]».
 */
public record PlantillaDocumentoIA(String clave, String codigo, String nombre, FormatoInstitucional formato,
                                   List<CampoDelDocumento> campos, boolean llevaObservaciones,
                                   List<TablaDelDocumento> tablas) {

    public PlantillaDocumentoIA(String clave, String codigo, String nombre, FormatoInstitucional formato,
                                List<CampoDelDocumento> campos, boolean llevaObservaciones) {
        this(clave, codigo, nombre, formato, campos, llevaObservaciones, List.of());
    }

    /**
     * Un dato del documento que no sale del contrato.
     *
     * @param ejemplo cómo lo escribe el formato real, para quien lo diligencia.
     * @param largo   si es un párrafo y no un dato de una línea (el cumplimiento
     *                del SIGA): el formulario le da un área de texto y admite
     *                hasta {@link #MAX_LARGO} caracteres en vez de 600.
     */
    public record CampoDelDocumento(String clave, String etiqueta, String ejemplo, boolean opcional, boolean largo) {
        public CampoDelDocumento(String clave, String etiqueta, String ejemplo) {
            this(clave, etiqueta, ejemplo, false, false);
        }

        public CampoDelDocumento(String clave, String etiqueta, String ejemplo, boolean opcional) {
            this(clave, etiqueta, ejemplo, opcional, false);
        }

        /**
         * Un dato que, si falta, no deja nada pendiente: se deduce (el plazo,
         * de las fechas) o solo aplica a veces (el correo del contratista, el
         * valor actual cuando hubo adiciones). El formulario no lo cuenta
         * entre los que quedarán pendientes.
         */
        static CampoDelDocumento opcional(String clave, String etiqueta, String ejemplo) {
            return new CampoDelDocumento(clave, etiqueta, ejemplo, true);
        }
    }

    /** Largo máximo de un campo de párrafo; los de una línea admiten 600. */
    public static final int MAX_LARGO = 2000;

    /**
     * Una tabla del formato que se llena fila por fila.
     *
     * <p>Existe porque en el Informe Final real (CO1.PCCNTR.8426076) las
     * obligaciones ocupan diez páginas —catorce generales y dieciséis
     * específicas, cada una con su cumplimiento y su evidencia—, y SICOT solo
     * tenía una fila con «[dato pendiente]»: el documento no se podía
     * presentar tal como salía, por más datos que diera el supervisor
     * (revisión del 30-09-2026). Las obligaciones son del contrato y SICOT no
     * las registra; las escribe el supervisor, tal como están en el contrato.
     *
     * @param ayuda qué va en la tabla, para quien la diligencia.
     */
    public record TablaDelDocumento(String clave, String etiqueta, String ayuda, List<ColumnaDeTabla> columnas) {
    }

    /**
     * @param delContrato si lo que se escribe en esta columna es del contrato y
     *                    se ofrece ya escrito en el siguiente documento (el
     *                    texto de la obligación), o de este documento (lo que
     *                    se hizo en el periodo, su evidencia), que no se
     *                    arrastra al siguiente: arrastrarlo es firmar el
     *                    periodo pasado como si fuera este.
     */
    public record ColumnaDeTabla(String etiqueta, String ejemplo, boolean delContrato) {
    }

    /**
     * Datos que son de UN documento —la factura del periodo, sus valores, la
     * declaración de multas— y no del contrato. El formulario no los ofrece ya
     * escritos para el siguiente documento: el «NO» de multas del informe de
     * un periodo aparecía puesto en el Informe Final y certificaba sin
     * multas el contrato entero (revisión del 28-09-2026).
     */
    private static final java.util.Set<String> POR_DOCUMENTO = java.util.Set.of(
            "informeNumero", "periodoDesde", "periodoHasta", "numeroFactura", "fechaFactura", "valorFactura",
            "valorFacturado", "valorEjecutado", "periodoSeguridadSocial", "numeroPlanilla", "fechaPlanilla",
            "multas", "actaNumero", "cantidadDevolutivos", "cantidadConsumo", "saldoPorEjecutar", "valorAObligar",
            "valorTotalPagado", "valorTotalEjecutado", "cumplimientoObjeto", "mantenimiento",
            "fechaCertificadoPagos", "terminacionAnticipada", "valorFinal", "fechaTerminacionFinal", "valorActual",
            "fechaTerminacionActual", "prorroga", "adicion", "siga", "pagosSeguridadSocial");

    /**
     * Un dato opcional que pasa a ser necesario según otro: el valor actual
     * solo hace falta si hubo una adición, la fecha actual si hubo una
     * prórroga. Con la adición declarada y el valor vacío, el documento sale
     * con el valor pendiente, y el formulario tiene que contarlo.
     */
    private static final Map<String, String> DEPENDE_DE = Map.of(
            "valorActual", "adicion", "fechaTerminacionActual", "prorroga");

    public static boolean esPorDocumento(String clave) {
        return POR_DOCUMENTO.contains(clave);
    }

    /** La clave del dato que vuelve necesario a este, o {@code null}. */
    public static String dependeDe(String clave) {
        return DEPENDE_DE.get(clave);
    }

    private static final String GESTION_CONTRACTUAL = "GESTIÓN CONTRACTUAL";

    /** Carta con los márgenes de Word de los GCCON (2,5 cm a los lados, 3 cm arriba). */
    private static Pagina paginaGccon(float finCuerpo) {
        return new Pagina(70.9f, 70.9f, 85.05f, finCuerpo);
    }

    /** El logo del encabezado de GCCON-F-030 y GCCON-F-031: 46,65 × 44,20 pt, centrado. */
    private static final Logo LOGO_GCCON = new Logo(Imagen.VERDE, Float.NaN, 35.45f, 46.65f, 44.20f, true);

    private static final CampoDelDocumento FECHA_SUSCRIPCION =
            new CampoDelDocumento("fechaSuscripcion", "Fecha de suscripción del contrato", "14/10/2025");
    private static final CampoDelDocumento CEDULA_SUPERVISOR =
            new CampoDelDocumento("cedulaSupervisor", "Cédula del supervisor", "98.587.121");
    private static final CampoDelDocumento CEDULA_REPRESENTANTE =
            new CampoDelDocumento("cedulaRepresentante", "Cédula del representante legal", "98.587.121");
    private static final CampoDelDocumento FECHA_GARANTIAS =
            new CampoDelDocumento("fechaAprobacionGarantias", "Fecha de aprobación de las garantías", "17/06/2025");
    /**
     * El plazo pactado. Sin él, SICOT lo deduce de las fechas solo si es un
     * número exacto de meses; si no, escribe las fechas de inicio y fin.
     */
    private static final CampoDelDocumento PLAZO =
            CampoDelDocumento.opcional("plazo", "Plazo pactado del contrato", "Dos (02) meses");
    /**
     * Si hubo multas o sanciones. Es una certificación del supervisor: sin
     * ella, el documento no afirma que no las hubo.
     */
    private static final CampoDelDocumento MULTAS = new CampoDelDocumento("multas",
            "Multas o sanciones durante la ejecución (NO, o cuáles)", "NO");
    private static final CampoDelDocumento FORMA_DE_PAGO = new CampoDelDocumento("formaDePago", "Forma de pago",
            "El valor del contrato será cancelado mediante único pago según lo facturado…");

    // Las tablas comparten clave entre formatos cuando son lo mismo: las
    // obligaciones específicas del Informe de Supervisión son las del Informe
    // Final, y la póliza es una sola. Así lo escrito en un documento se ofrece
    // en el otro. Los ejemplos salen del Informe Final real del Centro.

    private static final ColumnaDeTabla EVIDENCIA = new ColumnaDeTabla("Producto o evidencia",
            "Acta de recibo del 15/09/2026 publicada en SECOP II", false);

    /** Obligaciones específicas del contrato: las del Informe de Supervisión y las del Informe Final. */
    private static final TablaDelDocumento OBLIGACIONES_ESPECIFICAS = new TablaDelDocumento("obligacionesEspecificas",
            "Obligaciones específicas del contratista",
            "Una fila por obligación específica, copiada del contrato. Las obligaciones se ofrecen ya escritas en el"
                    + " siguiente documento; las actividades y la evidencia son de este.",
            List.of(new ColumnaDeTabla("Obligación", "Proveer los bienes nuevos, libres de defectos…", true),
                    new ColumnaDeTabla("Actividades realizadas",
                            "Se verificó que los bienes entregados no tuvieran defectos ni estuvieran vencidos.", false),
                    EVIDENCIA));

    /**
     * Amparos de la garantía única. Los formatos traen una lista fija de
     * amparos, pero el Informe Final real solo relaciona los que tiene la
     * póliza: el supervisor escribe los suyos.
     */
    private static final TablaDelDocumento AMPAROS = new TablaDelDocumento("amparos",
            "Amparos de la garantía única",
            "Una fila por amparo de la póliza, con su vigencia y su valor.",
            List.of(new ColumnaDeTabla("Amparo", "Cumplimiento - Cumplimiento del contrato", true),
                    new ColumnaDeTabla("Vigencia desde", "14/10/2025", true),
                    new ColumnaDeTabla("Vigencia hasta", "16/06/2026", true),
                    new ColumnaDeTabla("Valor", "$4.000.000,00", true)));

    private static TablaDelDocumento obligacionesSiga(String clave, String etiqueta, String ejemplo,
                                                      String cumplimiento) {
        return new TablaDelDocumento(clave, etiqueta,
                "Una fila por obligación, como está en el contrato (anexo GCCON-AN-001), con cómo se cumplió.",
                List.of(new ColumnaDeTabla("Obligación", ejemplo, true),
                        new ColumnaDeTabla("Cumplimiento", cumplimiento, false),
                        new ColumnaDeTabla("Evidencia", "Planillas de seguridad social publicadas en SECOP II", false)));
    }

    public static final Map<String, PlantillaDocumentoIA> CATALOGO = Map.of(
            "ACTA_INICIO", new PlantillaDocumentoIA(
                    "ACTA_INICIO", "GCCON-F-018", "Acta de Inicio",
                    new FormatoInstitucional("GCCON-F-018", "V.04", "GCCON-F-018 V.04",
                            Encabezado.CLASIFICACION_CON_CASILLAS, GESTION_CONTRACTUAL, "FORMATO ACTA DE INICIO",
                            Familia.CALIBRI, 10f,
                            // El .docx ancla el logo al margen izquierdo, no al centro.
                            new Logo(Imagen.VERDE_ACTA_DE_INICIO, 267.15f, 33.75f, 41.65f, 40.75f, true),
                            new Pagina(70.9f, 70.9f, 85.05f, 728.2f),
                            new EstiloPie(Familia.ARIAL, 10f, 32.42f, Familia.ARIAL, 10f, 18.98f)),
                    List.of(PLAZO, CEDULA_SUPERVISOR, CEDULA_REPRESENTANTE,
                            new CampoDelDocumento("expedicionCedulaRepresentante",
                                    "Lugar de expedición de la cédula del representante legal", "Bello-Antioquia"),
                            FECHA_GARANTIAS,
                            CampoDelDocumento.opcional("correoContratista", "Correo del contratista",
                                    "contratista@correo.com")),
                    false),
            "INFORME_SUPERVISION", new PlantillaDocumentoIA(
                    "INFORME_SUPERVISION", "GCCON-F-031", "Informe de Supervisión",
                    new FormatoInstitucional("GCCON-F-031", "V04", "GCCON-F-031 V04",
                            Encabezado.CLASIFICACION_EN_CELDAS, GESTION_CONTRACTUAL, "INFORME DE SUPERVISIÓN",
                            Familia.CALIBRI, 11f, LOGO_GCCON, paginaGccon(706.95f),
                            new EstiloPie(Familia.CALIBRI, 11f, 55.5f, Familia.CALIBRI, 11f, 40.1f)),
                    List.of(FECHA_SUSCRIPCION, PLAZO, FORMA_DE_PAGO,
                            new CampoDelDocumento("prorroga", "Prórrogas del contrato", "N/A"),
                            new CampoDelDocumento("adicion", "Adiciones al contrato", "N/A"),
                            CampoDelDocumento.opcional("valorActual", "Valor actual del contrato (si hubo adiciones)",
                                    "$25.000.000,00"),
                            CampoDelDocumento.opcional("fechaTerminacionActual",
                                    "Fecha de terminación actual (si hubo prórrogas)", "20/12/2025"),
                            new CampoDelDocumento("informeNumero", "Número del informe de supervisión", "1"),
                            new CampoDelDocumento("periodoDesde", "Inicio del periodo del informe", "01/09/2026"),
                            new CampoDelDocumento("periodoHasta", "Fin del periodo del informe", "30/09/2026"),
                            new CampoDelDocumento("aseguradora", "Aseguradora de la garantía", "SEGUROS MUNDIAL"),
                            new CampoDelDocumento("numeroPoliza", "Número de la póliza", "CVA-100010967"),
                            new CampoDelDocumento("certificadoAnexo", "Certificado o anexo de la póliza",
                                    "Anexo 01 - 360029848"),
                            new CampoDelDocumento("fechaExpedicionPoliza", "Fecha de expedición de la póliza",
                                    "14/10/2025"),
                            FECHA_GARANTIAS,
                            new CampoDelDocumento("numeroFactura", "Número de la factura del periodo", "FE 547"),
                            new CampoDelDocumento("valorFacturado", "Valor facturado en el periodo", "$39.400.634,00"),
                            new CampoDelDocumento("valorEjecutado", "Valor ejecutado acumulado", "$39.400.634,00"),
                            new CampoDelDocumento("periodoSeguridadSocial", "Periodo de seguridad social reportado",
                                    "Septiembre 2026"),
                            new CampoDelDocumento("numeroPlanilla", "Número de la planilla de seguridad social",
                                    "9471802315"),
                            new CampoDelDocumento("fechaPlanilla", "Fecha de pago de la planilla", "10/09/2026"),
                            MULTAS,
                            // El formato deja un espacio libre para relacionarlo y
                            // pide «No aplica» si el contrato no tiene esas obligaciones.
                            new CampoDelDocumento("siga",
                                    "Cumplimiento de las obligaciones SIGA en el periodo (ambientales, de seguridad y"
                                            + " salud en el trabajo, energéticas), o «No aplica»",
                                    "El contratista entregó la planilla de seguridad social del personal que ingresó"
                                            + " al Centro.", false, true)),
                    true,
                    List.of(AMPAROS, OBLIGACIONES_ESPECIFICAS)),
            "ACTA_RECIBO", new PlantillaDocumentoIA(
                    "ACTA_RECIBO", "GIL-F-010", "Acta de Recibo a Satisfacción de Bienes",
                    new FormatoInstitucional("GIL-F-010", "08", null,
                            Encabezado.HOJA_DE_CALCULO, "GESTIÓN DE INFRAESTRUCTURA Y LOGÍSTICA",
                            "FORMATO ACTA DE RECIBO A SATISFACCIÓN DE BIENES", Familia.ARIAL, 5.5f,
                            null, new Pagina(50f, 50f, 40f, 760f), null),
                    List.of(new CampoDelDocumento("actaNumero", "Número del acta", "1"),
                            new CampoDelDocumento("codigoCentroCosto", "Código del centro de costo",
                                    "36-02-00-0005-920510"),
                            new CampoDelDocumento("tipoAdquisicion", "Tipo de adquisición",
                                    "CONTRATO, ORDEN DE COMPRA u OTRO"),
                            new CampoDelDocumento("tipoEntrega", "Tipo de entrega", "SUMINISTRO o UNICA ENTREGA"),
                            new CampoDelDocumento("fechaSuscripcion", "Fecha del acto administrativo (suscripción)",
                                    "15/05/2026"),
                            new CampoDelDocumento("rubroPresupuestal", "Rubro presupuestal",
                                    "C-3603-1300-20-20305C-3603025-02"),
                            new CampoDelDocumento("cantidadDevolutivos", "Cantidad de bienes devolutivos", "0"),
                            new CampoDelDocumento("cantidadConsumo", "Cantidad de bienes de consumo", "26"),
                            CEDULA_SUPERVISOR,
                            new CampoDelDocumento("cargoSupervisor", "Cargo del supervisor", "INSTRUCTOR")),
                    true),
            "CERTIFICACION_CUMPLIMIENTO", new PlantillaDocumentoIA(
                    "CERTIFICACION_CUMPLIMIENTO", "PENDIENTE_DE_DEFINIR", "Certificación de cumplimiento",
                    new FormatoInstitucional(null, null, null,
                            Encabezado.NINGUNO, GESTION_CONTRACTUAL, "CERTIFICACIÓN DE CUMPLIMIENTO DEL SUPERVISOR",
                            Familia.ARIAL, 12f,
                            new Logo(Imagen.NEGRO, 288.25f, 70.55f, 39.6f, 38.65f, false),
                            // El cuerpo del certificado va de 1,5 cm a 3 cm de los bordes.
                            new Pagina(42.5f, 85f, 56.7f, 735.3f), null),
                    List.of(FECHA_SUSCRIPCION,
                            new CampoDelDocumento("numeroFactura", "Número de la factura", "FE 547"),
                            new CampoDelDocumento("fechaFactura", "Fecha de la factura", "03/11/2025"),
                            new CampoDelDocumento("valorFactura", "Valor de la factura", "$39.400.634,00"),
                            new CampoDelDocumento("tipoCuenta", "Tipo de cuenta bancaria", "AHORROS"),
                            new CampoDelDocumento("banco", "Banco", "BANCOLOMBIA"),
                            new CampoDelDocumento("numeroCuenta", "Número de la cuenta", "331-000072-59"),
                            new CampoDelDocumento("titularCuenta", "Titular de la cuenta",
                                    "EQUISUMINISTROS Y CONSTRUCCIONES S.A.S"),
                            new CampoDelDocumento("saldoPorEjecutar", "Saldo por ejecutar después del pago",
                                    "$59.666.893,00"),
                            new CampoDelDocumento("rubroPresupuestal", "Rubro presupuestal",
                                    "C-3603-1300-20-20305C-3603025-02 ADQUIS. DE BYS - …"),
                            new CampoDelDocumento("fuenteRecurso", "Fuente del recurso (REC)", "NACION"),
                            new CampoDelDocumento("usoPresupuestal", "Nombre de uso presupuestal",
                                    "OTROS MUEBLES NCP"),
                            new CampoDelDocumento("codigoUsoPresupuestal", "Código de uso presupuestal",
                                    "A-02-02-01-003-008-01"),
                            new CampoDelDocumento("valorAObligar", "Valor a obligar", "$21.628.500,00")),
                    false),
            "INFORME_FINAL", new PlantillaDocumentoIA(
                    "INFORME_FINAL", "GCCON-F-030", "Informe Final de Supervisión",
                    new FormatoInstitucional("GCCON-F-030", "V05", "GCCON-F-030 V05",
                            Encabezado.PROCESO_Y_CLASIFICACION, GESTION_CONTRACTUAL, "INFORME FINAL DE SUPERVISIÓN",
                            Familia.CALIBRI, 11f, LOGO_GCCON, paginaGccon(712f),
                            new EstiloPie(Familia.CALIBRI, 11f, 54.4f, Familia.CALIBRI, 10f, 39.9f)),
                    List.of(FECHA_SUSCRIPCION, PLAZO, CEDULA_REPRESENTANTE, FORMA_DE_PAGO,
                            new CampoDelDocumento("numeroCdp", "Número del certificado de disponibilidad presupuestal",
                                    "11125"),
                            new CampoDelDocumento("fechaCdp", "Fecha del certificado de disponibilidad presupuestal",
                                    "07/02/2025"),
                            new CampoDelDocumento("valorFinal", "Valor final del negocio jurídico", "$20.000.000,00"),
                            new CampoDelDocumento("fechaTerminacionFinal", "Fecha de terminación final", "20/12/2025"),
                            new CampoDelDocumento("terminacionAnticipada", "Fecha de terminación anticipada",
                                    "No aplica"),
                            new CampoDelDocumento("valorTotalPagado", "Valor total pagado", "$19.989.620,00"),
                            new CampoDelDocumento("valorTotalEjecutado", "Valor total ejecutado", "$19.989.620,00"),
                            new CampoDelDocumento("apoyoSupervision", "Apoyo a la supervisión",
                                    "Yeison Dariel Zapata Monsalve"),
                            new CampoDelDocumento("fechaDesignacion", "Fecha de designación de la supervisión",
                                    "16/10/2025"),
                            new CampoDelDocumento("aseguradora", "Aseguradora de la garantía", "SEGUROS MUNDIAL"),
                            new CampoDelDocumento("numeroPoliza", "Número de la póliza", "CVA-100010967"),
                            new CampoDelDocumento("certificadoAnexo", "Certificado o anexo de la póliza",
                                    "Anexo 01 - 360029848"),
                            new CampoDelDocumento("fechaExpedicionPoliza", "Fecha de expedición de la póliza",
                                    "14/10/2025"),
                            FECHA_GARANTIAS,
                            new CampoDelDocumento("fechaCertificadoPagos", "Fecha del certificado de desembolsos",
                                    "16/03/2026"),
                            new CampoDelDocumento("cumplimientoObjeto",
                                    "¿El contratista cumplió el objeto a satisfacción? (SI / NO / PARCIALMENTE)", "SI"),
                            MULTAS,
                            // El 2.5 es una certificación propia: antes se deducía
                            // del cumplimiento del objeto y certificaba pagos que
                            // nadie había declarado (auditoría del 02-10-2026).
                            new CampoDelDocumento("pagosSeguridadSocial",
                                    "¿El contratista cumplió con los pagos de seguridad social durante la ejecución?"
                                            + " (SI / NO, o la certificación)", "SI"),
                            new CampoDelDocumento("mantenimiento",
                                    "¿Los bienes requieren revisiones o mantenimientos periódicos? (SI / NO)", "NO")),
                    true,
                    List.of(new TablaDelDocumento("obligacionesGenerales", "Obligaciones del contrato",
                                    "Una fila por obligación general del contrato, copiada del contrato, con si se"
                                            + " cumplió y su evidencia.",
                                    List.of(new ColumnaDeTabla("Obligación",
                                                    "Ejecutar el objeto del contrato bajo las condiciones de calidad,"
                                                            + " oportunidad y obligaciones definidas en el proceso de"
                                                            + " contratación.", true),
                                            new ColumnaDeTabla("¿Cumplió? (SI CUMPLIO / NO / PARCIALMENTE / NO SE"
                                                    + " REQUIRIÓ EL CUMPLIMIENTO)", "SI CUMPLIO", false),
                                            EVIDENCIA)),
                            OBLIGACIONES_ESPECIFICAS,
                            obligacionesSiga("sigaSst", "Obligaciones de seguridad y salud en el trabajo (SIGA)",
                                    "Relación del personal que ingresa a las instalaciones con su pago de seguridad"
                                            + " social.", "El contratista suministró el pago de la seguridad social."),
                            obligacionesSiga("sigaAmbiental", "Obligaciones ambientales (SIGA)",
                                    "Acreditar un programa de recolección y gestión de residuos de los productos"
                                            + " suministrados.", "El contratista acreditó el programa en la etapa"
                                            + " precontractual."),
                            AMPAROS,
                            new TablaDelDocumento("ordenesDePago", "Órdenes de pago",
                                    "Una fila por orden de pago del certificado de desembolsos del SIIF. Deben sumar"
                                            + " el valor total pagado.",
                                    List.of(new ColumnaDeTabla("Número de orden de pago", "70614726", false),
                                            new ColumnaDeTabla("Fecha de pago", "11/03/2026", false),
                                            new ColumnaDeTabla("Valor de pago", "$16.798.000,00", false))))));
}
