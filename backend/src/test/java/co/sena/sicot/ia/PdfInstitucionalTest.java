package co.sena.sicot.ia;

import co.sena.sicot.ia.BloqueDocumento.Alineacion;
import co.sena.sicot.ia.BloqueDocumento.Campo;
import co.sena.sicot.ia.BloqueDocumento.Celda;
import co.sena.sicot.ia.BloqueDocumento.Ficha;
import co.sena.sicot.ia.BloqueDocumento.Fila;
import co.sena.sicot.ia.BloqueDocumento.Parrafo;
import co.sena.sicot.ia.BloqueDocumento.Tabla;
import co.sena.sicot.ia.BloqueDocumento.Tramo;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.contentstream.operator.color.SetNonStrokingColor;
import org.apache.pdfbox.contentstream.operator.color.SetNonStrokingColorN;
import org.apache.pdfbox.contentstream.operator.color.SetNonStrokingColorSpace;
import org.apache.pdfbox.contentstream.operator.color.SetNonStrokingDeviceRGBColor;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * El generador de los PDF que el supervisor firma.
 *
 * <p>Su salida es un <b>documento oficial que alguien va a firmar</b>. Un PDF
 * con el texto cortado, sin el pie del formato o con una fuente que el visor
 * reemplaza no rompe nada visiblemente: se firma igual y el defecto queda
 * dentro de un expediente. Por eso las pruebas vuelven a abrir el PDF y
 * comprueban lo que una persona vería al abrirlo, y lo que un sistema de
 * gestión documental exigiría (fuentes incrustadas, propiedades).
 */
class PdfInstitucionalTest {

    private static final Clock RELOJ = Clock.fixed(Instant.parse("2026-09-28T15:00:00Z"), ZoneId.of("America/Bogota"));
    private final PdfInstitucional escritor = new PdfInstitucional(RELOJ);

    private static final PlantillaDocumentoIA INFORME = PlantillaDocumentoIA.CATALOGO.get("INFORME_SUPERVISION");

    private byte[] generar(PlantillaDocumentoIA plantilla, List<BloqueDocumento> bloques) {
        return escritor.generar(new DocumentoFormal(plantilla.formato(), plantilla.nombre() + " — CO1.PCCNTR.1",
                "CO1.PCCNTR.1", "Paola Andrea Mejía", "Generado en SICOT", bloques));
    }

    private byte[] generar(List<BloqueDocumento> bloques) {
        return generar(INFORME, bloques);
    }

    private static String texto(byte[] pdf) throws IOException {
        try (PDDocument d = Loader.loadPDF(pdf)) {
            return new PDFTextStripper().getText(d);
        }
    }

    private static int paginas(byte[] pdf) throws IOException {
        try (PDDocument d = Loader.loadPDF(pdf)) {
            return d.getNumberOfPages();
        }
    }

    @Test
    void generaUnPdfValidoConElCuerpo() throws Exception {
        byte[] pdf = generar(List.of(new Parrafo("El presente documento deja constancia del inicio del contrato.")));

        assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");
        assertThat(texto(pdf)).contains("deja constancia del inicio");
    }

    // ── Lo que hace que se parezca al formato real ──────────────────────────

    @Test
    void llevaLaClasificacionDeLaInformacionYElPieConCodigoYVersion() throws Exception {
        String t = texto(generar(List.of(new Parrafo("Cuerpo."))));

        assertThat(t).contains("CLASIFICACIÓN DE LA INFORMACIÓN").contains("Pública Clasificada")
                .contains("GCCON-F-031 V04");
    }

    @Test
    void elInformeFinalLlevaLasBarrasDeProcesoYNombreDelFormato() throws Exception {
        String t = texto(generar(PlantillaDocumentoIA.CATALOGO.get("INFORME_FINAL"), List.of(new Parrafo("Cuerpo."))));

        assertThat(t).contains("PROCESO").contains("GESTIÓN CONTRACTUAL").contains("NOMBRE DEL FORMATO")
                .contains("INFORME FINAL DE SUPERVISIÓN").contains("GCCON-F-030 V05");
    }

