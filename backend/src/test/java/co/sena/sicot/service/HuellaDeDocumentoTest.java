package co.sena.sicot.service;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class HuellaDeDocumentoTest {

    /**
     * Vector conocido de SHA-256. Si alguien cambiara el algoritmo o el formato
     * de salida (Base64, mayúsculas), esta prueba lo detecta: las huellas ya
     * guardadas dejarían de verificar en silencio y todos los documentos
     * firmados aparecerían como alterados.
     */
    @Test
    void calculaElSha256EnHexadecimalMinusculas() {
        String huella = HuellaDeDocumento.calcular("abc".getBytes(StandardCharsets.UTF_8));

        assertThat(huella).isEqualTo(
                "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    @Test
    void laHuellaDeUnContenidoVacioNoEsNula() {
        assertThat(HuellaDeDocumento.calcular(new byte[0]))
                .isEqualTo("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
    }

    @Test
    void sinContenidoNoHayHuella() {
        assertThat(HuellaDeDocumento.calcular(null)).isNull();
    }

    /**
     * El caso que da sentido a toda la columna: un solo byte distinto tiene que
     * producir una huella distinta.
     */
    @Test
    void unCambioDeUnSoloByteCambiaLaHuella() {
        String original = HuellaDeDocumento.calcular("Acta de inicio: valor 184.500.000".getBytes(StandardCharsets.UTF_8));
        String alterado = HuellaDeDocumento.calcular("Acta de inicio: valor 184.500.001".getBytes(StandardCharsets.UTF_8));

        assertThat(original).isNotEqualTo(alterado);
    }

    @Test
    void coincideSoloCuandoLasDosHuellasSonIguales() {
        String huella = HuellaDeDocumento.calcular("contenido".getBytes(StandardCharsets.UTF_8));

        assertThat(HuellaDeDocumento.coincide(huella, huella)).isTrue();
        assertThat(HuellaDeDocumento.coincide(huella, huella.replace('a', 'b'))).isFalse();
    }

    /**
     * Un documento sin huella registrada (firmado antes de que existiera la
     * columna) nunca debe reportarse como íntegro: eso afirmaría algo que el
     * sistema no puede saber.
     */
    @Test
    void unaHuellaAusenteNuncaCoincide() {
        String huella = HuellaDeDocumento.calcular("x".getBytes(StandardCharsets.UTF_8));

        assertThat(HuellaDeDocumento.coincide(null, huella)).isFalse();
        assertThat(HuellaDeDocumento.coincide(huella, null)).isFalse();
        assertThat(HuellaDeDocumento.coincide(null, null)).isFalse();
    }
}
