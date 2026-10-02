package co.sena.sicot.ia;

import co.sena.sicot.entity.Contrato;
import co.sena.sicot.entity.Usuario;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Los cinco formatos, armados con los datos del Acta de Inicio real del
 * contrato CO1.PCCNTR.7986334 (carpeta «Documentos/SICOT»). Se comprueba sobre
 * el texto del PDF final, que es lo que el supervisor firma, y contra cómo
 * escribe el Centro cada dato en sus formatos diligenciados.
 */
class RedactorDeDocumentosTest {

    private static final LocalDate HOY = LocalDate.of(2026, 9, 28);

    private static Contrato contrato() {
        Usuario sup = new Usuario();
        sup.setNombre("Laura Carolina Restrepo Toro");
        sup.setEmail("LCRestrepo@sena.edu.co");
        sup.setTelefono("3042713044");
        Contrato c = new Contrato();
        c.setNumeroContrato("CO1.PCCNTR.7986334");
        c.setTipoContrato("Suministro");
        c.setObjeto("5_9205_278 CONTRATAR EL SERVICIO DE ALQUILER DE TOLDOS CARPAS Y LUCES PARA LA REALIZACIÓN DE "
                + "EVENTOS DEL PLAN NACIONAL DE BIENESTAR AL APRENDIZ");
        c.setValor(new BigDecimal("10000000"));
        c.setFechaInicio(LocalDate.of(2025, 6, 17));
        c.setFechaFin(LocalDate.of(2025, 12, 17));
        c.setLugarEjecucion("Calle 63 No. 58 B 03, Barrio Calatrava- Itagüí, Antioquia.");
        c.setContratista("EVENTOS SUPERNOVA S.A.S.");
        c.setContratistaNit("900.478.852-5");
        c.setRepresentanteLegal("Carlos Mario Reina Mejía");
        c.setNumeroRegistroPresupuestal("56725");
        c.setFechaRegistroPresupuestal(LocalDate.of(2025, 6, 17));
        c.setCentroCosto("920510");
        c.setSupervisor(sup);
        return c;
    }

    private static String pdf(String tipo, Contrato c, String obs) {
        return pdf(tipo, c, obs, Map.of());
    }

    private static String pdf(String tipo, Contrato c, String obs, Map<String, String> datos) {
        return pdf(tipo, c, obs, datos, Map.of());
    }

    private static String pdf(String tipo, Contrato c, String obs, Map<String, String> datos,
                              Map<String, List<List<String>>> tablas) {
        return new PdfTextExtractor().extraerTexto(bytes(tipo, c, obs, datos, tablas)).lines().map(String::strip)
                .collect(Collectors.joining(" ")).replaceAll("\\s+", " ");
    }

    private static byte[] bytes(String tipo, Contrato c, String obs, Map<String, String> datos,
                                Map<String, List<List<String>>> tablas) {
        PlantillaDocumentoIA p = PlantillaDocumentoIA.CATALOGO.get(tipo);
        List<BloqueDocumento> bloques = RedactorDeDocumentos.componer(p, c, HOY, obs, datos, tablas);
        // El texto extraído parte las líneas largas; quien lo lee normaliza
        // los espacios para comprobar frases completas.
        return new PdfInstitucional(Clock.fixed(Instant.parse("2026-09-28T15:00:00Z"),
                ZoneId.of("America/Bogota"))).generar(new DocumentoFormal(p.formato(), p.nombre(),
                c.getNumeroContrato(), "Laura", "Generado en SICOT", bloques));
    }

    @ParameterizedTest
    @ValueSource(strings = {"ACTA_INICIO", "INFORME_SUPERVISION", "ACTA_RECIBO", "CERTIFICACION_CUMPLIMIENTO",
            "INFORME_FINAL"})
    void cadaFormatoLlevaLosDatosExactosDelContrato(String tipo) {
        assertThat(pdf(tipo, contrato(), null))
                .contains("CO1.PCCNTR.7986334")
                .contains("EVENTOS SUPERNOVA S.A.S")
                .contains("TOLDOS CARPAS Y LUCES")
                // Nada de lo que el modelo inventó el 24-09-2026.
                .doesNotContain("Bs ")
                .doesNotContain("Secretaría")
                .doesNotContain("null");
    }

    // ── GCCON-F-018 ─────────────────────────────────────────────────────────

    @Test
    void elActaDeInicioSigueElGcconF018() {
        String t = pdf("ACTA_INICIO", contrato(), null);
        assertThat(t)
                .contains("CLASIFICACIÓN DE LA INFORMACIÓN")
                .contains("PROCESO GESTIÓN CONTRACTUAL FORMATO ACTA DE INICIO")
                .contains("CC o NIT")
                .contains("SUPERVISOR DESIGNADO LAURA CAROLINA RESTREPO TORO")
                // Como el acta real: letras sin M/CTE y la cifra en COP.
                .contains("DIEZ MILLONES DE PESOS ($10.000.000 COP)")
                .contains("PLAZO DEL CONTRATO 6 Meses")
                .contains("CALLE 63 NO. 58 B 03, BARRIO CALATRAVA- ITAGÜÍ, ANTIOQUIA.")
                .contains("En Itagüí- Antioquia el día 28 de septiembre de 2026, entre los suscritos LAURA CAROLINA"
                        + " RESTREPO TORO")
                .contains("en calidad de representante legal de EVENTOS SUPERNOVA S.A.S., identificada con NIT."
                        + " 900.478.852-5")
                .contains("56725 del 17 de junio de 2025")
                .contains("Fecha de terminación 17 de diciembre de 2025")
                .contains("Se deja constancia de la verificación de los documentos")
                .contains("Representante Legal")
                .contains("GCCON-F-018 V.04")
                // SICOT no conoce la cédula ni la fecha de las garantías: no las inventa.
                .contains("[dato pendiente: cédula del supervisor]")
                .contains("[dato pendiente: fecha de aprobación de las garantías]")
                // El formato no tiene apartado de observaciones.
                .doesNotContain("Observaciones");
    }

