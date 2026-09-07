package co.sena.sicot.ia;

import co.sena.sicot.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * La primera pieza por la que pasa un documento subido por Gestión: convierte
 * los bytes de un PDF en el texto que después se le da al modelo.
 *
 * <p>Es la clase más pequeña del paquete y por eso mismo conviene fijarla: todo
 * lo que hay aguas abajo —la extracción de campos, el prompt, la propuesta que
 * ve el funcionario— parte de lo que devuelva este método. Si aquí un archivo
 * corrupto se convirtiera en una excepción que nadie espera, el fallo aparecería
 * como un 500 opaco a mitad de la carga en vez de como un mensaje que el usuario
 * puede entender.
 */
class PdfTextExtractorTest {

    private final PdfTextExtractor extractor = new PdfTextExtractor();
    private final SimplePdfWriter escritor = new SimplePdfWriter();

    @Test
    void extraeElTextoDeUnPdfRealGeneradoPorElPropioSistema() {
        byte[] pdf = escritor.generar(
                "Acta de Inicio", "GCCON-F-018", "CO1.PCCNTR.7986334",
                "Alex Zapata", "Supervisor del contrato",
                List.of("El presente documento deja constancia del inicio de la ejecucion."));

        String texto = extractor.extraerTexto(pdf);

        assertThat(texto)
                .contains("Acta de Inicio")
                .contains("CO1.PCCNTR.7986334")
                .contains("deja constancia del inicio");
    }

    /**
     * El caso del ejecutable renombrado ya lo cubre {@code ArchivoValidator} por
     * bytes mágicos antes de llegar aquí. Esta prueba cubre el escalón siguiente:
     * un archivo que sí pasa por PDFBox pero está truncado o corrupto.
     */
    @Test
    void bytesQueNoSonUnPdfSalenComoErrorDeNegocioYNoComoFalloTecnico() {
        byte[] basura = "esto no es un PDF, es texto plano".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> extractor.extraerTexto(basura))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("No se pudo leer el contenido del PDF");
    }

    @Test
    void unPdfTruncadoAMitadTampocoRompeElContratoDeErrores() {
        byte[] completo = escritor.generar("Informe", List.of("contenido"));
        byte[] truncado = new byte[completo.length / 2];
        System.arraycopy(completo, 0, truncado, 0, truncado.length);

        assertThatThrownBy(() -> extractor.extraerTexto(truncado))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void unArchivoVacioNoSeConfundeConUnPdfSinTexto() {
        assertThatThrownBy(() -> extractor.extraerTexto(new byte[0]))
                .isInstanceOf(BusinessException.class);
    }
}