    /** El logo va en el encabezado de Word de los GCCON: en todas las páginas. */
    @Test
    void elLogoDelSenaVaEnCadaPagina() throws Exception {
        byte[] pdf = generar(parrafos(120));
        try (PDDocument d = Loader.loadPDF(pdf)) {
            assertThat(d.getNumberOfPages()).isGreaterThan(1);
            for (PDPage p : d.getPages()) {
                int imagenes = 0;
                for (COSName nombre : p.getResources().getXObjectNames()) {
                    if (p.getResources().getXObject(nombre) instanceof PDImageXObject) {
                        imagenes++;
                    }
                }
                assertThat(imagenes).as("imágenes en la página").isGreaterThanOrEqualTo(1);
            }
        }
    }

    /**
     * Con la Helvetica estándar el PDF no llevaba la fuente: cada visor ponía
     * la suya y el documento firmado se veía distinto según quién lo abriera.
     */
    @Test
    void todasLasFuentesVanIncrustadas() throws Exception {
        byte[] pdf = generar(List.of(new Parrafo(Alineacion.JUSTIFICADO, Tramo.negrita("Negrita "),
                Tramo.italica("cursiva "), Tramo.normal("normal"))));
        try (PDDocument d = Loader.loadPDF(pdf)) {
            for (PDPage p : d.getPages()) {
                for (COSName nombre : p.getResources().getFontNames()) {
                    PDFont f = p.getResources().getFont(nombre);
                    assertThat(f.isEmbedded()).as("fuente %s incrustada", f.getName()).isTrue();
                    assertThat(f.getName()).containsAnyOf("Carlito", "LiberationSans");
                }
            }
        }
    }

    @Test
    void lasPropiedadesDelPdfDicenQueEsYDeDondeSale() throws Exception {
        byte[] pdf = generar(List.of(new Parrafo("Cuerpo.")));
        try (PDDocument d = Loader.loadPDF(pdf)) {
            assertThat(d.getDocumentInformation().getTitle()).isEqualTo("Informe de Supervisión — CO1.PCCNTR.1");
            assertThat(d.getDocumentInformation().getSubject()).contains("GCCON-F-031");
            assertThat(d.getDocumentInformation().getCreator()).isEqualTo("SICOT");
            assertThat(d.getDocumentCatalog().getLanguage()).isEqualTo("es-CO");
        }
    }

    /** Lo que no es del formato se dice: quién lo generó y cuándo. */
    @Test
    void laLineaDeTrazabilidadDiceElOrigenYLaFechaDelCentro() throws Exception {
        assertThat(texto(generar(List.of(new Parrafo("Cuerpo."))))).contains("Generado en SICOT el 28/09/2026");
    }

    /**
     * 19:30 del 23 de septiembre en Bogotá son las 00:30 del 24 en UTC, que es
     * la zona de la JVM dentro del contenedor (MDL-214).
     */
    @Test
    void laFechaEsLaDelCentroAunqueEnUtcYaSeaManana() throws Exception {
        Clock noche = Clock.fixed(Instant.parse("2026-09-24T00:30:00Z"), ZoneId.of("America/Bogota"));
        byte[] pdf = new PdfInstitucional(noche).generar(new DocumentoFormal(INFORME.formato(), "t", "n", null,
                "Generado en SICOT", List.of(new Parrafo("Cuerpo."))));

        assertThat(texto(pdf)).contains("23/09/2026").doesNotContain("24/09/2026");
    }

    // ── Ancla de la firma ───────────────────────────────────────────────────

    @Test
    void elPdfGuardaDondeQuedoElHuecoDeLaFirma() throws Exception {
        byte[] pdf = generar(List.of(new BloqueDocumento.Firmas(List.of(
                new BloqueDocumento.Firmante("Paola Andrea Mejía", true, List.of("Supervisor del contrato"))),
                BloqueDocumento.DisposicionFirmas.IZQUIERDA, 60f)));
        try (PDDocument d = Loader.loadPDF(pdf)) {
            String ancla = d.getDocumentInformation().getCustomMetadataValue(PdfInstitucional.PROPIEDAD_ANCLA_FIRMA);
            assertThat(ancla).matches("0;[0-9.]+;[0-9.]+;[0-9.]+;[0-9.]+");
        }
    }

    // ── Texto que no cabe ───────────────────────────────────────────────────

    @Test
    void parteEnVariasLineasUnParrafoLargo() throws Exception {
        String largo = "El contratista entregó la totalidad de los bienes descritos en el anexo técnico, incluyendo mesas,"
                + " sillas y archivadores metálicos, verificados uno a uno contra las especificaciones acordadas en el"
                + " estudio previo y la propuesta económica presentada durante el proceso.";

        String t = texto(generar(List.of(new Parrafo(largo))));

        assertThat(t.replaceAll("\\s+", " ")).contains("archivadores metálicos");
    }