    @Test
    void conLosDatosDelSupervisorElActaNoTienePendientes() {
        String t = pdf("ACTA_INICIO", contrato(), null, Map.of(
                "cedulaSupervisor", "43.512.887", "cedulaRepresentante", "98.587.121",
                "expedicionCedulaRepresentante", "Bello-Antioquia", "fechaAprobacionGarantias", "17/06/2025",
                "correoContratista", "seguridadsocialcres@gmail.com"));

        assertThat(t)
                .contains("nro. 43.512.887, en calidad de supervisor")
                .contains("nro. 98.587.121 de Bello-Antioquia")
                .contains("Fecha de aprobación de las garantías 17 de junio de 2025")
                .contains("seguridadsocialcres@gmail.com")
                .doesNotContain("dato pendiente");
    }

    // ── GCCON-F-031 ─────────────────────────────────────────────────────────

    @Test
    void elInformeDeSupervisionSigueElBloqueDeBienesYServiciosDelGcconF031() {
        String t = pdf("INFORME_SUPERVISION", contrato(), "Se recibieron dos entregas parciales sin novedades.");
        assertThat(t)
                .contains("INFORME DE SUPERVISIÓN – CONTRATOS DE BIENES Y SERVICIOS")
                .contains("CONTRATO NRO. CO1.PCCNTR.7986334 del 2025")
                .contains("CONTRATANTE SENA- Centro Tecnológico del Mobiliario")
                .contains("VALOR INICIAL DEL CONTRATO $10.000.000,00")
                .contains("1.1. Garantías contractuales")
                .contains("GARANTÍA ÚNICA DE CUMPLIMIENTO")
                .contains("VIGENCIA")
                .contains("2. EJECUCIÓN CONTRACTUAL")
                .contains("3. AVANCE FINANCIERO DEL CONTRATO")
                .contains("4. RELACIÓN DE PAGOS DE SEGURIDAD SOCIAL")
                .contains("5. MULTAS Y SANCIONES")
                .contains("6. JUSTIFICACIÓN PARA LA MODIFICACIÓN No aplica")
                .contains("7. CERTIFICACIÓN")
                .contains("autorizo el pago conforme lo pactado contractualmente")
                .contains("8. OBSERVACIONES Se recibieron dos entregas parciales sin novedades.")
                .contains("Para constancia se firma 28/09/2026.")
                .contains("Supervisor del contrato")
                .contains("GCCON-F-031 V04")
                // La parte de servicios personales y la portada no van.
                .doesNotContain("PRESTACIÓN DE SERVICIOS PERSONALES")
                .doesNotContain("Generalidades");
    }

    /** El saldo y el porcentaje se calculan con el valor ejecutado que da el supervisor; no se inventan. */
    @Test
    void elAvanceFinancieroSeCalculaConLoQueDaElSupervisor() {
        String t = pdf("INFORME_SUPERVISION", contrato(), null, Map.of("valorEjecutado", "$4.000.000,00",
                "numeroFactura", "FE 547", "valorFacturado", "4000000"));

        assertThat(t).contains("FE 547").contains("$4.000.000,00").contains("$6.000.000,00").contains("40,00 %");
    }

    // ── GIL-F-010 ───────────────────────────────────────────────────────────

    @Test
    void elActaDeReciboEsLaHojaGilF010() {
        String t = pdf("ACTA_RECIBO", contrato(), "Se reciben los 26 ítems solicitados en sus respectivas cantidades.",
                Map.of("actaNumero", "1", "cantidadConsumo", "26", "tipoEntrega", "suministro"));
        assertThat(t)
                .contains("Versión: 08")
                .contains("Código: GIL-F-010")
                .contains("GESTIÓN DE INFRAESTRUCTURA Y LOGÍSTICA")
                .contains("FORMATO ACTA DE RECIBO A SATISFACCIÓN DE BIENES")
                .contains("28 de septiembre 2026")
                // NIT sin puntos ni dígito de verificación, valor sin signo: como la hoja real.
                .contains("900478852")
                .contains("10.000.000")
                .contains("SUMINISTRO")
                .contains("RECIBIDO A SATISFACCION:")
                .contains("Se reciben los 26 ítems")
                .contains("lcrestrepo@sena.edu.co")
                .contains("3042713044");
    }

    // ── Certificado del supervisor ──────────────────────────────────────────

