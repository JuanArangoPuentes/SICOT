package co.sena.sicot.ia;

import co.sena.sicot.entity.Contrato;
import co.sena.sicot.ia.BloqueDocumento.Alineacion;
import co.sena.sicot.ia.BloqueDocumento.Campo;
import co.sena.sicot.ia.BloqueDocumento.Celda;
import co.sena.sicot.ia.BloqueDocumento.DatosActaDeRecibo;
import co.sena.sicot.ia.BloqueDocumento.DisposicionFirmas;
import co.sena.sicot.ia.BloqueDocumento.Estilo;
import co.sena.sicot.ia.BloqueDocumento.Ficha;
import co.sena.sicot.ia.BloqueDocumento.Fila;
import co.sena.sicot.ia.BloqueDocumento.Firmante;
import co.sena.sicot.ia.BloqueDocumento.Firmas;
import co.sena.sicot.ia.BloqueDocumento.HojaDeRecibo;
import co.sena.sicot.ia.BloqueDocumento.Letra;
import co.sena.sicot.ia.BloqueDocumento.LineasEnBlanco;
import co.sena.sicot.ia.BloqueDocumento.Parrafo;
import co.sena.sicot.ia.BloqueDocumento.Seccion;
import co.sena.sicot.ia.BloqueDocumento.Tabla;
import co.sena.sicot.ia.BloqueDocumento.Titulo;
import co.sena.sicot.ia.BloqueDocumento.Tramo;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.Period;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Arma los cinco documentos formales del supervisor a partir de los formatos
 * reales del SENA y de los datos del contrato, sin pasar ningún dato por el
 * modelo de IA.
 *
 * <h2>Por qué los documentos no los redacta el modelo</h2>
 * En la prueba integral del 24-09-2026, con un contrato completo y el modelo
 * por defecto ({@code qwen2.5:7b}), los cinco documentos salieron con errores
 * que invalidan un documento oficial: el apellido de la representante legal
 * escrito de dos formas, el valor en letras equivocado y en otra acta en
 * bolívares, entidades inventadas como contratante, fechas futuras y
 * verificaciones que nadie hizo. Los formatos reales son en su mayoría fichas
 * y texto fijo; lo que cambia de un contrato a otro son los datos, y esos los
 * tiene el backend exactos. Por eso aquí se escriben con código: salen al
 * instante, iguales al contrato, y funcionan en cualquier equipo (ADR-008).
 *
 * <h2>Fieles al formato</h2>
 * Desde el 28-09-2026 cada documento sigue su formato real apartado por
 * apartado: el texto fijo se copia tal cual lo trae el formato (con su
 * ortografía, como el «cedula» sin tilde del GCCON-F-018 o el «SATISFACCION»
 * de la GIL-F-010: son formatos controlados y no le toca a SICOT corregirlos),
 * y cada dato se escribe como lo escribe el Centro en los diligenciados: el
 * valor en el Acta de Inicio es «DIEZ MILLONES DE PESOS ($10.000.000 COP)»,
 * en el Informe Final «$20.000.000,00» y en la GIL-F-010 «18.924.558». El
 * detalle de cada formato está en las especificaciones de la carpeta
 * docs/formatos del repositorio.
 *
 * <p>Una excepción deliberada: en el GCCON-F-030 del que se tomó el modelo,
 * «3.» se repite (ASPECTOS LEGALES y OBLIGACIONES DE LA ENTIDAD). Aquí los
 * apartados se numeran seguidos —3, 4 y 5— sin cambiar ningún título.
 *
 * <h2>Lo que SICOT no sabe queda a la vista</h2>
 * Número de factura, pólizas, pagos, cédulas… SICOT no los registra. El
 * supervisor los puede dar al generar (son los {@code campos} de cada
 * plantilla); los que falten salen como «[dato pendiente: …]», en rojo en el
 * PDF, en vez de inventarse.
 *
 * <p>El modelo interviene en un solo sitio: el apartado de observaciones, y
 * solo para pasar a redacción formal lo que el propio supervisor escribió (ver
 * {@code GeneracionDocumentoService}).
 */
public final class RedactorDeDocumentos {

    /** Contratante tal como lo escriben los formatos del Centro (sin espacio antes del guion). */
    static final String CONTRATANTE = "SENA- Centro Tecnológico del Mobiliario";
    /** El Centro está en Itagüí (Antioquia): ahí se suscriben sus actas. */
    private static final String CIUDAD_DEL_CENTRO = "ITAGÜÍ";
    private static final String CODIGO_REGIONAL = "5";
    private static final String REGIONAL = "ANTIOQUIA";

    private static final Locale ES = Locale.of("es", "CO");
    private static final DateTimeFormatter CORTA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter LARGA = DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", ES);
    private static final DateTimeFormatter SIN_CERO = DateTimeFormatter.ofPattern("d/MM/yyyy");
    private static final DateTimeFormatter ISO = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final Pattern ANO = Pattern.compile("\\b(19|20)\\d{2}\\b");

    private RedactorDeDocumentos() {
    }

    /** Sin datos complementarios: lo que el contrato no tiene sale como pendiente. */
    public static List<BloqueDocumento> componer(PlantillaDocumentoIA plantilla, Contrato c, LocalDate hoy,
                                                 String observaciones) {
        return componer(plantilla, c, hoy, observaciones, Map.of());
    }

    /**
     * @param observaciones texto del apartado de observaciones ya redactado, o
     *                      {@code null} si el supervisor no aportó notas.
     * @param datos         datos complementarios que dio el supervisor, por
     *                      clave (ver {@link PlantillaDocumentoIA#campos}).
     */
    public static List<BloqueDocumento> componer(PlantillaDocumentoIA plantilla, Contrato c, LocalDate hoy,
                                                 String observaciones, Map<String, String> datos) {
        return componer(plantilla, c, hoy, observaciones, datos, Map.of());
    }

    /**
     * @param tablas filas de las tablas del formato que dio el supervisor, por
     *               clave (ver {@link PlantillaDocumentoIA#tablas}), cada una con
     *               tantas celdas como columnas.
     */
    public static List<BloqueDocumento> componer(PlantillaDocumentoIA plantilla, Contrato c, LocalDate hoy,
                                                 String observaciones, Map<String, String> datos,
                                                 Map<String, List<List<String>>> tablas) {
        Datos d = new Datos(plantilla, datos == null ? Map.of() : datos, tablas == null ? Map.of() : tablas);
        String obs = observaciones == null || observaciones.isBlank() ? null : observaciones.strip();
        return switch (plantilla.clave()) {
            case "ACTA_INICIO" -> actaDeInicio(c, hoy, d);
            case "INFORME_SUPERVISION" -> informeDeSupervision(c, hoy, obs, d);
            case "ACTA_RECIBO" -> actaDeRecibo(c, hoy, obs, d);
            case "CERTIFICACION_CUMPLIMIENTO" -> certificacion(c, hoy, d);
            case "INFORME_FINAL" -> informeFinal(c, hoy, obs, d);
            default -> throw new IllegalArgumentException("Plantilla sin composición: " + plantilla.clave());
        };
    }

    // ── GCCON-F-018 Acta de Inicio ──────────────────────────────────────────

    private static List<BloqueDocumento> actaDeInicio(Contrato c, LocalDate hoy, Datos d) {
        List<BloqueDocumento> b = new ArrayList<>();
        // Una línea vacía de Calibri 11 entre la clasificación y el título.
        b.add(new BloqueDocumento.Espacio(15.44f));
        b.add(Titulo.de("PROCESO GESTIÓN CONTRACTUAL", "FORMATO ACTA DE INICIO"));
        b.add(new BloqueDocumento.Espacio(9.8f));
        b.add(new Ficha(List.of(
                new Campo("CONTRATO NRO.", c.getNumeroContrato()),
                new Campo("TIPO DE CONTRATO", mayusculas(c.getTipoContrato())),
                new Campo("OBJETO", mayusculas(c.getObjeto()), Estilo.NEGRITA_ITALICA),
                new Campo("VALOR DEL CONTRATO", valorEnLetrasYCifra(c.getValor())),
                new Campo("PLAZO DEL CONTRATO", d.o("plazo", plazo(c, false))),
                new Campo("LUGAR DE EJECUCIÓN", mayusculas(c.getLugarEjecucion())),
                new Campo("CONTRATISTA", mayusculas(c.getContratista())),
                new Campo("CC o NIT", o(c.getContratistaNit())),
                new Campo("REPRESENTANTE LEGAL", mayusculas(c.getRepresentanteLegal())),
                new Campo("SUPERVISOR DESIGNADO", mayusculas(supervisor(c)))), 0.2896f, 3.5f, true));
        b.add(new BloqueDocumento.Espacio(15.44f));
        String contratista = mayusculas(c.getContratista());
        b.add(new Parrafo("En Itagüí- Antioquia el día " + hoy.format(LARGA) + ", entre los suscritos "
                + mayusculas(supervisor(c)) + ", identificado con cedula de ciudadanía nro. "
                + d.pendienteSiFalta("cedulaSupervisor") + ", en calidad de supervisor y, de otra parte, "
                + mayusculas(c.getRepresentanteLegal()) + ", identificado con cedula de ciudadanía nro. "
                + d.pendienteSiFalta("cedulaRepresentante") + " de " + d.pendienteSiFalta("expedicionCedulaRepresentante")
                + ", en calidad de representante legal de " + contratista
                + ", identificada con NIT. " + o(c.getContratistaNit())
                + ", hemos convenido suscribir el acta de inicio del contrato de la referencia, de conformidad con"
                + " los términos que anteceden y los siguientes:"));
        b.add(new BloqueDocumento.Espacio(18.2f));
        b.add(new Ficha(List.of(
                new Campo("Número y fecha del registro presupuestal", registroPresupuestal(c, LARGA, " del ")),
                new Campo("Fecha de aprobación de las garantías", d.fecha("fechaAprobacionGarantias", LARGA)),
                new Campo("Fecha de inicio", fecha(c.getFechaInicio(), LARGA)),
                new Campo("Fecha de terminación", fecha(c.getFechaFin(), LARGA))), 0.4275f, 5.4f, true));
        b.add(new LineasEnBlanco(1));
        b.add(new Parrafo("Se deja constancia de la verificación de los documentos o requisitos establecidos en el"
                + " contrato para iniciar la ejecución."));
        List<String> contratistaFirma = new ArrayList<>(List.of("NIT. " + o(c.getContratistaNit()), contratista,
                "Representante Legal"));
        if (d.tiene("correoContratista")) {
            contratistaFirma.add(d.valor("correoContratista"));
        }
        b.add(new Firmas(List.of(
                new Firmante(mayusculas(supervisor(c)), false, List.of("Supervisor")),
                new Firmante(mayusculas(c.getRepresentanteLegal()), false, contratistaFirma)),
                // El acta real deja dos renglones (28 pt) para la firma manuscrita;
                // aquí van tres, para que la firma electrónica estampada se lea.
                DisposicionFirmas.COLUMNAS, 42.12f));
        return b;
    }