    @Test
    void abreMasPaginasCuandoElContenidoNoCabeYLasNumera() throws Exception {
        byte[] pdf = generar(parrafos(90));

        assertThat(paginas(pdf)).isGreaterThan(1);
        String t = texto(pdf);
        assertThat(t).contains("Párrafo número 90");
        // Número de página a la derecha, solo la cifra, como en el formato.
        assertThat(t.lines().map(String::strip)).contains("2");
    }

    /**
     * Una fila de ficha más alta que una página (un objeto de 4000 caracteres,
     * que es lo que admite la API) se parte entre páginas. Antes se dibujaba
     * por debajo del borde de la hoja y la cola se perdía en el PDF firmado.
     */
    @Test
    void unaFilaMasAltaQueUnaPaginaSeParteSinPerderTexto() throws Exception {
        StringBuilder objeto = new StringBuilder();
        IntStream.rangeClosed(1, 400).forEach(i -> objeto.append("palabra").append(i).append(' '));
        byte[] pdf = generar(List.of(new Ficha(List.of(new Campo("OBJETO", objeto.toString().strip())))));

        assertThat(paginas(pdf)).isGreaterThan(1);
        String t = texto(pdf).replaceAll("\\s+", " ");
        assertThat(t).contains("palabra1 ").contains("palabra400");
        assertThat(maximaAlturaYAnchura(pdf)).allSatisfy(dentro -> assertThat(dentro).isTrue());
    }

    /** Una palabra más ancha que la columna (un enlace del SECOP) se parte, no se sale de la hoja. */
    @Test
    void unaPalabraMasAnchaQueLaPaginaNoSeSaleDeLaHoja() throws Exception {
        byte[] pdf = generar(List.of(new Parrafo("A".repeat(400)),
                new Tabla(List.of(100f, 370f), List.of(Fila.de(Celda.de("B".repeat(150)), Celda.de("x"))), 0, false,
                        5.4f)));

        assertThat(maximaAlturaYAnchura(pdf)).allSatisfy(dentro -> assertThat(dentro).isTrue());
    }

    /**
     * La hoja GIL-F-010 tiene renglones fijos: un enlace del SECOP en el objeto
     * se salía por la derecha del recuadro (prueba en vivo del 28-09-2026).
     */
    @Test
    void unEnlaceLargoEnElObjetoNoSeSaleDeLaHojaDeRecibo() throws Exception {
        PlantillaDocumentoIA recibo = PlantillaDocumentoIA.CATALOGO.get("ACTA_RECIBO");
        co.sena.sicot.entity.Contrato c = new co.sena.sicot.entity.Contrato();
        c.setNumeroContrato("CO1.PCCNTR.1");
        c.setObjeto("ADQUISICIÓN DE MOBILIARIO SEGÚN LOS ESTUDIOS PREVIOS https://community.secop.gov.co/Public/"
                + "Tendering/OpportunityDetail/Index?noticeUID=CO1.NTC.1234567890123&isFromPublicArea=True&isModal=False");
        byte[] pdf = generar(recibo, RedactorDeDocumentos.componer(recibo, c, java.time.LocalDate.of(2026, 9, 28), null));

        // El recuadro de la hoja termina en 548 pt.
        try (PDDocument d = Loader.loadPDF(pdf)) {
            List<Float> derechas = new ArrayList<>();
            new PDFTextStripper() {
                @Override
                protected void writeString(String text, List<TextPosition> posiciones) {
                    posiciones.forEach(p -> derechas.add(p.getXDirAdj() + p.getWidthDirAdj()));
                }
            }.getText(d);
            assertThat(derechas).allSatisfy(x -> assertThat(x).isLessThanOrEqualTo(549f));
        }
        assertThat(texto(pdf).replaceAll("\\s+", "")).contains("ISMODAL=FALSE");
    }

    @Test
    void unaTablaQueSigueEnOtraPaginaRepiteElEncabezado() throws Exception {
        List<Fila> filas = new ArrayList<>();
        filas.add(Fila.de(Celda.encabezado("OBLIGACIONES"), Celda.encabezado("EVIDENCIA")));
        IntStream.rangeClosed(1, 80).forEach(i -> filas.add(Fila.de(Celda.de("Obligación " + i), Celda.de("Soporte"))));
        byte[] pdf = generar(List.of(new Tabla(List.of(235f, 235f), filas, 1, false, 5.4f)));

        String t = texto(pdf);
        assertThat(paginas(pdf)).isGreaterThan(1);
        assertThat(t.split("OBLIGACIONES", -1).length - 1).isEqualTo(paginas(pdf));
        assertThat(t).contains("Obligación 80");
    }