    @Test
    void laCertificacionSigueElCertificadoEsucon() {
        String t = pdf("CERTIFICACION_CUMPLIMIENTO", contrato(), "notas que no tienen dónde ir", Map.of());
        assertThat(t)
                .contains("EL SUPERVISOR DE CONTRATO NO. CO1.PCCNTR.7986334 DEL 17/06/2025 EVENTOS SUPERNOVA S.A.S.,"
                        + " DANDO CUMPLIMIENTO A LA RESOLUCIÓN 0069 DE 2014, -MANUAL DE SUPERVISIÓN-")
                .contains("CERTIFICA:")
                .contains("con Nit. 900.478.852-5 es proveedor del Centro Tecnológico del Mobiliario")
                .contains("Celebrado por un valor total de $ 10.000.000,00")
                .contains("2. Que la ejecución comenzó el día 17 de junio de 2025.")
                .contains("recibida por el aplicativo SIIF")
                .contains("DEPENDENCIA")
                .contains("CODIGO DE USO PRESUPUESTAL")
                .contains("Itagüí, 28 de septiembre de 2026.")
                .contains("Supervisor")
                // SICOT no conoce la factura ni el saldo: no los inventa.
                .contains("[dato pendiente: número de la factura]")
                .contains("[dato pendiente: saldo por ejecutar después del pago]")
                .doesNotContain("notas que no tienen dónde ir");
    }

    @Test
    void conLosDatosDelPagoLaCertificacionQuedaCompleta() {
        String t = pdf("CERTIFICACION_CUMPLIMIENTO", contrato(), null, Map.ofEntries(
                Map.entry("numeroFactura", "FE 547"), Map.entry("fechaFactura", "03/11/2025"),
                Map.entry("valorFactura", "$4.000.000,00"), Map.entry("tipoCuenta", "ahorros"),
                Map.entry("banco", "Bancolombia"), Map.entry("numeroCuenta", "331-000072-59"),
                Map.entry("titularCuenta", "Eventos Supernova S.A.S."), Map.entry("saldoPorEjecutar", "6000000"),
                Map.entry("rubroPresupuestal", "C-3603-1300-20-20305C-3603025-02"), Map.entry("fuenteRecurso", "nacion"),
                Map.entry("usoPresupuestal", "otros muebles ncp"), Map.entry("codigoUsoPresupuestal", "A-02-02-01-003-008-01"),
                Map.entry("valorAObligar", "$4.000.000,00")));

        assertThat(t)
                .contains("factura FE 547 del 3 de noviembre de 2025 por valor de $ 4.000.000,00")
                .contains("Cuenta AHORROS Banco BANCOLOMBIA No. 331-000072-59 a nombre de EVENTOS SUPERNOVA S.A.S.")
                .contains("será de $ 6.000.000,00.")
                .doesNotContain("dato pendiente");
    }

    // ── GCCON-F-030 ─────────────────────────────────────────────────────────

    @Test
    void elInformeFinalSigueElGcconF030() {
        String t = pdf("INFORME_FINAL", contrato(), null, Map.of("valorTotalPagado", "$9.989.620,00",
                "valorTotalEjecutado", "$9.989.620,00", "valorFinal", "$10.000.000,00"));
        assertThat(t)
                .contains("PROCESO GESTIÓN CONTRACTUAL NOMBRE DEL FORMATO INFORME FINAL DE SUPERVISIÓN")
                .contains("INFORME FINAL DE SUPERVISIÓN CO1.PCCNTR.7986334 del 2025")
                .contains("En mi calidad de supervisor del contrato de la referencia")
                .contains("CONTRATANTE: SENA- Centro Tecnológico del Mobiliario")
                .contains("PLAZO INICIAL Seis (06) meses")
                .contains("CERTIFICADO DE REGISTRO PRESUPUESTAL 56725 DE FECHA 2025-06-17")
                .contains("2.1 Obligaciones")
                .contains("¿CUMPLIÓ?")
                .contains("2.4 Multas y sanciones")
                .contains("3. ASPECTOS LEGALES")
                .contains("3.1 Garantías contractuales")
                .contains("4. OBLIGACIONES DE LA ENTIDAD")
                .contains("8) Las demás que se estimen de acuerdo con la naturaleza de la contratación.")
                .contains("numeral 4 del artículo 4 de la Ley 80 de 1993")
                .contains("5. ASPECTOS FINANCIEROS.")
                .contains("5.2 Estado financiero")
                // Valor a liberar = final − ejecutado; por pagar = ejecutado − pagado.
                .contains("Valor a liberar $10.380,00")
                .contains("Valor por pagar $0,00")
                .contains("Para constancia se firma 28/09/2026.")
                .contains("GCCON-F-030 V05")
                .doesNotContain("CONCLUSIÓN");
    }

    @Test
    void unContratoConDatosIncompletosLosMarcaPendientesEnVezDeInventarlos() {
        Contrato c = contrato();
        c.setContratistaNit(null);
        c.setRepresentanteLegal(null);
        c.setFechaInicio(null);
        for (String tipo : PlantillaDocumentoIA.CATALOGO.keySet()) {
            assertThat(pdf(tipo, c, null)).as(tipo).contains("[dato pendiente").doesNotContain("null");
        }
    }

    // ── Formato de los valores ──────────────────────────────────────────────

