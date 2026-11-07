package co.sena.sicot.service;

import co.sena.sicot.ArchivoDePrueba;
import co.sena.sicot.entity.enums.TipoDocumento;
import co.sena.sicot.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * El validador es la puerta por la que entra todo archivo de fuera, así que lo
 * que se comprueba aquí no es que "acepte imágenes", sino que siga aceptándolas
 * <b>por sus bytes</b> y no por el nombre: la extensión la elige quien sube el
 * archivo.
 */
class ArchivoValidatorTest {

    private final ArchivoValidator validador = new ArchivoValidator();

    @Test
    @DisplayName("Una foto JPG del teléfono entra al expediente y se guarda como image/jpeg")
    void aceptaJpgEnElExpediente() throws IOException {
        MockMultipartFile foto = new MockMultipartFile(
                "archivo", "entrega-bodega.jpg", "image/jpeg", imagenReal("jpg"));

        ArchivoValidator.ArchivoAceptado aceptado =
                validador.aceptar(foto, ArchivoValidator.EVIDENCIAS_DEL_EXPEDIENTE);

        assertThat(aceptado.tipo()).isEqualTo(TipoDocumento.IMAGEN);
        assertThat(aceptado.contentType()).isEqualTo("image/jpeg");
    }

    /**
     * El MIME que se guarda sale de los bytes y no del tipo: si se devolviera un
     * único MIME canónico para IMAGEN, un PNG se descargaría diciendo que es
     * JPEG y algunos visores no lo abrirían.
     */
    @Test
    @DisplayName("Un PNG se guarda como image/png, no como el MIME de las demás imágenes")
    void distingueElPngDelJpg() throws IOException {
        MockMultipartFile foto = new MockMultipartFile(
                "archivo", "acta.png", "image/png", imagenReal("png"));

        ArchivoValidator.ArchivoAceptado aceptado =
                validador.aceptar(foto, ArchivoValidator.EVIDENCIAS_DEL_EXPEDIENTE);

        assertThat(aceptado.tipo()).isEqualTo(TipoDocumento.IMAGEN);
        assertThat(aceptado.contentType()).isEqualTo("image/png");
    }

    @Test
    @DisplayName("Un PDF renombrado a .jpg se rechaza: el contenido no coincide con la extensión")
    void rechazaUnPdfDisfrazadoDeFoto() {
        byte[] pdf = "%PDF-1.7\n1 0 obj\n<<>>\nendobj\ntrailer\n<<>>\n%%EOF".getBytes();
        MockMultipartFile disfrazado = new MockMultipartFile(
                "archivo", "entrega.jpg", "image/jpeg", pdf);

        assertThatThrownBy(() -> validador.aceptar(disfrazado, ArchivoValidator.EVIDENCIAS_DEL_EXPEDIENTE))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("no coincide con su extensión");
    }

    @Test
    @DisplayName("Un ejecutable renombrado a .png se rechaza")
    void rechazaUnEjecutableDisfrazadoDeFoto() {
        // Cabecera MZ: lo que Tika reconoce como ejecutable de Windows.
        byte[] ejecutable = new byte[] {0x4D, 0x5A, (byte) 0x90, 0x00, 0x03, 0x00, 0x00, 0x00};
        MockMultipartFile disfrazado = new MockMultipartFile(
                "archivo", "foto.png", "image/png", ejecutable);

        assertThatThrownBy(() -> validador.aceptar(disfrazado, ArchivoValidator.EVIDENCIAS_DEL_EXPEDIENTE))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("no coincide con su extensión");
    }

    /**
     * Una foto no es un formato documental: el catálogo guarda las plantillas
     * oficiales del SENA, y abrirlo a imágenes sería aceptar como plantilla algo
     * de lo que no se puede leer ni un campo.
     */
    @Test
    @DisplayName("El catálogo de formatos oficiales sigue rechazando fotos")
    void elCatalogoDeFormatosNoAceptaFotos() throws IOException {
        MockMultipartFile foto = new MockMultipartFile(
                "archivo", "plantilla.jpg", "image/jpeg", imagenReal("jpg"));

        assertThatThrownBy(() -> validador.aceptar(foto, ArchivoValidator.OFIMATICOS))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Solo se aceptan PDF, DOCX o XLSX");
    }