    // ── Entradas degeneradas: el documento sale pobre, pero sale ────────────

    @Test
    void soportaUnDocumentoSinBloques() {
        assertThatCode(() -> assertThat(paginas(generar(List.of()))).isEqualTo(1)).doesNotThrowAnyException();
    }

    @Test
    void soportaTextosVaciosYNulos() {
        assertThatCode(() -> generar(List.of(new Parrafo(""), new Parrafo("   "),
                new Ficha(List.of(new Campo("CAMPO", null))), new Parrafo("Contenido real."))))
                .doesNotThrowAnyException();
    }

    /**
     * Con Carlito incrustado salen tal cual las tildes, eñes, diéresis, la raya
     * del título del GCCON-F-031 y las comillas tipográficas, que la Helvetica
     * estándar cambiaba por «-» y «"».
     */
    @Test
    void escribeElEspanolYLaTipografiaDeLosFormatosSinSustituir() throws Exception {
        String t = texto(generar(List.of(new Parrafo(
                "El señor Muñoz verificó la ejecución con antigüedad suficiente – “bienes” € ≥"))));

        assertThat(t).contains("Muñoz").contains("ejecución").contains("antigüedad").contains("–")
                .contains("“bienes”").contains("€");
    }

    /**
     * Carlito une «ti», «fi», «ft» en un solo glifo, y PDFBox 3 aplicaba esas
     * ligaduras por su cuenta: el PDF se veía bien pero al extraer el texto
     * «Antioquia» salía «-oquia» y «septiembre» «embre». La capa de texto es
     * lo que usan el buscador del lector, el copiar y pegar, el SECOP y la
     * propia extracción de SICOT.
     */
    @Test
    void laCapaDeTextoConservaLasPalabrasQueLaFuenteUniriaConLigaduras() throws Exception {
        String frase = "En Itagüí- Antioquia el día 28 de septiembre de 2026, identificado con cedula de ciudadanía,"
                + " en sus respectivas cantidades, ficha técnica y efectivo cumplimiento de las obligaciones";
        String t = texto(generar(List.of(new Parrafo(frase), new Ficha(List.of(new Campo("OBJETO", frase))))))
                .replaceAll("\\s+", " ");

        assertThat(t.split(java.util.regex.Pattern.quote(frase), -1).length - 1).isEqualTo(2);
    }

    /**
     * Lo que ninguna fuente tiene (un emoji que devolvió el modelo) se
     * sustituye; los invisibles (ancho cero, BOM) desaparecen, y un tabulador
     * pegado de Excel pasa a espacio. Nada de eso hace fallar el documento.
     */
    @Test
    void loQueLaFuenteNoTieneSeSustituyeSinLanzar() throws Exception {
        byte[] pdf = generar(List.of(new Parrafo("Listo ✅ sin error ❌ ancho​cero ﻿bom\tcon\ttabs 😀")));

        String t = texto(pdf);
        assertThat(t).contains("[OK]").contains("[X]").contains("anchocero").contains("con tabs");
    }

    // ── Hallazgos de la revisión del generador del 29-09-2026 ───────────────

    /**
     * «$139.400.634,0» en un renglón y «0» en el siguiente, en la columna de
     * valores del GCCON-F-031: quien copiaba la cifra del informe que autoriza
     * el pago obtenía otro número.
     */
    @Test
    void unaCifraEnUnaColumnaEstrechaNoSeParte() throws Exception {
        byte[] pdf = generar(List.of(new Tabla(List.of(76f, 394f), List.of(
                Fila.de(Celda.de("$139.400.634,00"), Celda.de("facturado")),
                Fila.de(Celda.de("$ 1.234.567.890,00"), Celda.de("ejecutado")),
                Fila.de(Celda.de("55,76 %"), Celda.de("avance"))), 0, false, 5.4f)));

        String t = texto(pdf);
        assertThat(t).contains("$139.400.634,00").contains("$ 1.234.567.890,00").contains("55,76 %");
        assertThat(maximaAlturaYAnchura(pdf)).allSatisfy(dentro -> assertThat(dentro).isTrue());
    }