    @Test
    void losValoresSeEscribenComoEnCadaFormato() {
        assertThat(RedactorDeDocumentos.pesos(new BigDecimal("20000000"), false)).isEqualTo("$20.000.000,00");
        assertThat(RedactorDeDocumentos.pesos(new BigDecimal("99067527"), true)).isEqualTo("$ 99.067.527,00");
        assertThat(RedactorDeDocumentos.entero(new BigDecimal("18924558.10"))).isEqualTo("18.924.558");
        assertThat(RedactorDeDocumentos.valorEnLetrasYCifra(new BigDecimal("10000000")))
                .isEqualTo("DIEZ MILLONES DE PESOS ($10.000.000 COP)");
        assertThat(RedactorDeDocumentos.soloDigitosSinVerificacion("900.478.852-5")).isEqualTo("900478852");
        assertThat(RedactorDeDocumentos.leerPesos("$39.400.634,00")).isEqualByComparingTo("39400634.00");
        assertThat(RedactorDeDocumentos.leerPesos("39400634")).isEqualByComparingTo("39400634");
        assertThat(RedactorDeDocumentos.leerPesos("treinta millones")).isNull();
        assertThat(RedactorDeDocumentos.leerFecha("3/11/2025")).isEqualTo(LocalDate.of(2025, 11, 3));
        assertThat(RedactorDeDocumentos.leerFecha("2025-11-03")).isEqualTo(LocalDate.of(2025, 11, 3));
    }

    /**
     * El plazo pactado no siempre coincide con la diferencia de fechas: solo se
     * deduce si es un número exacto de meses; si no, se escriben las fechas.
     */
    @Test
    void elPlazoSoloSeDeduceSiEsUnNumeroExactoDeMeses() {
        Contrato c = contrato();
        assertThat(RedactorDeDocumentos.plazo(c, false)).isEqualTo("6 Meses");
        assertThat(RedactorDeDocumentos.plazo(c, true)).isEqualTo("Seis (06) meses");
        c.setFechaFin(LocalDate.of(2025, 7, 16));
        assertThat(RedactorDeDocumentos.plazo(c, true)).isEqualTo("Un (01) mes");
        c.setFechaFin(LocalDate.of(2025, 8, 3));
        assertThat(RedactorDeDocumentos.plazo(c, true)).isEqualTo("Del 17/06/2025 al 03/08/2025");
    }

    // ── Hallazgos de la revisión adversarial del 28-09-2026 ─────────────────

    /** «19989620.00» copiado de Excel se leía como 1.998.962.000 y así iba al certificado firmado. */
    @Test
    void unValorConPuntoDecimalNoSeMultiplicaPorCien() {
        assertThat(RedactorDeDocumentos.leerPesos("19989620.00")).isNull();
        assertThat(RedactorDeDocumentos.leerPesos("1.2")).isNull();
        assertThat(RedactorDeDocumentos.leerPesos("$ 4.000.000")).isEqualByComparingTo("4000000");
        String t = pdf("CERTIFICACION_CUMPLIMIENTO", contrato(), null, Map.of("valorFactura", "19989620.00"));
        // Sale tal cual lo escribió el supervisor, no una cifra «entendida» cien veces mayor.
        assertThat(t).contains("por valor de 19989620.00").doesNotContain("1.998.962.000");
    }

    /** Una fecha que no existe se deja tal cual; antes «31/02/2025» se firmaba como 28/02/2025. */
    @Test
    void unaFechaQueNoExisteNoSeCorrigeEnSilencio() {
        assertThat(RedactorDeDocumentos.leerFecha("31/02/2025")).isNull();
        assertThat(RedactorDeDocumentos.leerFecha("29/02/2024")).isEqualTo(LocalDate.of(2024, 2, 29));
        assertThat(pdf("INFORME_SUPERVISION", contrato(), null, Map.of("fechaSuscripcion", "31/02/2025")))
                .contains("FECHA DE SUSCRIPCIÓN 31/02/2025").doesNotContain("28/02/2025");
    }

    /**
     * Las conclusiones del Informe Final (cumplimiento, multas, mantenimiento)
     * venían de un informe ya diligenciado: sin la declaración del supervisor
     * no se afirman.
     */
    @Test
    void elInformeFinalNoAfirmaConclusionesQueElSupervisorNoDeclaro() {
        String sin = pdf("INFORME_FINAL", contrato(), null);
        assertThat(sin)
                .contains("[dato pendiente: declaración sobre el cumplimiento del objeto")
                .contains("[dato pendiente: multas o sanciones")
                .contains("[dato pendiente: requieren o no requieren] revisiones")
                .doesNotContain("no se generaron incumplimientos")
                .doesNotContain("cumplió a cabalidad")
                .doesNotContain("NO se presentaron multas");

        String con = pdf("INFORME_FINAL", contrato(), null,
                Map.of("cumplimientoObjeto", "sí", "multas", "No", "mantenimiento", "NO"));
        assertThat(con)
                .contains("se dio cumplimiento a satisfacción de los bienes y/o servicios requeridos")
                .contains("no se generaron incumplimientos")
                .contains("NO se presentaron multas")
                .contains("cumplió a cabalidad")
                .contains("los bienes recibidos no requieren revisiones");

        assertThat(pdf("INFORME_FINAL", contrato(), null, Map.of("cumplimientoObjeto", "PARCIALMENTE",
                "multas", "Multa del 5 % por entrega tardía (Resolución 123)", "mantenimiento", "SI")))
                .contains("se cumplió parcialmente")
                .contains("se presentaron las siguientes multas y/o sanciones: Multa del 5 %")
                .contains("los bienes recibidos requieren revisiones")
                .doesNotContain("cumplió a cabalidad");
    }