    // ── GCCON-F-031 Informe de Supervisión (bienes y servicios) ─────────────

    private static List<BloqueDocumento> informeDeSupervision(Contrato c, LocalDate hoy, String obs, Datos d) {
        List<BloqueDocumento> b = new ArrayList<>();
        // Con una adición o una prórroga declaradas, el valor y la fecha del
        // contrato registrados en SICOT ya no son los actuales: se usan los que
        // dé el supervisor o quedan pendientes, en vez de escribir los viejos.
        boolean conAdicion = d.tiene("adicion") && !esNo(d.valor("adicion"));
        boolean conProrroga = d.tiene("prorroga") && !esNo(d.valor("prorroga"));
        BigDecimal valorActual = d.tiene("valorActual") ? d.pesos("valorActual") : conAdicion ? null : c.getValor();
        String valorActualTexto = d.tiene("valorActual") ? d.pesosTexto("valorActual")
                : conAdicion ? "[dato pendiente: valor actual con las adiciones]" : pesos(c.getValor(), false);
        String terminacionActual = d.tiene("fechaTerminacionActual") ? d.fecha("fechaTerminacionActual", CORTA)
                : conProrroga ? "[dato pendiente: fecha de terminación con las prórrogas]" : fecha(c.getFechaFin(), CORTA);
        b.add(new LineasEnBlanco(1));
        b.add(Titulo.de("INFORME DE SUPERVISIÓN – CONTRATOS DE BIENES Y SERVICIOS",
                "CONTRATO NRO. " + numeroConAno(c, d)));
        b.add(new LineasEnBlanco(1));
        seccion(b, "1.", "ASPECTOS GENERALES");
        b.add(new Letra(10f));
        b.add(new Ficha(List.of(
                new Campo("CONTRATANTE", CONTRATANTE),
                new Campo("CONTRATO NRO.", numeroConAno(c, d)),
                new Campo("FECHA DE SUSCRIPCIÓN", d.fecha("fechaSuscripcion", CORTA)),
                new Campo("OBJETO", o(c.getObjeto())),
                new Campo("CONTRATISTA", o(c.getContratista())),
                new Campo("CC o NIT", o(c.getContratistaNit())),
                new Campo("LUGAR DE EJECUCIÓN", o(c.getLugarEjecucion())),
                new Campo("FECHA DE INICIO", fecha(c.getFechaInicio(), CORTA)),
                new Campo("PLAZO INICIAL DEL CONTRATO", d.o("plazo", plazo(c, true))),
                new Campo("VALOR INICIAL DEL CONTRATO", pesos(c.getValor(), false)),
                new Campo("PRÓRROGA NRO.", d.o("prorroga", "[dato pendiente: prórrogas, o N/A si no hubo]")),
                new Campo("FECHA DE TERMINACIÓN", terminacionActual),
                new Campo("ADICIÓN NRO.", d.o("adicion", "[dato pendiente: adiciones, o N/A si no hubo]")),
                new Campo("VALOR ACTUAL DEL CONTRATO", valorActualTexto),
                new Campo("FORMA DE PAGO", d.pendienteSiFalta("formaDePago")),
                new Campo("INFORME DE SUPERVISIÓN NRO.", d.pendienteSiFalta("informeNumero")),
                new Campo("PERIODO DEL INFORME", d.tiene("periodoDesde") || d.tiene("periodoHasta")
                        ? "Desde el " + d.fecha("periodoDesde", CORTA) + " al " + d.fecha("periodoHasta", CORTA)
                        : "[dato pendiente: periodo que cubre el informe]")),
                0.353f, 5.4f, false));
        b.add(new Letra(11f));
        b.add(new LineasEnBlanco(1));
        seccion(b, "1.1.", "Garantías contractuales");
        b.add(garantia(d, List.of("Cumplimiento", "Devolución del pago anticipado", "Salarios y prestaciones sociales",
                "Calidad del servicio")));
        b.add(new LineasEnBlanco(2));
        seccion(b, "2.", "EJECUCIÓN CONTRACTUAL");
        b.add(new Letra(10f));
        // Una fila por obligación específica, como pide el formato
        // («Relacionar cada una de las obligaciones específicas»).
        List<Fila> ejecucion = new ArrayList<>();
        ejecucion.add(Fila.de(Celda.encabezado("OBLIGACIONES"), Celda.encabezado("ACTIVIDADES REALIZADAS"),
                Celda.encabezado("PRODUCTO O EVIDENCIA")));
        List<List<String>> especificas = d.filas("obligacionesEspecificas");
        if (especificas.isEmpty()) {
            ejecucion.add(Fila.de(new Celda(List.of(Tramo.negrita("1. "),
                            Tramo.normal("[dato pendiente: obligaciones específicas del contrato]")),
                            1, 1, Alineacion.JUSTIFICADO, false, false),
                    Celda.de("[dato pendiente: actividades realizadas en el periodo]"),
                    Celda.de("[dato pendiente: producto o evidencia]")));
        }
        for (int i = 0; i < especificas.size(); i++) {
            List<String> fila = especificas.get(i);
            ejecucion.add(Fila.de(new Celda(List.of(Tramo.negrita((i + 1) + ". "), Tramo.normal(obligacion(fila))),
                            1, 1, Alineacion.JUSTIFICADO, false, false),
                    Celda.de(celda(fila, 1)), Celda.de(celda(fila, 2))));
        }
        b.add(new Tabla(List.of(156.6f, 156.6f, 156.6f), ejecucion, 1, false, 5.4f));
        b.add(new Letra(11f));
        b.add(new LineasEnBlanco(1));
        seccion(b, "2.1.", "Cumplimiento de obligaciones referentes al Sistema Integrado de Gestión y Autocontrol – SIGA");
        // El formato pide digitar «No aplica» si el contrato no tiene estas obligaciones.
        b.add(new Parrafo(!d.tiene("siga")
                ? "[dato pendiente: cumplimiento de las obligaciones ambientales, de seguridad y salud en el trabajo y"
                        + " de gestión energética, o «No aplica.»]"
                : esSoloNo(d.valor("siga")) ? "No aplica." : d.valor("siga")));
        b.add(new LineasEnBlanco(2));
        seccion(b, "3.", "AVANCE FINANCIERO DEL CONTRATO");
        b.add(new Letra(10f));
        BigDecimal ejecutado = d.pesos("valorEjecutado");
        // Un ejecutado mayor que el valor del contrato es una inconsistencia de
        // los datos, no un saldo negativo que se pueda firmar.
        boolean consistente = ejecutado != null && valorActual != null && ejecutado.compareTo(valorActual) <= 0;
        String saldo = consistente ? pesos(valorActual.subtract(ejecutado), false)
                : ejecutado != null && valorActual != null ? "[dato pendiente: el ejecutado supera el valor del contrato]"
                : "[dato pendiente]";
        String porcentaje = consistente && valorActual.signum() > 0 ? porcentaje(ejecutado, valorActual)
                : "[dato pendiente]";
        b.add(new Tabla(List.of(69.4f, 65.5f, 77.6f, 74.4f, 77.3f, 105.6f), List.of(
                Fila.de(Celda.encabezado("FECHA DEL INFORME"), Celda.encabezado("NRO. DE FACTURA"),
                        Celda.encabezado("VALOR FACTURADO"), Celda.encabezado("VALOR EJECUTADO"),
                        Celda.encabezado("SALDO DEL CONTRATO"),
                        Celda.encabezado("PORCENTAJE DE EJECUCIÓN FINANCIERA")),
                Fila.de(Celda.de(hoy.format(CORTA)).alineada(Alineacion.CENTRO),
                        Celda.de(d.pendienteSiFalta("numeroFactura")).alineada(Alineacion.CENTRO),
                        Celda.de(d.pesosTexto("valorFacturado")).alineada(Alineacion.CENTRO),
                        Celda.de(d.pesosTexto("valorEjecutado")).alineada(Alineacion.CENTRO),
                        Celda.de(saldo).alineada(Alineacion.CENTRO),
                        Celda.de(porcentaje).alineada(Alineacion.CENTRO))), 1, false, 5.4f));
        b.add(new Letra(11f));
        b.add(new LineasEnBlanco(2));
        seccion(b, "4.", "RELACIÓN DE PAGOS DE SEGURIDAD SOCIAL");
        b.add(new Letra(9f));
        b.add(new Tabla(List.of(64.8f, 202.6f, 202.4f), List.of(Fila.de(
                new Celda(List.of(Tramo.negrita("SALUD, PENSIÓN Y ARL")), 1, 1, Alineacion.IZQUIERDA, false, true),
                new Celda(List.of(Tramo.negrita("Periodo reportado"),
                        Tramo.normal(" " + d.pendienteSiFalta("periodoSeguridadSocial"))),
                        1, 1, Alineacion.JUSTIFICADO, false, true),
                new Celda(List.of(Tramo.negrita("Planilla nro."), Tramo.normal(" " + d.pendienteSiFalta("numeroPlanilla")),
                        Tramo.negrita(" del "), Tramo.normal(d.fecha("fechaPlanilla", CORTA))),
                        1, 1, Alineacion.JUSTIFICADO, false, true))), 0, false, 5.4f));
        b.add(new Letra(11f));
        b.add(new LineasEnBlanco(2));
        seccion(b, "5.", "MULTAS Y SANCIONES");
        // Sin interventoría —lo normal en mínima cuantía— la orientación «[si
        // aplica]» del formato quita la referencia a su informe. La
        // certificación de que no hubo multas es del supervisor: solo se
        // escribe si él la declara.
        if (d.tiene("multas") && esNo(d.valor("multas"))) {
            b.add(new Parrafo("A la fecha de presentación del presente informe, se certifica como supervisor del"
                    + " contrato que no se han presentado multas, indemnizaciones, reintegros ni sanciones."));
        } else if (d.tiene("multas")) {
            b.add(new Parrafo("A la fecha de presentación del presente informe se han presentado las siguientes multas,"
                    + " indemnizaciones, reintegros o sanciones: " + d.valor("multas")));
        } else {
            b.add(new Parrafo("[dato pendiente: multas, indemnizaciones, reintegros o sanciones en el periodo, o NO si"
                    + " no las hubo]"));
        }
        b.add(new LineasEnBlanco(2));
        seccion(b, "6.", "JUSTIFICACIÓN PARA LA MODIFICACIÓN");
        b.add(new Parrafo("No aplica"));
        b.add(new LineasEnBlanco(2));
        seccion(b, "7.", "CERTIFICACIÓN");
        b.add(new Parrafo("Con la firma del presente informe, en mi calidad de supervisor, previa revisión de los"
                + " documentos en la plataforma SECOP II, certifico el cumplimiento a cabalidad de las obligaciones"
                + " establecidas en el contrato por parte del contratista y la plena autonomía en desarrollo de sus"
                + " actividades durante el respectivo periodo. Con base en lo anterior, autorizo el pago conforme lo"
                + " pactado contractualmente."));
        b.add(new LineasEnBlanco(2));
        seccion(b, "8.", "OBSERVACIONES");
        b.add(new Parrafo(obs != null ? obs
                : "[dato pendiente: novedades de la ejecución en el periodo, entregas verificadas y soportes]"));
        b.add(new LineasEnBlanco(1));
        b.add(new BloqueDocumento.MantenerJunto(150f));
        b.add(new Parrafo(Alineacion.IZQUIERDA, Tramo.normal("Para constancia se firma " + hoy.format(CORTA) + ".")));
        b.add(new Letra(10f));
        b.add(new Firmas(List.of(new Firmante(supervisor(c), true, List.of("Supervisor del contrato")),
                new Firmante("", false, List.of())), DisposicionFirmas.TABLA, 61.6f));
        b.add(new LineasEnBlanco(1));
        b.add(new Parrafo(Alineacion.IZQUIERDA, Tramo.normal("Elaboró: " + supervisor(c) + " – Supervisor del contrato")));
        return b;
    }

