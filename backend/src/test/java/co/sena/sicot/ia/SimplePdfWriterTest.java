package co.sena.sicot.ia;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * El generador de los PDF que el Copiloto redacta y el supervisor firma.
 *
 * <p>Era la clase con más ramas sin cubrir de todo el backend: 68 sin ejecutar
 * por ninguna prueba. Eso importa más aquí que en otros sitios, porque su
 * salida es un <b>documento oficial que alguien va a firmar</b>. Un PDF con el
 * texto cortado, sin el bloque de firma o con el título perdido no rompe nada
 * visiblemente — se firma igual, y el defecto queda dentro de un expediente.
 *
 * <p>Las pruebas no comparan bytes: generan el PDF y vuelven a extraer su texto
 * con PDFBox, que es lo más cerca que se puede estar de comprobar lo que una
 * persona leería al abrirlo.
 */
class SimplePdfWriterTest {

    private final SimplePdfWriter escritor = new SimplePdfWriter();

    private String textoDe(byte[] pdf) throws IOException {
        try (PDDocument documento = Loader.loadPDF(pdf)) {
            return new PDFTextStripper().getText(documento);
        }
    }

    private int paginasDe(byte[] pdf) throws IOException {
        try (PDDocument documento = Loader.loadPDF(pdf)) {
            return documento.getNumberOfPages();
        }
    }

    @Test
    void generaUnPdfValidoConElTituloYElCuerpo() throws Exception {
        byte[] pdf = escritor.generar(
                "Acta de Inicio",
                List.of("El presente documento deja constancia del inicio del contrato."));

        assertThat(pdf).isNotEmpty();
        assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");

        String texto = textoDe(pdf);
        assertThat(texto).contains("Acta de Inicio").contains("deja constancia del inicio");
    }

    @Test
    void incluyeElCodigoDeFormatoYElNumeroDeContrato() throws Exception {
        byte[] pdf = escritor.generar(
                "Informe de Supervisión", "GCCON-F-031", "CTMA-2026-0184",
                "Alex Fernando Zapata", "Supervisor del contrato",
                List.of("Se verificó el avance de la ejecución."));

        String texto = textoDe(pdf);
        assertThat(texto).contains("GCCON-F-031").contains("CTMA-2026-0184");
    }

    /**
     * El bloque de firma es la razón de ser del documento: sin él, lo que se
     * genera es una nota, no un formato suscribible.
     */
    @Test
    void incluyeElBloqueDeFirmaConNombreYCargo() throws Exception {
        byte[] pdf = escritor.generar(
                "Acta de Recibo", "GIL-F-010", "CTMA-2026-0184",
                "Alex Fernando Zapata", "Supervisor del contrato",
                List.of("Se recibe a satisfacción."));

        String texto = textoDe(pdf);
        assertThat(texto).contains("Alex Fernando Zapata").contains("Supervisor del contrato");
    }

    /**
     * Un párrafo más largo que el ancho útil tiene que partirse en varias
     * líneas. Si no, PDFBox lo escribe recto y el texto se sale de la página:
     * el PDF abre sin error y la mitad del contenido es invisible.
     */
    @Test
    void parteEnVariasLineasUnParrafoLargo() throws Exception {
        String largo = "El contratista entregó la totalidad de los bienes descritos en el "
                + "anexo técnico, incluyendo mesas, sillas y archivadores metálicos, "
                + "verificados uno a uno contra las especificaciones acordadas en el "
                + "estudio previo y la propuesta económica presentada durante el proceso.";

        byte[] pdf = escritor.generar("Acta", List.of(largo));

        String texto = textoDe(pdf);
        // El texto completo debe seguir presente aunque se haya repartido.
        assertThat(texto.replaceAll("\\s+", " ")).contains("archivadores metálicos");
        assertThat(texto.lines().filter(l -> !l.isBlank()).count()).isGreaterThan(3);
    }