    @Test
    void conUnaAdicionElValorActualNoEsElInicialYUnSaldoNegativoNoSeFirma() {
        String t = pdf("INFORME_SUPERVISION", contrato(), null,
                Map.of("adicion", "ADICIÓN 1 por $5.000.000,00", "valorEjecutado", "$12.000.000,00"));
        assertThat(t)
                .contains("VALOR ACTUAL DEL CONTRATO [dato pendiente: valor actual con las adiciones]")
                .doesNotContain("$-");

        String inconsistente = pdf("INFORME_SUPERVISION", contrato(), null, Map.of("valorEjecutado", "$12.000.000,00"));
        assertThat(inconsistente).contains("[dato pendiente: el ejecutado supera el valor del contrato]")
                .doesNotContain("$-");

        assertThat(pdf("INFORME_SUPERVISION", contrato(), null, Map.of("prorroga", "PRÓRROGA 1 hasta el 20/12/2025")))
                .contains("[dato pendiente: fecha de terminación con las prórrogas]");
    }

    /** Un contrato de vigencias futuras se suscribe un año y empieza el siguiente. */
    @Test
    void elAnoDelNumeroDeContratoEsElDeLaSuscripcionSiSeConoce() {
        Contrato c = contrato();
        c.setFechaInicio(LocalDate.of(2026, 1, 13));
        c.setFechaFin(LocalDate.of(2026, 7, 13));
        assertThat(pdf("INFORME_FINAL", c, null, Map.of("fechaSuscripcion", "22/12/2025")))
                .contains("CO1.PCCNTR.7986334 del 2025").doesNotContain("CO1.PCCNTR.7986334 del 2026");
    }

    @Test
    void laCertificacionUsaLaFechaDeSuscripcionCuandoSeLaDan() {
        assertThat(pdf("CERTIFICACION_CUMPLIMIENTO", contrato(), null, Map.of("fechaSuscripcion", "14/10/2025")))
                .contains("NO. CO1.PCCNTR.7986334 DEL 14/10/2025");
    }

    @Test
    void unDatoQueLlegaSinSuParejaNoSePierde() {
        assertThat(pdf("INFORME_SUPERVISION", contrato(), null, Map.of("periodoDesde", "01/09/2026")))
                .contains("Desde el 01/09/2026 al [dato pendiente: fin del periodo del informe]");
        assertThat(pdf("INFORME_FINAL", contrato(), null, Map.of("fechaCdp", "07/02/2025")))
                .contains("[dato pendiente: número del certificado de disponibilidad presupuestal] DE FECHA 2025-02-07");
    }

    /**
     * La GIL-F-010 tiene dos renglones para observaciones: lo que no cabe va
     * completo a una hoja de continuación. Antes se cortaba con «[…]» en el acta
     * firmada.
     */
    @Test
    void enLaHojaDeReciboElTextoQueNoCabeSigueEnUnaHojaDeContinuacion() {
        String larga = ("Se reciben 14 sillas ergonómicas, 6 mesas de trabajo, 3 archivadores metálicos y 26 cajas de"
                + " resmas de papel, verificados uno a uno contra la orden y el anexo técnico. ").repeat(6)
                + "Quedan a disposición del almacén para su ingreso al inventario del Centro.";
        String t = pdf("ACTA_RECIBO", contrato(), larga);
        assertThat(t)
                .contains("(continúa en la hoja siguiente)")
                .contains("Continuación")
                .contains("ingreso al inventario del Centro.")
                .doesNotContain("[…]");
    }

    @Test
    void losPendientesSeCuentanEnLoQueQuedoEscritoYNoEnLosCamposVacios() {
        PlantillaDocumentoIA acta = PlantillaDocumentoIA.CATALOGO.get("ACTA_INICIO");
        // El plazo se deduce y el correo es opcional: no dejan nada pendiente.
        assertThat(RedactorDeDocumentos.contarPendientes(RedactorDeDocumentos.componer(acta, contrato(), HOY, null,
                Map.of("cedulaSupervisor", "1", "cedulaRepresentante", "2", "expedicionCedulaRepresentante", "Bello",
                        "fechaAprobacionGarantias", "17/06/2025")))).isZero();
        assertThat(RedactorDeDocumentos.contarPendientes(RedactorDeDocumentos.componer(acta, contrato(), HOY, null)))
                .isEqualTo(4);
    }

    @ParameterizedTest
    @ValueSource(strings = {"No", "no.", "N/A", "No aplica", "Ningún", "ninguna", "No hubo", "No se presentaron"})
    void unaRespuestaNegativaSeReconoceAunqueLleveTilde(String respuesta) {
        assertThat(RedactorDeDocumentos.esNo(respuesta)).isTrue();
    }

