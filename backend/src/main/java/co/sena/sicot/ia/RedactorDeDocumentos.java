package co.sena.sicot.ia;

import co.sena.sicot.entity.Contrato;
import co.sena.sicot.ia.BloqueDocumento.Campo;
import co.sena.sicot.ia.BloqueDocumento.Ficha;
import co.sena.sicot.ia.BloqueDocumento.Parrafo;
import co.sena.sicot.ia.BloqueDocumento.Seccion;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Arma los cinco documentos formales del supervisor a partir de los formatos
 * reales del SENA y de los datos del contrato, sin pasar ningún dato por el
 * modelo de IA.
 *
 * <h2>Por qué los documentos ya no los redacta el modelo</h2>
 * En la prueba integral del 24-09-2026, con un contrato completo y el modelo
 * por defecto ({@code qwen2.5:7b}), los cinco documentos salieron con errores
 * que invalidan un documento oficial:
 * <ul>
 *   <li>el apellido de la representante legal escrito de dos formas distintas
 *       («Osipina», «Ospona») en dos actas;</li>
 *   <li>el valor del contrato en letras equivocado y, en otra, expresado en
 *       bolívares;</li>
 *   <li>entidades inventadas como contratante («Secretaría Distrital de
 *       Educación de Bogotá», «Servicio Técnico Especializado… (SICOT)»);</li>
 *   <li>fechas futuras como fecha del acta y verificaciones que nadie hizo
 *       («se ha realizado un exhaustivo proceso de verificación»);</li>
 *   <li>el Informe de Supervisión no llegó: tardó más de 240 s.</li>
 * </ul>
 * Los formatos reales (GCCON-F-018, GCCON-F-031, GIL-F-010, el certificado
 * ESUCON y GCCON-F-030) son en su mayoría fichas de datos y texto fijo. Lo que
 * cambia de un contrato a otro son los datos, y esos los tiene el backend
 * exactos. Por eso aquí se escriben con código: salen al instante, iguales al
 * contrato, y funcionan en cualquier equipo, que es la razón de ser del motor
 * de SICOT (ver ADR-008).
 *
 * <h2>Lo que SICOT no sabe queda a la vista</h2>
 * Número de factura, pólizas, pagos realizados, bienes recibidos… SICOT no los
 * registra. En vez de inventarlos, el documento los marca como
 * «[dato pendiente: …]», en color ámbar en el PDF, para que el supervisor los
 * vea antes de firmar.
 *
 * <p>El modelo interviene en un solo sitio: el apartado de observaciones, y
 * solo para pasar a redacción formal lo que el propio supervisor escribió (ver
 * {@code GeneracionDocumentoService}).
 */
public final class RedactorDeDocumentos {

    /** Contratante tal como aparece en los formatos del Centro. */
    static final String CONTRATANTE = "SENA - Centro Tecnológico del Mobiliario";

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter FECHA_LARGA =
            DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", Locale.of("es", "CO"));

    private RedactorDeDocumentos() {
    }

    /**
     * @param observaciones texto del apartado de observaciones ya redactado, o
     *                      {@code null} si el supervisor no aportó notas.
     */
    public static List<BloqueDocumento> componer(PlantillaDocumentoIA plantilla, Contrato c, LocalDate hoy,
                                                 String observaciones) {
        return switch (plantilla.clave()) {
            case "ACTA_INICIO" -> actaDeInicio(c, hoy, observaciones);
            case "INFORME_SUPERVISION" -> informeDeSupervision(c, hoy, observaciones);
            case "ACTA_RECIBO" -> actaDeRecibo(c, hoy, observaciones);
            case "CERTIFICACION_CUMPLIMIENTO" -> certificacion(c, observaciones);
            case "INFORME_FINAL" -> informeFinal(c, observaciones);
            default -> throw new IllegalArgumentException("Plantilla sin composición: " + plantilla.clave());
        };
    }