    /** Con suficiente contenido tiene que abrir una segunda página, no recortar. */
    @Test
    void abreMasPaginasCuandoElContenidoNoCabe() throws Exception {
        List<String> muchos = IntStream.rangeClosed(1, 90)
                .mapToObj(i -> "Párrafo número " + i + " del informe de supervisión del contrato.")
                .toList();

        byte[] pdf = escritor.generar("Informe extenso", muchos);

        assertThat(paginasDe(pdf)).isGreaterThan(1);
        assertThat(textoDe(pdf)).contains("Párrafo número 90");
    }

    @Test
    void numeraLasPaginasCuandoHayVarias() throws Exception {
        List<String> muchos = IntStream.rangeClosed(1, 90)
                .mapToObj(i -> "Contenido de relleno número " + i + " para forzar el salto de página.")
                .toList();

        String texto = textoDe(escritor.generar("Informe extenso", muchos));

        assertThat(texto).containsPattern("1\\s*(de|/)\\s*\\d");
    }

    // ── Entradas degeneradas ────────────────────────────────────────────────
    // El Copiloto es quien alimenta a esta clase, y un modelo puede devolver
    // vacío, una sola palabra o un texto sin espacios. Ninguno de esos casos
    // debe producir una excepción: el documento saldrá pobre, pero saldrá.

    @Test
    void soportaUnDocumentoSinParrafos() {
        assertThatCode(() -> {
            byte[] pdf = escritor.generar("Título solo", List.of());
            assertThat(paginasDe(pdf)).isEqualTo(1);
        }).doesNotThrowAnyException();
    }

    @Test
    void soportaParrafosVaciosOEnBlanco() {
        assertThatCode(() -> {
            byte[] pdf = escritor.generar("Con huecos", List.of("", "   ", "Contenido real."));
            assertThat(textoDe(pdf)).contains("Contenido real.");
        }).doesNotThrowAnyException();
    }

    /**
     * Una "palabra" más ancha que la página (una URL larga, una cadena sin
     * espacios) no se puede partir por espacios. Debe salir igualmente, no
     * lanzar ni entrar en un bucle infinito buscando dónde cortar.
     */
    @Test
    void soportaUnaPalabraMasAnchaQueLaPagina() {
        String interminable = "A".repeat(400);

        assertThatCode(() -> {
            byte[] pdf = escritor.generar("Sin espacios", List.of(interminable));
            assertThat(pdf).isNotEmpty();
        }).doesNotThrowAnyException();
    }

    @Test
    void soportaQueFalteElCodigoOElNumeroDeContrato() {
        assertThatCode(() -> {
            byte[] pdf = escritor.generar("Sin metadatos", null, null,
                    "Alex Fernando Zapata", "Supervisor", List.of("Cuerpo del documento."));
            assertThat(textoDe(pdf)).contains("Cuerpo del documento.");
        }).doesNotThrowAnyException();
    }

    @Test
    void soportaQueFalteElFirmante() {
        assertThatCode(() -> {
            byte[] pdf = escritor.generar("Sin firmante", "GCCON-F-031", "CTMA-2026-0184",
                    null, null, List.of("Cuerpo del documento."));
            assertThat(pdf).isNotEmpty();
        }).doesNotThrowAnyException();
    }

    /**
     * El español lleva tildes, eñes y diéresis. La fuente Helvetica estándar de
     * PDF no cubre todo Unicode, y un carácter fuera de su repertorio hace que
     * PDFBox lance al escribir — un fallo que aparecería en el primer documento
     * con la palabra "supervisión".
     */
    @Test
    void escribeAcentosEnesYDieresisSinLanzar() throws Exception {
        byte[] pdf = escritor.generar(
                "Certificación de cumplimiento",
                List.of("El señor Muñoz verificó la ejecución con antigüedad suficiente."));

        String texto = textoDe(pdf);
        assertThat(texto).contains("Muñoz").contains("ejecución").contains("antigüedad");
    }
}