    @Test
    void unaRespuestaConContenidoNoSeTomaComoNegativa() {
        assertThat(RedactorDeDocumentos.esNo("Otrosí No. 1 del 15/08/2025")).isFalse();
        assertThat(RedactorDeDocumentos.esNo("$ 5.000.000")).isFalse();
        assertThat(RedactorDeDocumentos.esNo(null)).isFalse();
    }

    // ── Tablas que se llenan fila por fila ──────────────────────────────────
    //
    // Hasta el 30-09-2026 cada tabla tenía una sola fila con «[dato
    // pendiente]»: el Informe Final real (CO1.PCCNTR.8426076) relaciona 15
    // obligaciones generales, 16 específicas, 3 del SIGA de SST, 3 ambientales,
    // 3 amparos y 2 órdenes de pago, y SICOT no podía escribir ninguna.
    // Los textos de estas pruebas son los de ese informe.

    private static final List<String> EJECUTAR = List.of(
            "Ejecutar el objeto del contrato bajo las condiciones de calidad, oportunidad y obligaciones definidas en"
                    + " el proceso de contratación.",
            "si cumplio",
            "Se ejecuta el suministro de materiales para la formación con los parámetros de planeación, calidades"
                    + " definidas en el contrato.");

    private static final List<String> FUERZA_MAYOR = List.of(
            "Informar, por escrito y dentro de los 3 días hábiles siguientes a su materialización, cualquier"
                    + " eventualidad de fuerza mayor o caso fortuito que afecte la normal y correcta ejecución del"
                    + " contrato",
            "No se requirió el cumplimiento",
            "A la fecha no se han presentado eventualidades o casos fortuito que afecte la ejecución del contrato.");

    private static final List<String> PROVEER = List.of(
            "Proveer los bienes nuevos, libres de defectos, vencimientos e imperfecciones.",
            "Se verificó que los tóner y tintas suministrados no tuvieran defectos ni estuvieran vencidos.",
            "Acta de recibo publicada en SECOP II");

    private static final Map<String, List<List<String>>> TABLAS_DEL_INFORME_FINAL = Map.of(
            "obligacionesGenerales", List.of(EJECUTAR, FUERZA_MAYOR),
            "obligacionesEspecificas", List.of(PROVEER),
            "sigaSst", List.of(List.of("Relación del personal que ingresa a las instalaciones con su respectivo pago"
                    + " de seguridad social.", "El contratista suministra el pago de la seguridad social.",
                    "Planillas en SECOP II")),
            "sigaAmbiental", List.of(List.of("Acreditar que cuenta con un programa de Recolección y Gestión de"
                    + " residuos.", "El contratista acreditó el programa en la etapa precontractual.",
                    "El contratista entrega acta de disposición final de los productos")),
            "amparos", List.of(
                    List.of("Cumplimiento - Cumplimiento del contrato", "14/10/2025", "2026-06-16", "4000000"),
                    List.of("Calidad de los bienes solicitados", "14/10/2025", "16/06/2026", "$ 4.000.000,00")),
            "ordenesDePago", List.of(List.of("70614726", "11/03/2026", "16798000"),
                    List.of("70614826", "2026-03-11", "$ 3.191.620,00")));

    /** El texto de cada celda de las tablas del documento, tal como se va a dibujar. */
    private static List<String> celdas(String tipo, Map<String, String> datos, Map<String, List<List<String>>> tablas) {
        return RedactorDeDocumentos.componer(PlantillaDocumentoIA.CATALOGO.get(tipo), contrato(), HOY, null, datos,
                        tablas).stream()
                .filter(b -> b instanceof BloqueDocumento.Tabla)
                .flatMap(b -> ((BloqueDocumento.Tabla) b).filas().stream())
                .flatMap(f -> f.celdas().stream())
                .map(c -> c.tramos().stream().map(BloqueDocumento.Tramo::texto).collect(Collectors.joining()))
                .toList();
    }

    @Test
    void elInformeFinalLlevaCadaObligacionAmparoYOrdenDePagoQueDaElSupervisor() {
        List<String> c = celdas("INFORME_FINAL", Map.of("valorTotalPagado", "$19.989.620,00"),
                TABLAS_DEL_INFORME_FINAL);

        assertThat(c)
                .contains("1. " + EJECUTAR.get(0), "SI CUMPLIO", EJECUTAR.get(2))
                // En mayúsculas, como las escribe el informe real.
                .contains("2. " + FUERZA_MAYOR.get(0), "NO SE REQUIRIÓ EL CUMPLIMIENTO")
                .contains("OBLIGACIONES ESPECIFICAS DEL CONTRATISTA.", "1. " + PROVEER.get(0), PROVEER.get(1))
                .contains("1. Relación del personal que ingresa a las instalaciones con su respectivo pago de"
                        + " seguridad social.", "El contratista acreditó el programa en la etapa precontractual.")
                // Fechas y valores con el formato de la tabla, se escriban como se escriban.
                .containsSequence("Cumplimiento - Cumplimiento del contrato", "14/10/2025", "16/06/2026",
                        "$ 4.000.000,00")
                .containsSequence("Calidad de los bienes solicitados", "14/10/2025", "16/06/2026", "$ 4.000.000,00")
                .containsSequence("70614726", "11/03/2026", "$16.798.000,00")
                .containsSequence("70614826", "11/03/2026", "$3.191.620,00")
                // Lo que queda pendiente es solo lo que no se dio (la póliza, el
                // estado financiero), no las filas que el supervisor escribió.
                .doesNotContain("1. [dato pendiente: obligaciones del contrato]",
                        "[dato pendiente: SI CUMPLIO / NO / PARCIALMENTE]",
                        "1. [dato pendiente: obligaciones específicas del contrato]", "[dato pendiente: obligación]",
                        "[dato pendiente: vigencia y valor]");
        assertThat(pdf("INFORME_FINAL", contrato(), null, Map.of(), TABLAS_DEL_INFORME_FINAL))
                .contains("SI CUMPLIO").contains("$16.798.000,00").contains("$3.191.620,00");
    }