    // ── GCCON-F-018 ─────────────────────────────────────────────────────────

    private static List<BloqueDocumento> actaDeInicio(Contrato c, LocalDate hoy, String observaciones) {
        List<BloqueDocumento> b = new ArrayList<>();
        b.add(new Ficha(List.of(
                new Campo("CONTRATO NRO.", c.getNumeroContrato()),
                new Campo("TIPO DE CONTRATO", o(c.getTipoContrato())),
                new Campo("OBJETO", c.getObjeto()),
                new Campo("VALOR DEL CONTRATO", valor(c.getValor())),
                new Campo("PLAZO DEL CONTRATO", plazo(c)),
                new Campo("LUGAR DE EJECUCIÓN", o(c.getLugarEjecucion())),
                new Campo("CONTRATISTA", o(c.getContratista())),
                new Campo("CC O NIT", o(c.getContratistaNit())),
                new Campo("REPRESENTANTE LEGAL", o(c.getRepresentanteLegal())),
                new Campo("SUPERVISOR DESIGNADO", supervisor(c)))));
        b.add(new Parrafo("El día " + hoy.format(FECHA_LARGA) + ", entre los suscritos " + supervisor(c)
                + ", en calidad de supervisor del contrato y, de otra parte, " + o(c.getRepresentanteLegal())
                + ", en calidad de representante legal de " + o(c.getContratista())
                + ", identificada con NIT " + o(c.getContratistaNit())
                + ", hemos convenido suscribir el acta de inicio del contrato de la referencia, de conformidad"
                + " con los términos que anteceden y los siguientes:"));
        b.add(new Ficha(List.of(
                new Campo("Número y fecha del registro presupuestal", registroPresupuestal(c)),
                new Campo("Fecha de aprobación de las garantías", "[dato pendiente: fecha de aprobación de las garantías]"),
                new Campo("Fecha de inicio", fecha(c.getFechaInicio())),
                new Campo("Fecha de terminación", fecha(c.getFechaFin())))));
        b.add(new Parrafo("Se deja constancia de la verificación de los documentos o requisitos establecidos en el"
                + " contrato para iniciar la ejecución."));
        observaciones(b, "Observaciones del supervisor", observaciones, null);
        b.add(new Parrafo("Por el contratista: " + o(c.getRepresentanteLegal()) + ", representante legal de "
                + o(c.getContratista()) + " (NIT " + o(c.getContratistaNit()) + ").   Firma: ____________________"));
        return b;
    }

    // ── GCCON-F-031 (bienes y servicios) ────────────────────────────────────

    private static List<BloqueDocumento> informeDeSupervision(Contrato c, LocalDate hoy, String observaciones) {
        List<BloqueDocumento> b = new ArrayList<>();
        b.add(new Seccion("1. ASPECTOS GENERALES"));
        b.add(new Ficha(List.of(
                new Campo("CONTRATANTE", CONTRATANTE),
                new Campo("CONTRATO NRO.", c.getNumeroContrato()),
                new Campo("OBJETO", c.getObjeto()),
                new Campo("CONTRATISTA", o(c.getContratista())),
                new Campo("CC O NIT", o(c.getContratistaNit())),
                new Campo("REPRESENTANTE LEGAL", o(c.getRepresentanteLegal())),
                new Campo("FECHA DE INICIO", fecha(c.getFechaInicio())),
                new Campo("FECHA DE TERMINACIÓN", fecha(c.getFechaFin())),
                new Campo("VALOR DEL CONTRATO", valor(c.getValor())),
                new Campo("REGISTRO PRESUPUESTAL", registroPresupuestal(c)),
                new Campo("LUGAR DE EJECUCIÓN", o(c.getLugarEjecucion())),
                new Campo("SUPERVISOR", supervisor(c)),
                new Campo("FECHA DEL INFORME", fecha(hoy)),
                new Campo("PERIODO QUE CUBRE EL INFORME", "[dato pendiente: periodo que cubre el informe]"))));
        b.add(new Seccion("1.1 Garantías contractuales"));
        b.add(new Ficha(List.of(
                new Campo("ASEGURADORA", "[dato pendiente]"),
                new Campo("NRO. DE PÓLIZA", "[dato pendiente]"),
                new Campo("FECHA DE APROBACIÓN", "[dato pendiente]"))));
        observaciones(b, "2. SEGUIMIENTO A LA EJECUCIÓN", observaciones,
                "[dato pendiente: descripción de la ejecución en el periodo, entregas verificadas y novedades]");
        b.add(new Seccion("3. ASPECTOS FINANCIEROS"));
        b.add(new Ficha(List.of(
                new Campo("VALOR DEL CONTRATO", valor(c.getValor())),
                new Campo("VALOR FACTURADO EN EL PERIODO", "[dato pendiente]"),
                new Campo("VALOR PAGADO ACUMULADO", "[dato pendiente]"),
                new Campo("SALDO POR EJECUTAR", "[dato pendiente]"))));
        b.add(new Seccion("4. CONCEPTO DEL SUPERVISOR"));
        b.add(new Parrafo("[dato pendiente: concepto del supervisor sobre el cumplimiento en el periodo"
                + " (cumple / cumple parcialmente / no cumple)]"));
        return b;
    }

