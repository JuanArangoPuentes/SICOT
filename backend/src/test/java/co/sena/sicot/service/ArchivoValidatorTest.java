package co.sena.sicot.service;

import co.sena.sicot.entity.enums.TipoDocumento;
import co.sena.sicot.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

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