    /**
     * El archivo exacto que entró al expediente del contrato de prueba el
     * 15-10-2026: 44 bytes de texto cuya primera línea es «%PDF-1.4». Tika lo
     * detecta como application/pdf —decide por la cabecera—, SICOT lo guardaba
     * con un 201 y lo ofrecía para descargar, y el lector del supervisor lo
     * daba por dañado.
     */
    @Test
    @DisplayName("Un archivo con la cabecera %PDF pero sin estructura se rechaza: ningún lector lo abre")
    void rechazaUnPdfQueNingunLectorAbre() {
        byte[] soloLaCabecera = "%PDF-1.4\n% acta firmada de prueba E2E\n%%EOF\n".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile roto = new MockMultipartFile("archivo", "acta.pdf", "application/pdf", soloLaCabecera);

        assertThatThrownBy(() -> validador.aceptar(roto, ArchivoValidator.EVIDENCIAS_DEL_EXPEDIENTE))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("ningún lector puede abrirlo");
    }

    /** Un PDF cuya subida se cortó a mitad: la cabecera está, el resto no. */
    @Test
    @DisplayName("Un PDF truncado se rechaza aunque la cabecera esté completa")
    void rechazaUnPdfTruncado() {
        byte[] completo = ArchivoDePrueba.pdfDeUnaPagina("acta de inicio");
        byte[] aMedias = Arrays.copyOf(completo, completo.length / 2);
        MockMultipartFile cortado = new MockMultipartFile("archivo", "acta.pdf", "application/pdf", aMedias);

        assertThatThrownBy(() -> validador.aceptar(cortado, ArchivoValidator.EVIDENCIAS_DEL_EXPEDIENTE))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("ningún lector puede abrirlo");
    }

    @Test
    @DisplayName("Un PDF real de una página entra al expediente")
    void aceptaUnPdfReal() {
        MockMultipartFile acta = new MockMultipartFile(
                "archivo", "acta.pdf", "application/pdf", ArchivoDePrueba.pdfDeUnaPagina("acta de inicio"));

        ArchivoValidator.ArchivoAceptado aceptado =
                validador.aceptar(acta, ArchivoValidator.EVIDENCIAS_DEL_EXPEDIENTE);

        assertThat(aceptado.tipo()).isEqualTo(TipoDocumento.PDF);
        assertThat(aceptado.contentType()).isEqualTo("application/pdf");
    }

    /**
     * La foto de la entrega llega desde el teléfono por la red del Centro: una
     * subida cortada deja los bytes de cabecera y nada más. Guardarla sería
     * dejar en el expediente una evidencia que no se puede mirar.
     */
    @Test
    @DisplayName("Una foto truncada se rechaza: no se puede mostrar")
    void rechazaUnaFotoTruncada() throws IOException {
        byte[] completa = imagenReal("jpg");
        byte[] aMedias = Arrays.copyOf(completa, 16);
        MockMultipartFile cortada = new MockMultipartFile("archivo", "entrega.jpg", "image/jpeg", aMedias);

        assertThatThrownBy(() -> validador.aceptar(cortada, ArchivoValidator.EVIDENCIAS_DEL_EXPEDIENTE))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("no se puede mostrar");
    }

    @Test
    @DisplayName("El mensaje de error del expediente nombra las fotos entre lo aceptado")
    void elMensajeDelExpedienteNombraLasFotos() {
        MockMultipartFile texto = new MockMultipartFile(
                "archivo", "notas.txt", "text/plain", "hola".getBytes());

        assertThatThrownBy(() -> validador.aceptar(texto, ArchivoValidator.EVIDENCIAS_DEL_EXPEDIENTE))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("fotos JPG y PNG");
    }

    /** Imagen real, codificada por ImageIO: bytes de verdad, no una cabecera inventada. */
    private byte[] imagenReal(String formato) throws IOException {
        BufferedImage imagen = new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB);
        imagen.setRGB(0, 0, 0x00FF00);
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        ImageIO.write(imagen, formato, salida);
        return salida.toByteArray();
    }
}