    /** Tabla «GARANTÍA ÚNICA DE CUMPLIMIENTO» de los GCCON-F-030 y GCCON-F-031. */
    private static Tabla garantia(Datos d, List<String> amparos) {
        List<Fila> filas = new ArrayList<>();
        filas.add(Fila.de(Celda.encabezado("GARANTÍA ÚNICA DE CUMPLIMIENTO").abarcando(4, 1)));
        String[][] rotulos = {{"ASEGURADORA", "aseguradora"}, {"NRO. DE PÓLIZA", "numeroPoliza"},
                {"CERTIFICADO O ANEXO", "certificadoAnexo"}, {"FECHA EXPEDICIÓN", "fechaExpedicionPoliza"},
                {"FECHA APROBACIÓN", "fechaAprobacionGarantias"}};
        for (String[] r : rotulos) {
            String valor = r[1].startsWith("fecha") ? d.fecha(r[1], CORTA) : d.pendienteSiFalta(r[1]);
            filas.add(Fila.de(Celda.de(r[0]).enNegrita(), Celda.de(valor).abarcando(3, 1)));
        }
        filas.add(Fila.de(Celda.encabezado("AMPARO").abarcando(1, 2), Celda.encabezado("VIGENCIA").abarcando(2, 1),
                Celda.encabezado("VALOR").abarcando(1, 2)));
        filas.add(Fila.de(Celda.encabezado("DESDE"), Celda.encabezado("HASTA")));
        List<List<String>> dados = d.filas("amparos");
        if (!dados.isEmpty()) {
            // Los amparos de la póliza, como en el Informe Final real: solo los
            // que tiene, con su vigencia y su valor.
            for (List<String> fila : dados) {
                filas.add(Fila.de(Celda.de(celda(fila, 0)).alineada(Alineacion.CENTRO),
                        Celda.de(fechaDeCelda(celda(fila, 1))).alineada(Alineacion.CENTRO),
                        Celda.de(fechaDeCelda(celda(fila, 2))).alineada(Alineacion.CENTRO),
                        Celda.de(pesosDeCelda(celda(fila, 3), true)).alineada(Alineacion.CENTRO)));
            }
        } else {
            // Sin amparos, la lista del formato. Hasta el 30-09-2026 sus celdas
            // salían en blanco, y un documento firmado con la vigencia y el
            // valor vacíos no dice que falten: ahora dice que están pendientes,
            // o «No aplica» si el supervisor declaró que no hay póliza.
            String vacio = d.tiene("aseguradora") && esNo(d.valor("aseguradora")) ? "No aplica"
                    : "[dato pendiente: vigencia y valor]";
            for (String amparo : amparos) {
                filas.add(Fila.de(Celda.de(amparo).alineada(Alineacion.CENTRO),
                        Celda.de(vacio).abarcando(3, 1).alineada(Alineacion.CENTRO)));
            }
        }
        return new Tabla(List.of(135.6f, 128.0f, 95.5f, 110.8f), filas, 1, false, 5.75f);
    }

    // ── GIL-F-010 Acta de Recibo a Satisfacción de Bienes ───────────────────

    private static List<BloqueDocumento> actaDeRecibo(Contrato c, LocalDate hoy, String obs, Datos d) {
        String corto = "[dato pendiente]";
        // El valor total y la fecha de vencimiento son los actuales, como en el
        // Informe de Supervisión: con una adición o una prórroga declaradas,
        // los que dé el supervisor o pendientes. Aquí además el acta no tiene
        // casilla para la adición ni la prórroga, así que si no se declaran
        // tampoco se afirman los iniciales: hasta la auditoría del 02-10-2026
        // el acta imprimía el valor y la fecha registrados aunque el contrato
        // tuviera adiciones, y contradecía al informe del mismo contrato.
        String valorTotal;
        if (d.tiene("valorActual")) {
            BigDecimal v = d.pesos("valorActual");
            valorTotal = v != null ? entero(v) : d.valor("valorActual");
        } else {
            valorTotal = esNo(d.valor("adicion")) && c.getValor() != null ? entero(c.getValor()) : corto;
        }
        String fechaVencimiento = d.tiene("fechaTerminacionActual") ? d.fecha("fechaTerminacionActual", CORTA)
                : esNo(d.valor("prorroga")) && c.getFechaFin() != null ? c.getFechaFin().format(CORTA) : corto;
        DatosActaDeRecibo datos = new DatosActaDeRecibo(
                d.o("actaNumero", corto),
                hoy.format(DateTimeFormatter.ofPattern("dd 'de' MMMM yyyy", ES)),
                CIUDAD_DEL_CENTRO,
                CODIGO_REGIONAL,
                REGIONAL,
                c.getCentroCosto() != null && !c.getCentroCosto().isBlank() ? c.getCentroCosto() : corto,
                d.o("codigoCentroCosto", corto),
                d.mayusculas("tipoAdquisicion", corto),
                d.mayusculas("tipoEntrega", corto),
                c.getNumeroContrato(),
                d.tiene("fechaSuscripcion") ? d.fecha("fechaSuscripcion", CORTA) : corto,
                d.o("rubroPresupuestal", corto),
                c.getContratista() != null ? mayusculas(c.getContratista()) : corto,
                c.getContratistaNit() != null && !c.getContratistaNit().isBlank()
                        ? soloDigitosSinVerificacion(c.getContratistaNit()) : corto,
                valorTotal,
                fechaVencimiento,
                c.getObjeto() != null ? mayusculas(c.getObjeto()) : corto,
                d.o("cantidadDevolutivos", corto),
                d.o("cantidadConsumo", corto),
                obs != null ? obs : "[dato pendiente: bienes recibidos y sus cantidades]",
                c.getSupervisor() != null ? mayusculas(c.getSupervisor().getNombre()) : corto,
                d.tiene("cedulaSupervisor") ? soloDigitosSinVerificacion(d.valor("cedulaSupervisor")) : corto,
                c.getSupervisor() != null && c.getSupervisor().getEmail() != null
                        ? c.getSupervisor().getEmail().toLowerCase(ES) : corto,
                d.mayusculas("cargoSupervisor", corto),
                c.getSupervisor() != null && c.getSupervisor().getTelefono() != null
                        && !c.getSupervisor().getTelefono().isBlank() ? c.getSupervisor().getTelefono() : corto);
        return List.of(new HojaDeRecibo(datos));
    }

    // ── Certificado del supervisor (ESUCON en el CTMA) ──────────────────────