    // ── GIL-F-010 ───────────────────────────────────────────────────────────

    private static List<BloqueDocumento> actaDeRecibo(Contrato c, LocalDate hoy, String observaciones) {
        List<BloqueDocumento> b = new ArrayList<>();
        b.add(new Ficha(List.of(
                new Campo("FECHA", fecha(hoy)),
                new Campo("CENTRO DE COSTO", o(c.getCentroCosto())),
                new Campo("TIPO DE ENTREGA", o(c.getTipoContrato())),
                new Campo("N° DE ACTO ADMINISTRATIVO", c.getNumeroContrato()),
                new Campo("RUBRO PRESUPUESTAL", "[dato pendiente]"),
                new Campo("PROVEEDOR CONTRATISTA", o(c.getContratista())),
                new Campo("NIT / CÉDULA DE CIUDADANÍA", o(c.getContratistaNit())),
                new Campo("VALOR TOTAL", valor(c.getValor())),
                new Campo("FECHA DE VENCIMIENTO", fecha(c.getFechaFin())),
                new Campo("OBJETO DEL CONTRATO", c.getObjeto()),
                new Campo("CANTIDAD BIENES DEVOLUTIVOS", "[dato pendiente]"),
                new Campo("CANTIDAD BIENES DE CONSUMO", "[dato pendiente]"))));
        b.add(new Seccion("RECIBIDO A SATISFACCIÓN"));
        b.add(new Parrafo("A través del siguiente documento certifico que los bienes recibidos cumplen con las"
                + " características técnicas y físicas establecidas por el SENA en el acto administrativo."));
        observaciones(b, "OBSERVACIONES", observaciones,
                "[dato pendiente: bienes recibidos y sus cantidades]");
        b.add(new Seccion("SUPERVISOR"));
        b.add(new Ficha(List.of(
                new Campo("NOMBRE COMPLETO", supervisor(c)),
                new Campo("CORREO INSTITUCIONAL", c.getSupervisor() != null ? o(c.getSupervisor().getEmail()) : "[dato pendiente]"),
                new Campo("N° DE IDENTIFICACIÓN", "[dato pendiente]"),
                new Campo("CARGO", "[dato pendiente]"))));
        return b;
    }

    // ── Certificado del supervisor (ESUCON en el CTMA) ──────────────────────