    @Test
    void elInformeDeSupervisionRelacionaCadaObligacionEspecificaYElSiga() {
        Map<String, List<List<String>>> tablas = Map.of("obligacionesEspecificas", List.of(PROVEER,
                        List.of("Entregar las fichas técnicas de los productos.", "Se revisaron en SECOP II.",
                                "Fichas técnicas aprobadas")),
                "amparos", List.of(List.of("Cumplimiento", "14/10/2025", "16/06/2026", "$4.000.000,00")));

        assertThat(celdas("INFORME_SUPERVISION", Map.of(), tablas))
                .containsSequence("1. " + PROVEER.get(0), PROVEER.get(1), PROVEER.get(2))
                .containsSequence("2. Entregar las fichas técnicas de los productos.", "Se revisaron en SECOP II.",
                        "Fichas técnicas aprobadas")
                .containsSequence("Cumplimiento", "14/10/2025", "16/06/2026", "$ 4.000.000,00")
                .noneMatch(t -> t.contains("[dato pendiente: obligaciones específicas"));
        assertThat(pdf("INFORME_SUPERVISION", contrato(), null, Map.of("siga", "No aplica"), tablas))
                .contains("SIGA No aplica.")
                .doesNotContain("[dato pendiente: cumplimiento de las obligaciones ambientales");
    }

    @Test
    void unaCeldaVaciaQuedaPendienteYUnaTablaQueElFormatoNoTieneNoLlega() {
        List<String> c = celdas("INFORME_SUPERVISION", Map.of(),
                Map.of("obligacionesEspecificas", List.of(List.of("Entregar los bienes.", "", "")),
                        // El Informe de Supervisión no tiene órdenes de pago.
                        "ordenesDePago", List.of(List.of("70614726", "11/03/2026", "16798000"))));

        assertThat(c).containsSequence("1. Entregar los bienes.", "[dato pendiente]", "[dato pendiente]")
                .doesNotContain("70614726");
    }

    @Test
    void sinAmparosLaGarantiaLosDejaPendientesYSinPolizaDiceNoAplica() {
        assertThat(celdas("INFORME_SUPERVISION", Map.of(), Map.of()))
                .containsSequence("Cumplimiento", "[dato pendiente: vigencia y valor]")
                .containsSequence("Calidad del servicio", "[dato pendiente: vigencia y valor]");
        assertThat(celdas("INFORME_FINAL", Map.of("aseguradora", "No aplica"), Map.of()))
                .containsSequence("Cumplimiento - Cumplimiento del contrato", "No aplica")
                .doesNotContain("[dato pendiente: vigencia y valor]");
    }

    /** Las 31 obligaciones del informe real no caben en una página: la tabla sigue en la siguiente con su encabezado. */
    @Test
    void unaTablaLargaSigueEnLaPaginaSiguienteSinPerderFilas() throws Exception {
        List<List<String>> muchas = new java.util.ArrayList<>();
        for (int i = 1; i <= 30; i++) {
            muchas.add(List.of("Obligación número " + i + " del contrato, copiada tal como está en el contrato"
                    + " suscrito por las partes.", "SI CUMPLIO", "Evidencia " + i + " publicada en SECOP II."));
        }
        byte[] pdf = bytes("INFORME_FINAL", contrato(), null, Map.of(), Map.of("obligacionesGenerales", muchas));

        // La evidencia cabe en un renglón de su columna: se lee entera en el texto del PDF.
        String t = new PdfTextExtractor().extraerTexto(pdf).replaceAll("\\s+", " ");
        for (int i = 1; i <= 30; i++) {
            assertThat(t).contains("Evidencia " + i + " publicada en SECOP II.");
        }
        try (var documento = org.apache.pdfbox.Loader.loadPDF(pdf)) {
            assertThat(documento.getNumberOfPages()).isGreaterThan(5);
        }
        // El encabezado se repite en cada página que ocupa la tabla.
        assertThat(t.split("PRODUCTO O EVIDENCIA", -1).length - 1).isGreaterThan(3);
    }