    /** «A-02-02-01-003-0» / «08-01» ya no es el código; «A-02-02-01-003-» / «008-01» sí se lee como tal. */
    @Test
    void unCodigoLargoSeParteDespuesDeUnGuion() throws Exception {
        byte[] pdf = generar(List.of(new Tabla(List.of(62f, 408f),
                List.of(Fila.de(Celda.de("A-02-02-01-003-008-01"), Celda.de("uso"))), 0, false, 5.4f)));

        List<String> renglones = texto(pdf).lines().filter(l -> l.startsWith("A-02") || l.matches("^\\d.*-\\d\\d.*"))
                .toList();
        assertThat(renglones.getFirst()).endsWith("-");
        assertThat(texto(pdf).replaceAll("\\s+", "")).contains("A-02-02-01-003-008-01");
    }

    /**
     * El bloque de firmas reservaba sitio por renglones lógicos: una razón
     * social, un nombre o un correo que se partían en dos renglones salían por
     * debajo del cuerpo, encima del pie. Se prueba con el bloque en todas las
     * alturas de la página.
     */
    @Test
    void elBloqueDeFirmasNoPasaDelFinDelCuerpoAunqueLosNombresOcupenDosRenglones() throws Exception {
        float finCuerpo = INFORME.formato().pagina().finCuerpo();
        BloqueDocumento.Firmas firmas = new BloqueDocumento.Firmas(List.of(
                new BloqueDocumento.Firmante("MARÍA ALEJANDRA GUTIÉRREZ CASTAÑEDA DE RESTREPO VILLEGAS", true,
                        List.of("Supervisor del contrato", "correo.muy.largo.de.la.supervisora@sena.edu.co")),
                new BloqueDocumento.Firmante("UNIÓN TEMPORAL MOBILIARIO ESCOLAR ANTIOQUIA 2026", true,
                        List.of("Contratista", "contratacion@uniontemporalmobiliarioescolar.com.co"))),
                BloqueDocumento.DisposicionFirmas.TABLA, 42f);
        for (int relleno = 0; relleno < 60; relleno++) {
            List<BloqueDocumento> bloques = new ArrayList<>(parrafos(relleno));
            bloques.add(firmas);
            byte[] pdf = generar(bloques);
            try (PDDocument d = Loader.loadPDF(pdf)) {
                List<Float> bajos = new ArrayList<>();
                new PDFTextStripper() {
                    @Override
                    protected void writeString(String text, List<TextPosition> posiciones) {
                        if (text.contains("Supervisor del contrato") || text.contains("Contratista")
                                || text.contains("@")) {
                            posiciones.forEach(p -> bajos.add(p.getYDirAdj()));
                        }
                    }
                }.getText(d);
                assertThat(bajos).as("relleno " + relleno)
                        .allSatisfy(yy -> assertThat(yy).isLessThanOrEqualTo(finCuerpo + 1f));
            }
        }
    }

    /**
     * La hoja GIL-F-010: en la casilla estrecha de la cantidad, el aviso de
     * continuación no cabía al lado del texto y la cantidad salía espaciada
     * letra por letra («2 6 re s m a s»). La hoja de continuación, además,
     * decía «Casilla de la fila 30» para las dos cantidades.
     */
    @Test
    void enLaHojaDeReciboUnaCantidadLargaNoSeDesarmaYSuContinuacionDiceQueEs() throws Exception {
        PlantillaDocumentoIA recibo = PlantillaDocumentoIA.CATALOGO.get("ACTA_RECIBO");
        String consumo = "26 resmas de papel carta, 10 cajas de ganchos, 40 carpetas de yute y 12 marcadores";
        String devolutivos = "12 sillas ergonómicas con brazos, 4 mesas plegables y 2 tableros acrílicos móviles";
        BloqueDocumento.DatosActaDeRecibo datos = new BloqueDocumento.DatosActaDeRecibo("[dato pendiente]",
                "28/09/2026", "Itagüí", "5", "Antioquia", "Centro", "920510", "Compra", "Total", "CO1.PCCNTR.1",
                "17/06/2025", "C-3603", "Proveedor", "900", "$1", "17/12/2025", "Objeto", devolutivos, consumo, null,
                "Paola", "43", "p@sena.edu.co", "Instructora", "300");
        byte[] pdf = generar(recibo, List.of(new BloqueDocumento.HojaDeRecibo(datos)));

        String t = texto(pdf);
        assertThat(t).doesNotContain("2 6 ").doesNotContain("1 2 s");
        assertThat(t.replaceAll("\\s+", " ")).contains("CANTIDAD BIENES DE CONSUMO " + consumo)
                .contains("CANTIDAD BIENES DEVOLUTIVOS " + devolutivos)
                .doesNotContain("Casilla de la fila");
        // El marcador del número de acta sale en rojo, como todos los pendientes.
        assertThat(colorDe(pdf, "[dato")).isEqualTo(0xC00000);
    }