    private static List<BloqueDocumento> certificacion(Contrato c, String observaciones) {
        List<BloqueDocumento> b = new ArrayList<>();
        b.add(new Parrafo("EL SUPERVISOR DEL CONTRATO NO. " + c.getNumeroContrato() + ", SUSCRITO CON "
                + o(c.getContratista()) + ", DANDO CUMPLIMIENTO A LA RESOLUCIÓN 0069 DE 2014 -MANUAL DE SUPERVISIÓN-"));
        b.add(new Seccion("CERTIFICA:"));
        b.add(new Parrafo("1. Que " + o(c.getContratista()) + ", con NIT " + o(c.getContratistaNit())
                + ", es proveedor del Centro Tecnológico del Mobiliario, según consta en el contrato No. "
                + c.getNumeroContrato() + ", cuyo objeto lo constituye " + c.getObjeto()
                + ", celebrado por un valor total de " + valor(c.getValor()) + "."));
        b.add(new Parrafo("2. Que la ejecución comenzó el día " + fechaLarga(c.getFechaInicio()) + "."));
        b.add(new Parrafo("3. Que, durante la ejecución, el proveedor ha prestado el servicio de acuerdo con lo"
                + " requerido, como consta en el informe de supervisión. Por lo anterior se recomienda tramitar el"
                + " pago de la factura [dato pendiente: número y fecha de la factura] por valor de"
                + " [dato pendiente: valor de la factura], previa constancia escrita de aportes al sistema de"
                + " seguridad social y parafiscales, los cuales se anexan."));
        b.add(new Parrafo("4. Que, una vez efectuado este pago, el saldo por ejecutar en el presente contrato será de"
                + " [dato pendiente: saldo por ejecutar]."));
        b.add(new Seccion("Imputación presupuestal"));
        b.add(new Ficha(List.of(
                new Campo("DEPENDENCIA / CENTRO DE COSTO", o(c.getCentroCosto())),
                new Campo("RUBRO PRESUPUESTAL", "[dato pendiente]"),
                new Campo("REGISTRO PRESUPUESTAL", registroPresupuestal(c)),
                new Campo("VALOR A OBLIGAR", "[dato pendiente]"))));
        observaciones(b, "Observaciones del supervisor", observaciones, null);
        return b;
    }

    // ── GCCON-F-030 ─────────────────────────────────────────────────────────

    private static List<BloqueDocumento> informeFinal(Contrato c, String observaciones) {
        List<BloqueDocumento> b = new ArrayList<>();
        b.add(new Parrafo("En mi calidad de supervisor del contrato de la referencia, me permito presentar el informe"
                + " final del mismo, de acuerdo con la siguiente información:"));
        b.add(new Seccion("1. ASPECTOS GENERALES"));
        b.add(new Ficha(List.of(
                new Campo("CONTRATANTE", CONTRATANTE),
                new Campo("TIPO DE CONTRATO", o(c.getTipoContrato())),
                new Campo("CONTRATO NRO.", c.getNumeroContrato()),
                new Campo("OBJETO", c.getObjeto()),
                new Campo("FECHA DE INICIO", fecha(c.getFechaInicio())),
                new Campo("PLAZO INICIAL", plazo(c)),
                new Campo("FECHA DE TERMINACIÓN INICIAL", fecha(c.getFechaFin())),
                new Campo("RAZÓN SOCIAL", o(c.getContratista())),
                new Campo("CC O NIT", o(c.getContratistaNit())),
                new Campo("NOMBRE DEL REPRESENTANTE LEGAL", o(c.getRepresentanteLegal())),
                new Campo("LUGAR DE EJECUCIÓN", o(c.getLugarEjecucion())),
                new Campo("VALOR INICIAL", valor(c.getValor())),
                new Campo("FORMA DE PAGO", "[dato pendiente]"),
                new Campo("CERTIFICADO DE DISPONIBILIDAD PRESUPUESTAL", "[dato pendiente]"),
                new Campo("CERTIFICADO DE REGISTRO PRESUPUESTAL", registroPresupuestal(c)),
                new Campo("VALOR FINAL DEL NEGOCIO JURÍDICO", "[dato pendiente]"),
                new Campo("FECHA DE TERMINACIÓN FINAL", "[dato pendiente]"),
                new Campo("FECHA DE TERMINACIÓN ANTICIPADA (SI APLICA)", "[dato pendiente]"),
                new Campo("VALOR TOTAL PAGADO", "[dato pendiente]"),
                new Campo("VALOR TOTAL EJECUTADO", "[dato pendiente]"),
                new Campo("SUPERVISOR", supervisor(c)))));
        b.add(new Seccion("2. ASPECTOS TÉCNICOS"));
        b.add(new Parrafo("En virtud de la suscripción del contrato " + c.getNumeroContrato()
                + ", el contratista adquirió obligaciones cuyo cumplimiento se relaciona a continuación."));
        observaciones(b, null, observaciones,
                "[dato pendiente: relación de obligaciones, si se cumplieron y el producto o evidencia de cada una]");
        b.add(new Seccion("3. ASPECTOS FINANCIEROS"));
        b.add(new Ficha(List.of(
                new Campo("VALOR INICIAL", valor(c.getValor())),
                new Campo("VALOR TOTAL PAGADO", "[dato pendiente]"),
                new Campo("VALOR POR PAGAR O A LIBERAR", "[dato pendiente]"))));
        b.add(new Seccion("4. CONCLUSIÓN"));
        b.add(new Parrafo("[dato pendiente: certificación del cumplimiento total o parcial del objeto contractual]"));
        return b;
    }