    private static List<BloqueDocumento> certificacion(Contrato c, LocalDate hoy, Datos d) {
        List<BloqueDocumento> b = new ArrayList<>();
        String contratista = mayusculas(c.getContratista());
        LocalDate fechaContrato = d.tiene("fechaSuscripcion") ? d.fechaComoFecha("fechaSuscripcion") : c.getFechaInicio();
        String fechaCorta = fechaContrato != null ? fechaContrato.format(SIN_CERO) : "[dato pendiente]";
        b.add(new Letra(12f, 1f));
        b.add(new LineasEnBlanco(2));
        b.add(new Parrafo(Alineacion.CENTRO, Tramo.negrita("EL SUPERVISOR DE CONTRATO NO. " + c.getNumeroContrato()
                + " DEL " + fechaCorta + " " + contratista
                + ", DANDO CUMPLIMIENTO A LA RESOLUCIÓN 0069 DE 2014, -MANUAL DE SUPERVISIÓN-")));
        b.add(new LineasEnBlanco(2));
        b.add(new Parrafo(Alineacion.CENTRO, Tramo.negrita("CERTIFICA:")));
        b.add(new LineasEnBlanco(2));
        // El numeral 1 no lleva número ni «Que» en los certificados reales.
        b.add(new Parrafo(Alineacion.JUSTIFICADO,
                Tramo.negrita(contratista), Tramo.normal(" con Nit. "), Tramo.negrita(o(c.getContratistaNit())),
                Tramo.normal(" es proveedor del Centro Tecnológico del Mobiliario, según consta En el contrato "),
                Tramo.negrita("NO. " + c.getNumeroContrato() + " DEL " + fechaCorta),
                Tramo.normal(", cuyo objeto lo constituye "), Tramo.negrita(mayusculas(c.getObjeto()))));
        b.add(new LineasEnBlanco(2));
        b.add(new Parrafo(Alineacion.JUSTIFICADO,
                Tramo.normal("Celebrado por un valor total de " + pesos(c.getValor(), true))));
        b.add(new LineasEnBlanco(1));
        b.add(new Parrafo("2. Que la ejecución comenzó el día " + fecha(c.getFechaInicio(), LARGA) + "."));
        b.add(new LineasEnBlanco(1));
        b.add(new Parrafo(Alineacion.JUSTIFICADO,
                Tramo.normal("3. Que, durante la ejecución, el proveedor ha prestado el servicio de acuerdo con lo"
                        + " requerido como consta en el informe de supervisión. Por lo anterior se recomienda tramitar"
                        + " el pago de la factura " + d.pendienteSiFalta("numeroFactura") + " del "
                        + d.fecha("fechaFactura", LARGA) + " por valor de " + d.pesosTexto("valorFactura", true)
                        + " recibida por el aplicativo SIIF, el cual se efectuará mediante transferencia bancaria a"
                        + " favor del contratista en la Cuenta " + d.mayusculas("tipoCuenta", d.pendiente("tipoCuenta"))
                        + " Banco " + d.mayusculas("banco", d.pendiente("banco")) + " No. "),
                Tramo.negrita(d.pendienteSiFalta("numeroCuenta")), Tramo.normal(" a nombre de "),
                Tramo.negrita(d.tiene("titularCuenta") ? d.valor("titularCuenta").toUpperCase(ES)
                        : "[dato pendiente: titular de la cuenta]"),
                Tramo.normal(", previa constancia escrita de aportes al Sistema de seguridad social y parafiscales,"
                        + " los cuales se anexan.")));
        b.add(new LineasEnBlanco(1));
        b.add(new Parrafo("4.  Que, una vez efectuado este pago, el saldo por ejecutar en el presente contrato será de "
                + d.pesosTexto("saldoPorEjecutar", true) + "."));
        b.add(new LineasEnBlanco(2));
        b.add(new Letra(9.5f, 1f));
        b.add(new Tabla(List.of(86.1f, 98.9f, 52.7f, 98.9f, 84.7f, 85.0f, 69.7f), List.of(
                Fila.de(plana("DEPENDENCIA"), plana("RUBRO PRESUPUESTAL"), plana("REC"), plana("NOMBRE RUBRO"),
                        plana("NOMBRE DE USO PRESUPUESTAL"), plana("CODIGO DE USO PRESUPUESTAL"),
                        plana("VALOR A OBLIGAR")),
                Fila.de(Celda.de(c.getCentroCosto() != null && !c.getCentroCosto().isBlank()
                                ? c.getCentroCosto() : "[dato pendiente]"),
                        plana(d.pendienteSiFalta("rubroPresupuestal")),
                        plana(d.mayusculas("fuenteRecurso", d.pendiente("fuenteRecurso"))),
                        plana(d.pendienteSiFalta("rubroPresupuestal")),
                        plana(d.mayusculas("usoPresupuestal", d.pendiente("usoPresupuestal"))),
                        plana(d.pendienteSiFalta("codigoUsoPresupuestal")),
                        plana(d.pesosTexto("valorAObligar", true)).enNegrita())), 1, true, 3f));
        b.add(new Letra(12f, 1f));
        b.add(new LineasEnBlanco(2));
        // «Itagüí, …» va en la columna de la sección (3 cm), no en la del cuerpo (1,5 cm).
        b.add(new BloqueDocumento.MantenerJunto(130f));
        b.add(new Parrafo(List.of(Tramo.normal("Itagüí, " + hoy.format(LARGA) + ".")), Alineacion.IZQUIERDA, 42.5f));
        b.add(new Firmas(List.of(new Firmante(mayusculas(supervisor(c)), true, List.of("Supervisor"))),
                DisposicionFirmas.CENTRADA, 70f));
        return b;
    }