    @Test
    void unNumeroDeActaLargoNoSeSaleDeLaHoja() throws Exception {
        PlantillaDocumentoIA recibo = PlantillaDocumentoIA.CATALOGO.get("ACTA_RECIBO");
        String numero = "ACTA DE RECIBO 001-2026 ALMACÉN CENTRO TECNOLÓGICO DEL MOBILIARIO ITAGÜÍ ANTIOQUIA";
        BloqueDocumento.DatosActaDeRecibo datos = new BloqueDocumento.DatosActaDeRecibo(numero, "28/09/2026",
                "Itagüí", "5", "Antioquia", "Centro", "920510", "Compra", "Total", "CO1.PCCNTR.1", "17/06/2025",
                "C-3603", "Proveedor", "900", "$1", "17/12/2025", "Objeto", "1", "1", null, "Paola", "43",
                "p@sena.edu.co", "Instructora", "300");
        byte[] pdf = generar(recibo, List.of(new BloqueDocumento.HojaDeRecibo(datos)));

        assertThat(maximaAlturaYAnchura(pdf)).allSatisfy(dentro -> assertThat(dentro).isTrue());
        assertThat(texto(pdf).replaceAll("\\s+", " ")).contains(numero);
    }

    // ── Utilidades ──────────────────────────────────────────────────────────

    /** El color de relleno (RGB, sin alfa) con que se dibujó la primera palabra que empieza por el texto. */
    private static int colorDe(byte[] pdf, String buscado) throws IOException {
        java.util.Map<TextPosition, Integer> colores = new java.util.IdentityHashMap<>();
        int[] color = {-1};
        try (PDDocument d = Loader.loadPDF(pdf)) {
            PDFTextStripper s = new PDFTextStripper() {
                {
                    // El extractor de texto no sigue los colores si no se le pide.
                    addOperator(new SetNonStrokingColorSpace(this));
                    addOperator(new SetNonStrokingDeviceRGBColor(this));
                    addOperator(new SetNonStrokingColor(this));
                    addOperator(new SetNonStrokingColorN(this));
                }

                @Override
                protected void processTextPosition(TextPosition text) {
                    try {
                        colores.put(text, getGraphicsState().getNonStrokingColor().toRGB());
                    } catch (IOException e) {
                        colores.put(text, -1);
                    }
                    super.processTextPosition(text);
                }

                @Override
                protected void writeString(String text, List<TextPosition> posiciones) throws IOException {
                    if (color[0] == -1 && text.startsWith(buscado) && !posiciones.isEmpty()) {
                        color[0] = colores.getOrDefault(posiciones.getFirst(), -1);
                    }
                    super.writeString(text, posiciones);
                }
            };
            s.getText(d);
        }
        return color[0];
    }

    private static List<BloqueDocumento> parrafos(int n) {
        return IntStream.rangeClosed(1, n)
                .<BloqueDocumento>mapToObj(i -> new Parrafo("Párrafo número " + i + " del informe de supervisión."))
                .toList();
    }

    /** Por cada carácter dibujado: ¿queda dentro de la hoja? */
    private static List<Boolean> maximaAlturaYAnchura(byte[] pdf) throws IOException {
        List<Boolean> dentro = new ArrayList<>();
        try (PDDocument d = Loader.loadPDF(pdf)) {
            PDFTextStripper s = new PDFTextStripper() {
                @Override
                protected void writeString(String text, List<TextPosition> posiciones) {
                    for (TextPosition p : posiciones) {
                        float x = p.getXDirAdj() + p.getWidthDirAdj();
                        float y = p.getYDirAdj();
                        dentro.add(x <= 612 - 20 && y >= 0 && y <= 792 - 10);
                    }
                }
            };
            s.getText(d);
        }
        return dentro;
    }
}