    // ── Piezas comunes ──────────────────────────────────────────────────────

    /**
     * El apartado de observaciones: el texto del supervisor si lo hay; si no,
     * el marcador de pendiente del formato (o nada, si el formato no lo exige).
     */
    private static void observaciones(List<BloqueDocumento> b, String titulo, String texto, String siFalta) {
        boolean hay = texto != null && !texto.isBlank();
        if (!hay && siFalta == null) {
            return;
        }
        if (titulo != null) {
            b.add(new Seccion(titulo));
        }
        b.add(new Parrafo(hay ? texto.strip() : siFalta));
    }

    static String valor(BigDecimal v) {
        if (v == null) {
            return "[dato pendiente]";
        }
        DecimalFormatSymbols simbolos = new DecimalFormatSymbols(Locale.of("es", "CO"));
        simbolos.setGroupingSeparator('.');
        simbolos.setDecimalSeparator(',');
        boolean conCentavos = v.stripTrailingZeros().scale() > 0;
        DecimalFormat formato = new DecimalFormat(conCentavos ? "#,##0.00" : "#,##0", simbolos);
        return "$" + formato.format(v) + " (" + NumeroEnLetras.pesos(v) + ")";
    }

    private static String plazo(Contrato c) {
        if (c.getFechaInicio() == null || c.getFechaFin() == null) {
            return "[dato pendiente]";
        }
        return "Del " + fecha(c.getFechaInicio()) + " al " + fecha(c.getFechaFin());
    }

    private static String registroPresupuestal(Contrato c) {
        String numero = c.getNumeroRegistroPresupuestal();
        if (numero == null || numero.isBlank()) {
            return "[dato pendiente]";
        }
        return c.getFechaRegistroPresupuestal() != null
                ? numero + " del " + fecha(c.getFechaRegistroPresupuestal())
                : numero;
    }

    private static String supervisor(Contrato c) {
        return c.getSupervisor() != null ? c.getSupervisor().getNombre() : "[dato pendiente: supervisor sin asignar]";
    }

    private static String fecha(LocalDate f) {
        return f != null ? f.format(FECHA) : "[dato pendiente]";
    }

    private static String fechaLarga(LocalDate f) {
        return f != null ? f.format(FECHA_LARGA) : "[dato pendiente]";
    }

    /** Un dato opcional del contrato: el valor tal cual, o el marcador de pendiente. */
    private static String o(String valor) {
        return valor == null || valor.isBlank() ? "[dato pendiente]" : valor;
    }
}