    @Test
    void lasOrdenesDePagoTienenQueSumarElValorTotalPagado() {
        PlantillaDocumentoIA fin = PlantillaDocumentoIA.CATALOGO.get("INFORME_FINAL");
        Map<String, List<List<String>>> ordenes = Map.of("ordenesDePago", List.of(
                List.of("70614726", "11/03/2026", "$16.798.000,00"), List.of("70614826", "11/03/2026", "3191620")));

        assertThat(RedactorDeDocumentos.incoherencia(fin, Map.of("valorTotalPagado", "$19.989.620,00"), ordenes))
                .isNull();
        assertThat(RedactorDeDocumentos.incoherencia(fin, Map.of("valorTotalPagado", "$20.000.000,00"), ordenes))
                .contains("suman $19.989.620,00").contains("$20.000.000,00");
        // Una orden que no se lee como cifra no se compara: va tal cual.
        assertThat(RedactorDeDocumentos.incoherencia(fin, Map.of("valorTotalPagado", "$20.000.000,00"),
                Map.of("ordenesDePago", List.of(List.of("1", "11/03/2026", "diecinueve millones"))))).isNull();
        // Los demás formatos no tienen esa regla.
        assertThat(RedactorDeDocumentos.incoherencia(PlantillaDocumentoIA.CATALOGO.get("INFORME_SUPERVISION"),
                Map.of("valorTotalPagado", "$1,00"), ordenes)).isNull();
    }

    @Test
    void conTodasLasTablasElInformeFinalSoloDejaPendienteLoQueNoSeDio() {
        List<BloqueDocumento> bloques = RedactorDeDocumentos.componer(PlantillaDocumentoIA.CATALOGO.get("INFORME_FINAL"),
                contrato(), HOY, null, Map.of(), TABLAS_DEL_INFORME_FINAL);
        List<BloqueDocumento> sinTablas = RedactorDeDocumentos.componer(
                PlantillaDocumentoIA.CATALOGO.get("INFORME_FINAL"), contrato(), HOY, null, Map.of());

        // Generales (3), específicas (3), SIGA (3 + 3), amparos (3) y órdenes de pago (3).
        assertThat(RedactorDeDocumentos.contarPendientes(sinTablas)
                - RedactorDeDocumentos.contarPendientes(bloques)).isEqualTo(18);
    }

    // ── Revisión del 01-10-2026 ─────────────────────────────────────────────

    /** Los tramos de la celda de la tabla que contiene ese texto. */
    private static List<BloqueDocumento.Tramo> tramosDeLaCelda(String tipo, Map<String, String> datos,
                                                               Map<String, List<List<String>>> tablas, String contiene) {
        return RedactorDeDocumentos.componer(PlantillaDocumentoIA.CATALOGO.get(tipo), contrato(), HOY, null, datos,
                        tablas).stream()
                .filter(b -> b instanceof BloqueDocumento.Tabla)
                .flatMap(b -> ((BloqueDocumento.Tabla) b).filas().stream())
                .flatMap(f -> f.celdas().stream())
                .filter(c -> c.tramos().stream().map(BloqueDocumento.Tramo::texto).collect(Collectors.joining())
                        .contains(contiene))
                .findFirst().orElseThrow().tramos();
    }

    @Test
    void enCumplioSoloLaRespuestaVaEnMayusculasYNegritaYLoDemasComoLoEscribio() {
        String explicacion = " Se verifican las especificaciones en https://community.secop.gov.co/Public/Index";
        List<BloqueDocumento.Tramo> t = tramosDeLaCelda("INFORME_FINAL", Map.of(),
                Map.of("obligacionesGenerales", List.of(List.of("Cumplir la oferta.", "si cumplio" + explicacion, ""))),
                "SI CUMPLIO");

        assertThat(t.get(0).texto()).isEqualTo("SI CUMPLIO");
        assertThat(t.get(0).estilo()).isEqualTo(BloqueDocumento.Estilo.NEGRITA);
        assertThat(t.get(1).texto()).isEqualTo(explicacion);
        // Una respuesta que no es una de las opciones va tal cual.
        assertThat(tramosDeLaCelda("INFORME_FINAL", Map.of(),
                Map.of("obligacionesGenerales", List.of(List.of("Cumplir la oferta.", "Cumplió en octubre", "")))
                , "en octubre").get(0).texto()).isEqualTo("Cumplió en octubre");
    }

    @Test
    void laObligacionCopiadaConSuNumeroNoSaleNumeradaDosVeces() {
        List<String> c = celdas("INFORME_FINAL", Map.of(), Map.of(
                "obligacionesGenerales", List.of(List.of("3. entregar las garantías", "SI CUMPLIO", "")),
                "obligacionesEspecificas", List.of(List.of("1) Proveer los bienes nuevos", "", ""),
                        List.of("2 - Entregar las fichas", "", ""), List.of("1.5 toneladas de arena", "", ""))));

        assertThat(c).contains("1. entregar las garantías", "1. Proveer los bienes nuevos", "2. Entregar las fichas",
                "3. 1.5 toneladas de arena");
    }

    @Test
    void unParrafoDelSigaQueEmpiezaPorNoSeEscribeEnteroYSoloElNoSoloDiceNoAplica() {
        String parrafo = "No se presentaron incidentes de SST. El contratista entregó la planilla del personal.";
        assertThat(pdf("INFORME_SUPERVISION", contrato(), null, Map.of("siga", parrafo))).contains(parrafo)
                .doesNotContain("SIGA No aplica.");
        assertThat(pdf("INFORME_SUPERVISION", contrato(), null, Map.of("siga", "no aplica.")))
                .contains("SIGA No aplica.");
        assertThat(RedactorDeDocumentos.esSoloNo("N/A")).isTrue();
        assertThat(RedactorDeDocumentos.esSoloNo("Ninguna novedad ambiental")).isFalse();
    }
}