    /** «NO», «No aplica», «N/A», «ninguna»: lo que dice que algo no pasó. */
    static boolean esNo(String valor) {
        if (valor == null) {
            return false;
        }
        // Sin tildes: «Ningún» no empieza por «NINGUN» (la Ú no es U), y se
        // tomaba como una adición o una multa que sí hubo.
        String v = java.text.Normalizer.normalize(valor.strip(), java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toUpperCase(ES)
                .replace(".", "");
        return v.equals("NO") || v.equals("N/A") || v.equals("NA") || v.startsWith("NO APLICA")
                || v.startsWith("NINGUN") || v.startsWith("NO HUBO") || v.startsWith("NO SE PRESENTARON");
    }

    /**
     * La respuesta SI, NO o PARCIALMENTE con que empieza lo que escribió el
     * supervisor, como palabra completa («Sí, a satisfacción», «NO CUMPLIÓ»,
     * «Parcial»); {@code null} si no empieza por una de ellas. Hasta la
     * auditoría del 02-10-2026 se miraba con startsWith: «Sin cumplir» o «Sin
     * entregar» se tomaban por un SI y el Informe Final certificaba el
     * cumplimiento a satisfacción.
     */
    static String respuesta(String valor) {
        if (valor == null) {
            return null;
        }
        String v = comparable(valor);
        for (String r : List.of("PARCIALMENTE", "PARCIAL", "SI", "NO")) {
            if (empiezaPorPalabra(v, r)) {
                return r.startsWith("PARCIAL") ? "PARCIALMENTE" : r;
            }
        }
        return null;
    }

    /** Sin tildes y en mayúsculas, para comparar respuestas: «Sí» es «SI». */
    private static String comparable(String valor) {
        return java.text.Normalizer.normalize(valor.strip(), java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toUpperCase(ES);
    }

    /** Si el texto empieza por esa palabra (o frase) completa, no por otra más larga que la contiene. */
    private static boolean empiezaPorPalabra(String texto, String palabra) {
        return texto.startsWith(palabra)
                && (texto.length() == palabra.length() || !Character.isLetter(texto.charAt(palabra.length())));
    }

    /** Solo la respuesta y nada más: «SI», «Sí.», «no». */
    private static boolean esSoloLaRespuesta(String valor, String respuesta) {
        return comparable(valor).replaceAll("[.\\s]+$", "").equals(respuesta);
    }

    /**
     * El 2.5 del Informe Final: una certificación del supervisor sobre los
     * pagos de seguridad social, con su propio dato. Hasta la auditoría del
     * 02-10-2026 se escribía con solo responder SI al cumplimiento del objeto,
     * aunque nadie hubiera certificado los pagos. La frase del informe real
     * («cumplió a cabalidad con el objeto y las obligaciones contractuales»)
     * solo va si el supervisor declaró las dos cosas; {@link #incoherencia}
     * impide además que la tabla de obligaciones diga lo contrario. Cualquier
     * otra respuesta va tal como la escribió.
     */
    private static String pagosDeSeguridadSocial(Datos d, String cumplimiento) {
        if (!d.tiene("pagosSeguridadSocial")) {
            return "[dato pendiente: certificación de los pagos de seguridad social del contratista durante la"
                    + " ejecución]";
        }
        String dado = d.valor("pagosSeguridadSocial");
        String informes = "Mediante los informes presentados por la supervisión durante la ejecución del contrato, los"
                + " cuales fueron entregados para el proceso de pago, se evidenció que el contratista";
        if (esSoloLaRespuesta(dado, "SI")) {
            return informes + ("SI".equals(cumplimiento)
                    ? " cumplió a cabalidad con el objeto y las obligaciones contractuales."
                    : " cumplió con los pagos de seguridad social.");
        }
        if (esSoloLaRespuesta(dado, "NO")) {
            return informes + " no cumplió con los pagos de seguridad social.";
        }
        return dado;
    }

    private static String noNegativo(BigDecimal v, String siNegativo) {
        return v.signum() < 0 ? "[dato pendiente: " + siNegativo + "]" : pesos(v, false);
    }

    /** Celda de la tabla de imputación: todo centrado y en regular, como en el certificado real. */
    private static Celda plana(String texto) {
        return new Celda(List.of(Tramo.normal(texto)), 1, 1, Alineacion.CENTRO, false, true);
    }

    // ── GCCON-F-030 Informe Final de Supervisión ────────────────────────────

    private static List<BloqueDocumento> informeFinal(Contrato c, LocalDate hoy, String obs, Datos d) {
        List<BloqueDocumento> b = new ArrayList<>();
        String numero = numeroConAno(c, d);
        b.add(new LineasEnBlanco(1));
        b.add(new Titulo(List.of(Tramo.negrita("INFORME FINAL DE SUPERVISIÓN"), Tramo.normal(numero))));
        b.add(new Parrafo("En mi calidad de supervisor del contrato de la referencia, me permito presentar el informe"
                + " final del mismo, de acuerdo con la siguiente información:"));
        b.add(new LineasEnBlanco(1));
        seccion(b, "1.", "ASPECTOS GENERALES");
        b.add(new Letra(11f, 1f));
        BigDecimal valorFinal = d.pesos("valorFinal");
        BigDecimal pagado = d.pesos("valorTotalPagado");
        BigDecimal ejecutado = d.pesos("valorTotalEjecutado");
        b.add(new Ficha(List.of(
                new Campo("CONTRATANTE:", CONTRATANTE),
                new Campo("TIPO DE CONTRATO", o(c.getTipoContrato())),
                new Campo("CONTRATO NRO.", numero),
                new Campo("OBJETO", o(c.getObjeto()), Estilo.ITALICA),
                new Campo("FECHA DE SUSCRIPCIÓN DEL NEGOCIO JURÍDICO", d.fecha("fechaSuscripcion", CORTA)),
                new Campo("FECHA DE INICIO", fecha(c.getFechaInicio(), CORTA)),
                new Campo("PLAZO INICIAL", d.o("plazo", plazo(c, true))),
                new Campo("FECHA DE TERMINACIÓN INICIAL", fecha(c.getFechaFin(), CORTA)),
                new Campo("RAZÓN SOCIAL", o(c.getContratista())),
                new Campo("CC o NIT", o(c.getContratistaNit())),
                new Campo("NOMBRE DEL REPRESENTANTE LEGAL", mayusculas(c.getRepresentanteLegal())),
                new Campo("NÚMERO DE IDENTIFICACIÓN DEL REPRESENTANTE LEGAL", d.pendienteSiFalta("cedulaRepresentante")),
                new Campo("LUGAR DE EJECUCIÓN", mayusculas(c.getLugarEjecucion())),
                new Campo("VALOR INICIAL", pesos(c.getValor(), false)),
                new Campo("FORMA DE PAGO", d.pendienteSiFalta("formaDePago")),
                new Campo("CERTIFICADO DE DISPONIBILIDAD PRESUPUESTAL", d.tiene("numeroCdp") || d.tiene("fechaCdp")
                        ? d.pendienteSiFalta("numeroCdp") + " DE FECHA " + d.fecha("fechaCdp", ISO)
                        : "[dato pendiente]"),
                new Campo("CERTIFICADO DE REGISTRO PRESUPUESTAL", registroPresupuestal(c, ISO, " DE FECHA ")),
                new Campo("VALOR FINAL DEL NEGOCIO JURÍDICO", d.pesosTexto("valorFinal")),
                new Campo("FECHA DE TERMINACIÓN FINAL", d.fecha("fechaTerminacionFinal", CORTA)),
                new Campo("FECHA DE TERMINACIÓN ANTICIPADA (Sí aplica)", d.tiene("terminacionAnticipada")
                        ? d.fecha("terminacionAnticipada", CORTA) : "[dato pendiente: fecha, o No aplica]"),
                new Campo("VALOR TOTAL PAGADO", d.pesosTexto("valorTotalPagado")),
                new Campo("VALOR TOTAL EJECUTADO", d.pesosTexto("valorTotalEjecutado")),
                new Campo("SUPERVISOR", supervisor(c)),
                new Campo("APOYO A LA SUPERVISIÓN", d.o("apoyoSupervision", "[dato pendiente: nombre, o No aplica]"))),
                0.361f, 5.4f, true));
        b.add(new Letra(11f));
        b.add(new LineasEnBlanco(2));
        seccion(b, "2.", "ASPECTOS TÉCNICOS");
        seccion(b, "2.1", "Obligaciones");
        b.add(new Parrafo(Alineacion.JUSTIFICADO, Tramo.normal("En virtud de la suscripción del contrato "),
                Tramo.negrita(numero), Tramo.normal(", el contratista adquirió las siguientes obligaciones:")));
        b.add(new LineasEnBlanco(1));
        List<Fila> generales = new ArrayList<>();
        generales.add(Fila.de(Celda.encabezado("OBLIGACIONES"),
                new Celda(List.of(Tramo.negrita("¿CUMPLIÓ?\n"), Tramo.normal("[Seleccione: "),
                        Tramo.negrita("SI / NO / Parcialmente / No se requirió el cumplimiento]")),
                        1, 1, Alineacion.CENTRO, true, true),
                Celda.encabezado("PRODUCTO O EVIDENCIA")));
        List<List<String>> generalesDadas = d.filas("obligacionesGenerales");
        if (generalesDadas.isEmpty()) {
            generales.add(Fila.de(new Celda(List.of(Tramo.normal("1. [dato pendiente: obligaciones del contrato]")), 1, 1,
                            Alineacion.IZQUIERDA, false, true),
                    Celda.de("[dato pendiente: SI CUMPLIO / NO / PARCIALMENTE]").alineada(Alineacion.JUSTIFICADO),
                    Celda.de("[dato pendiente: producto o evidencia]").alineada(Alineacion.JUSTIFICADO)));
        }
        for (int i = 0; i < generalesDadas.size(); i++) {
            List<String> fila = generalesDadas.get(i);
            generales.add(Fila.de(new Celda(List.of(Tramo.normal((i + 1) + ". " + obligacion(fila))), 1, 1,
                            Alineacion.IZQUIERDA, false, true),
                    new Celda(veredicto(celda(fila, 1)), 1, 1, Alineacion.JUSTIFICADO, false, true),
                    Celda.de(celda(fila, 2)).alineada(Alineacion.JUSTIFICADO)));
        }
        b.add(new Tabla(List.of(148.7f, 127.6f, 193.5f), generales, 1, false, 5.4f));
        // Las específicas van a continuación, con su propio encabezado, como en
        // el Informe Final real: son las del Informe de Supervisión.
        List<Fila> especificas = new ArrayList<>();
        especificas.add(Fila.de(Celda.encabezado("OBLIGACIONES ESPECIFICAS DEL CONTRATISTA."),
                Celda.encabezado("ACTIVIDADES REALIZADAS"), Celda.encabezado("PRODUCTO O EVIDENCIA")));
        List<List<String>> dadas = d.filas("obligacionesEspecificas");
        if (dadas.isEmpty()) {
            especificas.add(Fila.de(Celda.de("1. [dato pendiente: obligaciones específicas del contrato]"),
                    Celda.de("[dato pendiente: actividades realizadas]").alineada(Alineacion.JUSTIFICADO),
                    Celda.de("[dato pendiente: producto o evidencia]").alineada(Alineacion.JUSTIFICADO)));
        }
        for (int i = 0; i < dadas.size(); i++) {
            List<String> fila = dadas.get(i);
            especificas.add(Fila.de(Celda.de((i + 1) + ". " + obligacion(fila)),
                    Celda.de(celda(fila, 1)).alineada(Alineacion.JUSTIFICADO),
                    Celda.de(celda(fila, 2)).alineada(Alineacion.JUSTIFICADO)));
        }
        b.add(new Tabla(List.of(148.7f, 127.6f, 193.5f), especificas, 1, false, 5.4f));
        b.add(new LineasEnBlanco(1));
        seccion(b, "2.2", "Cumplimiento del objeto");
        // Las conclusiones del informe (cumplimiento, multas, mantenimiento) no
        // son texto fijo: el modelo del formato es un informe ya diligenciado,
        // y esas frases eran las conclusiones de ESE contrato. Se escriben solo
        // si el supervisor las declara; si no, quedan pendientes (revisión del
        // 28-09-2026).
        String cumplimiento = respuesta(d.valor("cumplimientoObjeto"));
        boolean sinMultas = d.tiene("multas") && esNo(d.valor("multas"));
        List<Tramo> objeto = new ArrayList<>(List.of(Tramo.normal("En calidad de supervisor del contrato "),
                Tramo.negrita(numero)));
        if ("SI".equals(cumplimiento)) {
            objeto.add(Tramo.normal(" se dio cumplimiento a satisfacción de los bienes y/o servicios requeridos en el"
                    + " contrato con el objeto: "));
            objeto.add(Tramo.italica(o(c.getObjeto()) + "." + (sinMultas ? " En el proceso contractual no se generaron"
                    + " incumplimientos, procesos de multas u otras sanciones." : "")));
        } else if ("PARCIALMENTE".equals(cumplimiento)) {
            objeto.add(Tramo.normal(" certifico que el objeto del contrato, "));
            objeto.add(Tramo.italica(o(c.getObjeto())));
            objeto.add(Tramo.normal(", se cumplió parcialmente, según se detalla en este informe."));
        } else if ("NO".equals(cumplimiento)) {
            objeto.add(Tramo.normal(" certifico que el objeto del contrato, "));
            objeto.add(Tramo.italica(o(c.getObjeto())));
            objeto.add(Tramo.normal(", no se cumplió, según se detalla en este informe."));
        } else {
            objeto.add(Tramo.normal(", respecto del objeto: "));
            objeto.add(Tramo.italica(o(c.getObjeto())));
            objeto.add(Tramo.normal(", [dato pendiente: declaración sobre el cumplimiento del objeto (SI / NO /"
                    + " PARCIALMENTE)]."));
        }
        b.add(new Parrafo(objeto, Alineacion.JUSTIFICADO));
        if (obs != null) {
            b.add(new LineasEnBlanco(1));
            b.add(new Parrafo(obs));
        }
        b.add(new LineasEnBlanco(1));
        seccion(b, "2.3", "Cumplimiento de los aspectos del Sistema Integrado de Gestión y Autocontrol – SIGA");
        List<Fila> siga = new ArrayList<>();
        siga.add(Fila.de(Celda.de("Obligaciones Seguridad y Salud en el Trabajo del Contratista.").enNegrita()
                .abarcando(3, 1)));
        siga.addAll(filasSiga(d.filas("sigaSst")));
        siga.add(Fila.de(Celda.de("Obligaciones ambientales del contratista.").enNegrita().abarcando(3, 1)));
        siga.addAll(filasSiga(d.filas("sigaAmbiental")));
        b.add(new Tabla(List.of(162.9f, 85.0f, 221.9f), siga, 0, false, 5.4f));
        b.add(new LineasEnBlanco(1));
        seccion(b, "2.4", "Multas y sanciones");
        if (sinMultas) {
            b.add(new Parrafo(Alineacion.JUSTIFICADO, Tramo.normal("De conformidad con la ejecución del contrato "),
                    Tramo.negrita("NO"), Tramo.normal(" se presentaron multas y/o sanciones.")));
        } else if (d.tiene("multas")) {
            b.add(new Parrafo("De conformidad con la ejecución del contrato se presentaron las siguientes multas y/o"
                    + " sanciones: " + d.valor("multas")));
        } else {
            b.add(new Parrafo("[dato pendiente: multas o sanciones durante la ejecución, o NO si no las hubo]"));
        }
        b.add(new LineasEnBlanco(1));
        seccion(b, "2.5", "Certificado de pagos de seguridad social");
        b.add(new Parrafo(pagosDeSeguridadSocial(d, cumplimiento)));
        b.add(new LineasEnBlanco(1));
        seccion(b, "2.6", "Designación de la supervisión");
        b.add(new Parrafo("Que el ordenador del gasto realizó la designación de supervisión el "
                + d.fecha("fechaDesignacion", DateTimeFormatter.ofPattern("d 'de' MMMM 'del' yyyy", ES))
                + " mediante comunicación enviada por correo electrónico."));
        b.add(new LineasEnBlanco(1));
        seccion(b, "2.7", "Liquidación del negocio jurídico");
        b.add(new Parrafo("Que respecto de la liquidación del contrato se estableció: La liquidación del contrato se"
                + " efectuará de mutuo acuerdo dentro del término establecido en la configuración del contrato"
                + " electrónico publicado en la Plataforma SECOP II. En caso de que no se haya establecido dicho"
                + " término se dará aplicación a plazos previstos en el artículo 11 de la Ley 1150 de 2007."));
        b.add(new LineasEnBlanco(2));
        seccion(b, "3.", "ASPECTOS LEGALES");
        seccion(b, "3.1", "Garantías contractuales");
        b.add(new Parrafo(Alineacion.IZQUIERDA, Tramo.normal("Como garantías se establecieron las siguientes:")));
        b.add(new LineasEnBlanco(1));
        b.add(garantia(d, List.of("Cumplimiento - Cumplimiento del contrato", "Pagos de salarios y prestaciones sociales",
                "Calidad de los bienes solicitados")));
        b.add(new LineasEnBlanco(2));
        seccion(b, "4.", "OBLIGACIONES DE LA ENTIDAD");
        String[] obligaciones = {
                "Exigir al contratista la ejecución idónea y oportuna de las obligaciones del presente contrato.",
                "Rechazar los bienes y/o servicios cuando estos no cumplan con los requerimientos técnicos exigidos.",
                "Pagar la contraprestación a la que tiene derecho el contratista, con ocasión de la correcta ejecución"
                        + " del negocio jurídico suscrito.",
                "Suministrar la información que previamente requiera el contratista en relación con el objeto del"
                        + " presente contrato.",
                "Suscribir juntamente con el contratista y/o la Interventoría las actas y los demás documentos"
                        + " necesarios para la ejecución y liquidación de este contrato.",
                "Adelantar las gestiones necesarias para el reconocimiento y cobro de las sanciones pecuniarias y"
                        + " garantías a que hubiere lugar. Para tal efecto, el Supervisor dará aviso oportuno al"
                        + " ordenador del gasto o a su delegado, sobre la ocurrencia de hecho constitutivo"
                        + " incumplimiento o mora de las prestaciones contratadas.",
                "Informar al proveedor la forma como se deben presentar las facturas o documento equivalente.",
                "Las demás que se estimen de acuerdo con la naturaleza de la contratación."};
        for (int i = 0; i < obligaciones.length; i++) {
            b.add(new Parrafo(Alineacion.JUSTIFICADO, Tramo.negrita((i + 1) + ")"), Tramo.normal(" " + obligaciones[i])));
        }
        b.add(new LineasEnBlanco(1));
        String mantenimiento = !d.tiene("mantenimiento") ? "[dato pendiente: requieren o no requieren]"
                : esNo(d.valor("mantenimiento")) ? "no requieren" : "requieren";
        b.add(new Parrafo("En atención a lo preceptuado en el numeral 4 del artículo 4 de la Ley 80 de 1993 y de"
                + " conformidad con lo establecido en el negocio jurídico me permito informar al Ordenador del gasto"
                + " que los bienes recibidos " + mantenimiento + " revisiones o mantenimientos periódicos."));
        b.add(new LineasEnBlanco(2));
        seccion(b, "5.", "ASPECTOS FINANCIEROS.");
        seccion(b, "5.1", "Pagos realizados");
        b.add(new Parrafo(Alineacion.JUSTIFICADO,
                Tramo.normal("El " + d.fecha("fechaCertificadoPagos", CORTA) + " se expidió el certificado de desembolsos"
                        + " Relación de pago de SIIF del Contrato nro. " + numero + ", cuyo valor total pagado es de "),
                Tramo.negrita(d.pesosTexto("valorTotalPagado")), Tramo.normal(".")));
        b.add(new LineasEnBlanco(1));
        List<Fila> pagos = new ArrayList<>();
        pagos.add(Fila.de(Celda.encabezado("NÚMERO DE ORDEN DE PAGO"), Celda.encabezado("FECHA DE PAGO"),
                Celda.encabezado("VALOR DE PAGO")));
        List<List<String>> ordenes = d.filas("ordenesDePago");
        if (ordenes.isEmpty()) {
            pagos.add(Fila.de(Celda.de("[dato pendiente]").alineada(Alineacion.CENTRO),
                    Celda.de("[dato pendiente]").alineada(Alineacion.CENTRO),
                    Celda.de("[dato pendiente]").alineada(Alineacion.CENTRO)));
        }
        for (List<String> fila : ordenes) {
            pagos.add(Fila.de(Celda.de(celda(fila, 0)).alineada(Alineacion.CENTRO),
                    Celda.de(fechaDeCelda(celda(fila, 1))).alineada(Alineacion.CENTRO),
                    Celda.de(pesosDeCelda(celda(fila, 2), false)).alineada(Alineacion.CENTRO)));
        }
        b.add(new Tabla(List.of(161.8f, 107.8f, 113.4f), pagos, 1, true, 5.4f));
        b.add(new LineasEnBlanco(1));
        seccion(b, "5.2", "Estado financiero");
        String adiciones = "[dato pendiente]";
        String reducciones = "[dato pendiente]";
        if (valorFinal != null && c.getValor() != null) {
            BigDecimal diferencia = valorFinal.subtract(c.getValor());
            adiciones = pesos(diferencia.max(BigDecimal.ZERO), false);
            reducciones = pesos(diferencia.min(BigDecimal.ZERO).negate(), false);
        }
        String porPagar = ejecutado != null && pagado != null
                ? noNegativo(ejecutado.subtract(pagado), "el pagado supera el ejecutado") : "[dato pendiente]";
        String aLiberar = valorFinal != null && ejecutado != null
                ? noNegativo(valorFinal.subtract(ejecutado), "el ejecutado supera el valor final") : "[dato pendiente]";
        String[][] estado = {
                {"Valor inicial del negocio jurídico", pesos(c.getValor(), false)},
                {"Adiciones o disminuciones del negocio jurídico", adiciones},
                {"Valor de las reducciones", reducciones},
                {"Valor final del negocio jurídico", d.pesosTexto("valorFinal")},
                {"Valor ejecutado", d.pesosTexto("valorTotalEjecutado")},
                {"Valor pagado", d.pesosTexto("valorTotalPagado")},
                {"Valor por pagar", porPagar},
                {"Valor a liberar", aLiberar}};
        List<Fila> filasEstado = new ArrayList<>();
        filasEstado.add(Fila.de(Celda.encabezado("CONCEPTO"), Celda.encabezado("VALOR")));
        for (String[] fila : estado) {
            // En una columna de cifras el marcador va corto: el largo partía la celda en cinco renglones.
            String valor = fila[1].startsWith("[dato") ? "[dato pendiente]" : fila[1];
            filasEstado.add(Fila.de(Celda.de(fila[0]).enNegrita(),
                    Celda.de(valor).enNegrita().alineada(Alineacion.DERECHA)));
        }
        // Mismo ancho total que el formato (318,8 pt); la columna VALOR va un
        // poco más ancha para que «$20.000.000,00» en negrita quepa en un renglón.
        b.add(new Tabla(List.of(213.0f, 105.8f), filasEstado, 1, true, 4f));
        b.add(new LineasEnBlanco(3));
        b.add(new BloqueDocumento.MantenerJunto(115f));
        b.add(new Parrafo(Alineacion.IZQUIERDA, Tramo.normal("Para constancia se firma " + hoy.format(CORTA) + ".")));
        b.add(new Firmas(List.of(new Firmante(supervisor(c), true, List.of("Supervisor del contrato"))),
                DisposicionFirmas.IZQUIERDA, 60f));
        return b;
    }

    // ── Piezas comunes ──────────────────────────────────────────────────────

    /**
     * Cuántos «[dato pendiente…]» quedaron en el documento. Se cuentan en lo
     * compuesto y no en los datos que faltaron: un dato opcional (el correo
     * del contratista) o deducible (el plazo) no deja nada pendiente.
     */
    public static int contarPendientes(List<BloqueDocumento> bloques) {
        StringBuilder t = new StringBuilder();
        for (BloqueDocumento b : bloques) {
            switch (b) {
                case Parrafo p -> t.append(p.texto());
                case Titulo ti -> ti.lineas().forEach(l -> t.append(l.texto()));
                case Ficha f -> f.campos().forEach(c -> t.append(c.valor() == null ? "[dato pendiente]" : c.valor()));
                case Tabla ta -> ta.filas().forEach(fi -> fi.celdas().forEach(ce -> ce.tramos()
                        .forEach(tr -> t.append(tr.texto()))));
                case HojaDeRecibo h -> t.append(h.datos().toString());
                default -> {
                }
            }
            t.append('\n');
        }
        return t.toString().split(java.util.regex.Pattern.quote("[dato pendiente"), -1).length - 1;
    }

    /**
     * Lo que haría inválido el documento aunque cada dato esté bien escrito,
     * para rechazarlo antes de generarlo; {@code null} si no hay nada.
     *
     * <p>Dos reglas, las dos del Informe Final. La primera: si el supervisor
     * declara cumplido el objeto a satisfacción, ninguna obligación general
     * puede decir NO o PARCIALMENTE en «¿CUMPLIÓ?». Firmado así, el 2.2 y el
     * 2.5 afirmaban lo que la tabla del mismo documento negaba (auditoría del
     * 02-10-2026).
     *
     * <p>La segunda: las órdenes de pago tienen que sumar el valor total
     * pagado. En el real suman exacto (16.798.000 + 3.191.620 = 19.989.620);
     * un informe firmado con otra suma es un documento que se contradice en la
     * misma página, y la tabla de estado financiero calcula el valor por pagar
     * con el total. Solo se compara cuando todas las órdenes y el total se
     * leen como cifras: si alguna no, se escribe tal cual y la suma queda para
     * quien revisa el documento.
     */
    public static String incoherencia(PlantillaDocumentoIA plantilla, Map<String, String> datos,
                                      Map<String, List<List<String>>> tablas) {
        if (!"INFORME_FINAL".equals(plantilla.clave()) || datos == null || tablas == null) {
            return null;
        }
        if ("SI".equals(respuesta(datos.get("cumplimientoObjeto")))) {
            // Numeradas como en el documento, que no cuenta las filas vacías.
            List<List<String>> generales = sinFilasVacias(tablas.get("obligacionesGenerales"));
            for (int i = 0; i < generales.size(); i++) {
                List<String> fila = generales.get(i);
                String cumplio = veredictoDe(fila.size() > 1 && fila.get(1) != null ? fila.get(1).strip() : "");
                if ("NO CUMPLIO".equals(cumplio) || "NO".equals(cumplio) || "PARCIALMENTE".equals(cumplio)) {
                    return "Declaró que el contratista cumplió el objeto a satisfacción, pero en la obligación "
                            + (i + 1) + " respondió «" + cumplio + "». Corrija una de las dos cosas antes de generar"
                            + " el Informe Final: firmado así, el documento se contradice.";
                }
            }
        }
        List<List<String>> ordenes = tablas.getOrDefault("ordenesDePago", List.of());
        BigDecimal total = leerPesos(datos.get("valorTotalPagado"));
        if (ordenes.isEmpty() || total == null) {
            return null;
        }
        BigDecimal suma = BigDecimal.ZERO;
        for (List<String> orden : ordenes) {
            BigDecimal valor = orden.size() > 2 ? leerPesos(orden.get(2)) : null;
            if (valor == null) {
                return null;
            }
            suma = suma.add(valor);
        }
        if (suma.compareTo(total) != 0) {
            return "Las órdenes de pago suman " + pesos(suma, false) + " y el valor total pagado es "
                    + pesos(total, false) + ". Corrija una de las dos cosas antes de generar el Informe Final:"
                    + " firmado así, el documento se contradice.";
        }
        return null;
    }

    /** «1. », «2) », «3 - » al principio de una obligación copiada del contrato con su número. */
    private static final Pattern NUMERO_INICIAL = Pattern.compile("^\\s*\\d{1,3}\\s*(?:\\.(?=\\s*\\p{L})|\\)|-|–)\\s*");

    /**
     * El texto de la obligación sin el número con que viene en el contrato:
     * la tabla ya numera sus filas, y pegarla tal cual daba «1. 1. Proveer…»
     * o, si se pegaba desde la tercera, «1. 3. Entregar…» (revisión del
     * 01-10-2026). «1.5 toneladas» no es un número de lista y se conserva.
     */
    private static String obligacion(List<String> fila) {
        String texto = celda(fila, 0);
        String sinNumero = NUMERO_INICIAL.matcher(texto).replaceFirst("");
        return sinNumero.isBlank() ? texto : sinNumero;
    }

    /** Las respuestas de la columna «¿CUMPLIÓ?», de la más larga a la más corta. */
    private static final List<String> VEREDICTOS = List.of("NO SE REQUIRIO EL CUMPLIMIENTO", "SI CUMPLIO",
            "NO CUMPLIO", "PARCIALMENTE", "SI", "NO");

    /**
     * La celda «¿CUMPLIÓ?» como en el Informe Final real: la respuesta en
     * mayúsculas y negrita («SI CUMPLIO») y lo que el supervisor escriba
     * después, tal cual. Hasta el 01-10-2026 la celda entera pasaba a
     * mayúsculas: una explicación o un enlace de SECOP II salían cambiados.
     */
    private static List<Tramo> veredicto(String texto) {
        String v = texto.startsWith("[dato") ? null : veredictoDe(texto);
        if (v == null) {
            return List.of(Tramo.normal(texto));
        }
        String resto = texto.substring(v.length());
        return resto.isBlank() ? List.of(Tramo.negrita(texto.substring(0, v.length()).toUpperCase(ES)))
                : List.of(Tramo.negrita(texto.substring(0, v.length()).toUpperCase(ES)), Tramo.normal(resto));
    }

    /** La respuesta de {@link #VEREDICTOS} con que empieza la celda, como palabra completa, o {@code null}. */
    private static String veredictoDe(String texto) {
        String comparable = java.text.Normalizer.normalize(texto, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toUpperCase(ES);
        return VEREDICTOS.stream().filter(v -> empiezaPorPalabra(comparable, v)).findFirst().orElse(null);
    }

    /**
     * Solo una respuesta negativa y nada más: «No», «N/A», «No aplica.». Un
     * párrafo que empieza así y sigue («No se presentaron incidentes; el
     * contratista entregó la planilla…») no lo es: con {@link #esNo}, el
     * cumplimiento del SIGA que escribió el supervisor se cambiaba por «No
     * aplica.», que en el formato quiere decir que el contrato no tiene esas
     * obligaciones (revisión del 01-10-2026).
     */
    static boolean esSoloNo(String valor) {
        if (valor == null) {
            return false;
        }
        String v = java.text.Normalizer.normalize(valor.strip(), java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toUpperCase(ES).replaceAll("[.\\s]+$", "").replace(".", "");
        return v.equals("NO") || v.equals("N/A") || v.equals("NA") || v.equals("NO APLICA");
    }

    /** La celda de una fila dada, o «[dato pendiente]» si quedó vacía. */
    private static String celda(List<String> fila, int columna) {
        String v = columna < fila.size() ? fila.get(columna) : null;
        return v == null || v.isBlank() ? "[dato pendiente]" : v;
    }

    /** Una fecha escrita en una celda, como la escriben las tablas del formato; si no se entiende, tal cual. */
    private static String fechaDeCelda(String texto) {
        LocalDate f = texto.startsWith("[dato") ? null : leerFecha(texto);
        return f != null ? f.format(CORTA) : texto;
    }

    /** Un valor escrito en una celda con el formato de pesos; si no se entiende como cifra, tal cual. */
    private static String pesosDeCelda(String texto, boolean conEspacio) {
        BigDecimal v = leerPesos(texto);
        return v != null ? pesos(v, conEspacio) : texto;
    }

    /** Filas «1. obligación | cumplimiento | evidencia» de una parte del SIGA del Informe Final. */
    private static List<Fila> filasSiga(List<List<String>> dadas) {
        if (dadas.isEmpty()) {
            return List.of(Fila.de(Celda.de("[dato pendiente: obligación]"), Celda.de("[dato pendiente]"),
                    Celda.de("[dato pendiente: evidencia]")));
        }
        List<Fila> filas = new ArrayList<>();
        for (int i = 0; i < dadas.size(); i++) {
            List<String> fila = dadas.get(i);
            filas.add(Fila.de(Celda.de((i + 1) + ". " + obligacion(fila)), Celda.de(celda(fila, 1)),
                    Celda.de(celda(fila, 2))));
        }
        return filas;
    }

    /** Las filas con algo escrito: una fila vacía no sale en el documento ni se numera. */
    private static List<List<String>> sinFilasVacias(List<List<String>> filas) {
        if (filas == null) {
            return List.of();
        }
        return filas.stream()
                .filter(f -> f != null && f.stream().anyMatch(v -> v != null && !v.isBlank()))
                .toList();
    }

    /** Título de apartado con la línea en blanco que lo sigue en los formatos. */
    private static void seccion(List<BloqueDocumento> b, String numero, String titulo) {
        b.add(new Seccion(numero, titulo));
        b.add(new LineasEnBlanco(1));
    }

    /**
     * Los datos complementarios de un documento. Solo se aceptan las claves que
     * la plantilla declara: una clave desconocida no llega al documento.
     */
    private static final class Datos {
        private final PlantillaDocumentoIA plantilla;
        private final Map<String, String> valores;
        private final Map<String, List<List<String>>> tablas;

        Datos(PlantillaDocumentoIA plantilla, Map<String, String> valores, Map<String, List<List<String>>> tablas) {
            this.plantilla = plantilla;
            this.valores = valores;
            this.tablas = tablas;
        }

        /**
         * Las filas que dio el supervisor para una tabla que el formato
         * declara, sin las vacías; una tabla que el formato no declara no llega.
         */
        List<List<String>> filas(String clave) {
            boolean declarada = plantilla.tablas().stream().anyMatch(t -> t.clave().equals(clave));
            return sinFilasVacias(declarada ? tablas.get(clave) : null);
        }

        boolean tiene(String clave) {
            String v = valores.get(clave);
            return v != null && !v.isBlank();
        }

        String valor(String clave) {
            return tiene(clave) ? valores.get(clave).strip() : null;
        }

        /** El dato, o lo que se diga si falta. */
        String o(String clave, String siFalta) {
            return tiene(clave) ? valor(clave) : siFalta;
        }

        /** El dato en mayúsculas, como lo escriben los formatos; el marcador de pendiente no se toca. */
        String mayusculas(String clave, String siFalta) {
            return tiene(clave) ? valor(clave).toUpperCase(ES) : siFalta;
        }

        /** El dato, o «[dato pendiente: etiqueta]». */
        String pendienteSiFalta(String clave) {
            return tiene(clave) ? valor(clave) : pendiente(clave);
        }

        String pendiente(String clave) {
            String etiqueta = plantilla.campos().stream().filter(cd -> cd.clave().equals(clave))
                    .map(PlantillaDocumentoIA.CampoDelDocumento::etiqueta).findFirst().orElse(clave);
            return "[dato pendiente: " + etiqueta.substring(0, 1).toLowerCase(ES) + etiqueta.substring(1) + "]";
        }

        /** Una fecha que dio el supervisor, escrita como la pide el formato; si no se entiende, tal cual. */
        String fecha(String clave, DateTimeFormatter formato) {
            if (!tiene(clave)) {
                return pendiente(clave);
            }
            LocalDate f = fechaComoFecha(clave);
            return f != null ? f.format(formato) : valor(clave);
        }

        LocalDate fechaComoFecha(String clave) {
            return leerFecha(valor(clave));
        }

        BigDecimal pesos(String clave) {
            return leerPesos(valor(clave));
        }

        String pesosTexto(String clave) {
            return pesosTexto(clave, false);
        }

        /** Un valor en pesos escrito como el formato; si no se entiende como cifra, tal cual lo escribió. */
        String pesosTexto(String clave, boolean conEspacio) {
            if (!tiene(clave)) {
                return pendiente(clave);
            }
            BigDecimal v = pesos(clave);
            return v != null ? RedactorDeDocumentos.pesos(v, conEspacio) : valor(clave);
        }
    }

    /** «17/06/2025», «17-06-2025», «2025-06-17» o «17 de junio de 2025». */
    static LocalDate leerFecha(String texto) {
        if (texto == null || texto.isBlank()) {
            return null;
        }
        String t = texto.strip();
        // Estricto: con el modo por defecto, «31/02/2025» se entendía como el
        // 28 de febrero y el documento firmado decía una fecha que nadie
        // escribió. Una fecha que no existe se deja tal cual la escribieron.
        for (String patron : List.of("d/M/uuuu", "d-M-uuuu", "uuuu-M-d", "d 'de' MMMM 'de' uuuu", "d 'de' MMMM 'del' uuuu")) {
            try {
                return LocalDate.parse(t, DateTimeFormatter.ofPattern(patron, ES).withResolverStyle(ResolverStyle.STRICT));
            } catch (DateTimeParseException e) {
                // Siguiente patrón.
            }
        }
        return null;
    }

    /**
     * «$39.400.634,00», «39400634», «$ 4.000.000» → la cifra; cualquier otra
     * cosa → null, y el valor sale tal cual lo escribió el supervisor sin
     * calcular nada con él. Solo el formato colombiano: antes «19989620.00»
     * (copiado de Excel, con punto decimal) se leía como 1.998.962.000 y así
     * iba al certificado de pago firmado (revisión del 28-09-2026).
     */
    static BigDecimal leerPesos(String texto) {
        if (texto == null || texto.isBlank()) {
            return null;
        }
        String t = texto.replace("$", "").replace("COP", "").replace(" ", "").replace("\u00A0", "").strip();
        if (!t.matches("\\d{1,3}(\\.\\d{3})+(,\\d{1,2})?|\\d+(,\\d{1,2})?")) {
            return null;
        }
        int coma = t.lastIndexOf(',');
        String entero = (coma >= 0 ? t.substring(0, coma) : t).replace(".", "");
        String decimales = coma >= 0 ? t.substring(coma + 1) : "";
        return new BigDecimal(entero + (decimales.isEmpty() ? "" : "." + decimales));
    }

    /** «$20.000.000,00», siempre con centavos; con espacio tras el signo en el certificado («$ 99.067.527,00»). */
    static String pesos(BigDecimal v, boolean conEspacio) {
        if (v == null) {
            return "[dato pendiente]";
        }
        return "$" + (conEspacio ? " " : "") + formato("#,##0.00").format(v.setScale(2, RoundingMode.HALF_UP));
    }

    /** «18.924.558»: sin signo ni decimales, como la GIL-F-010. */
    static String entero(BigDecimal v) {
        return formato("#,##0").format(v.setScale(0, RoundingMode.HALF_UP));
    }

    /** «DIEZ MILLONES DE PESOS ($10.000.000 COP)», como el Acta de Inicio. */
    static String valorEnLetrasYCifra(BigDecimal v) {
        if (v == null) {
            return "[dato pendiente]";
        }
        String letras = NumeroEnLetras.pesos(v);
        if (letras.endsWith(" M/CTE")) {
            letras = letras.substring(0, letras.length() - " M/CTE".length());
        }
        boolean conCentavos = v.stripTrailingZeros().scale() > 0;
        return letras + " ($" + formato(conCentavos ? "#,##0.00" : "#,##0").format(v) + " COP)";
    }

    private static DecimalFormat formato(String patron) {
        DecimalFormatSymbols simbolos = new DecimalFormatSymbols(ES);
        simbolos.setGroupingSeparator('.');
        simbolos.setDecimalSeparator(',');
        simbolos.setMinusSign('-');
        return new DecimalFormat(patron, simbolos);
    }

    private static String porcentaje(BigDecimal parte, BigDecimal total) {
        BigDecimal p = parte.multiply(BigDecimal.valueOf(100)).divide(total, 2, RoundingMode.HALF_UP);
        return formato("#,##0.00").format(p) + " %";
    }

    /**
     * El plazo, solo si se deduce sin ambigüedad de las fechas: un número
     * exacto de meses (contando el día final o no). Si no, se escriben las
     * fechas: un plazo pactado de «dos meses» no siempre coincide con la
     * diferencia de fechas, y SICOT no debe afirmar un plazo que no conoce.
     *
     * @param enLetras «Dos (02) meses» (GCCON-F-030/031) o «6 Meses» (GCCON-F-018).
     */
    static String plazo(Contrato c, boolean enLetras) {
        LocalDate inicio = c.getFechaInicio();
        LocalDate fin = c.getFechaFin();
        if (inicio == null || fin == null) {
            return "[dato pendiente]";
        }
        Period p = Period.between(inicio, fin);
        Period pInclusivo = Period.between(inicio, fin.plusDays(1));
        int meses = -1;
        if (p.getDays() == 0 && p.getYears() == 0 && p.getMonths() > 0) {
            meses = p.getMonths();
        } else if (pInclusivo.getDays() == 0 && pInclusivo.getYears() == 0 && pInclusivo.getMonths() > 0) {
            meses = pInclusivo.getMonths();
        }
        if (meses < 0) {
            return "Del " + inicio.format(CORTA) + " al " + fin.format(CORTA);
        }
        if (!enLetras) {
            return meses + (meses == 1 ? " Mes" : " Meses");
        }
        String letras = NumeroEnLetras.entero(meses).toLowerCase(ES);
        letras = letras.equals("uno") ? "un" : letras;
        return letras.substring(0, 1).toUpperCase(ES) + letras.substring(1) + " (%02d) ".formatted(meses)
                + (meses == 1 ? "mes" : "meses");
    }

    /**
     * «CO1.PCCNTR.8426076 del 2025», como lo escriben los informes del Centro:
     * el año de la suscripción si el supervisor la dio (un contrato de
     * vigencias futuras se suscribe un año y empieza el siguiente), y si no, el
     * del inicio.
     */
    static String numeroConAno(Contrato c, LocalDate suscripcion) {
        String numero = c.getNumeroContrato();
        LocalDate referencia = suscripcion != null ? suscripcion : c.getFechaInicio();
        if (numero == null || ANO.matcher(numero).find() || referencia == null) {
            return numero;
        }
        return numero + " del " + referencia.getYear();
    }

    private static String numeroConAno(Contrato c, Datos d) {
        return numeroConAno(c, d.tiene("fechaSuscripcion") ? d.fechaComoFecha("fechaSuscripcion") : null);
    }

    private static String registroPresupuestal(Contrato c, DateTimeFormatter formato, String union) {
        String numero = c.getNumeroRegistroPresupuestal();
        if (numero == null || numero.isBlank()) {
            return "[dato pendiente]";
        }
        return c.getFechaRegistroPresupuestal() != null
                ? numero + union + c.getFechaRegistroPresupuestal().format(formato)
                : numero;
    }

    /** «900.478.852-5» → «900478852»: la GIL-F-010 pide solo dígitos y sin el de verificación. */
    static String soloDigitosSinVerificacion(String nit) {
        String sinVerificacion = nit.strip().replaceFirst("-\\s*\\d\\s*$", "");
        String digitos = sinVerificacion.replaceAll("[^0-9]", "");
        return digitos.isEmpty() ? nit : digitos;
    }

    private static String supervisor(Contrato c) {
        return c.getSupervisor() != null ? c.getSupervisor().getNombre() : "[dato pendiente: supervisor sin asignar]";
    }

    private static String fecha(LocalDate f, DateTimeFormatter formato) {
        return f != null ? f.format(formato) : "[dato pendiente]";
    }

    /** En mayúsculas, que es como los formatos escriben nombres, razón social y lugar. */
    private static String mayusculas(String valor) {
        if (valor == null || valor.isBlank()) {
            return "[dato pendiente]";
        }
        return valor.startsWith("[dato") ? valor : valor.toUpperCase(ES);
    }

    /** Un dato opcional del contrato: el valor tal cual, o el marcador de pendiente. */
    private static String o(String valor) {
        return valor == null || valor.isBlank() ? "[dato pendiente]" : valor;
    }
}
