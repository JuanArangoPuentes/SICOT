package co.sena.sicot.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El nombre con que baja un archivo de SICOT. Cada caso es un archivo real que
 * bajó mal antes del 28-09-2026.
 */
class NombreDeDescargaTest {

    @Test
    void unDocumentoGeneradoSinExtensionBajaComoPdf() {
        assertThat(NombreDeDescarga.nombre("Acta de Inicio — CO1.PCCNTR.7788991", "application/pdf"))
                .isEqualTo("Acta de Inicio — CO1.PCCNTR.7788991.pdf");
    }

    /** La foto bajaba como «…jpg.pdf» y el lector de PDF la daba por dañada. */
    @Test
    void unaFotoConservaSuExtensionYNoLlevaPdf() {
        assertThat(NombreDeDescarga.nombre("Evidencia fotográfica 3.2 — 1000000021.jpg", "image/jpeg"))
                .isEqualTo("Evidencia fotográfica 3.2 — 1000000021.jpg");
        assertThat(NombreDeDescarga.nombre("foto.JPEG", "image/jpeg")).isEqualTo("foto.JPEG");
        assertThat(NombreDeDescarga.nombre("Acta.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document")).isEqualTo("Acta.docx");
    }

    @Test
    void losCaracteresQueWindowsNoAdmiteSeCambian() {
        assertThat(NombreDeDescarga.nombre("Acta CTMA-045/2026: final?", "application/pdf"))
                .isEqualTo("Acta CTMA-045_2026_ final_.pdf");
        assertThat(NombreDeDescarga.nombre("termina en punto.", null)).isEqualTo("termina en punto");
    }

    /**
     * {@code filename=} lleva ASCII legible y {@code filename*=} el nombre
     * completo: antes {@code curl -OJ} guardaba «=_UTF-8_Q_Acta…».
     */
    @Test
    void laCabeceraLlevaUnaVersionAsciiYElNombreCompletoEnUtf8() {
        String cabecera = NombreDeDescarga.cabecera("Evidencia fotográfica — 1.jpg", "image/jpeg");

        assertThat(cabecera)
                .startsWith("attachment; filename=\"Evidencia fotografica - 1.jpg\"; filename*=UTF-8''")
                .contains("fotogr%C3%A1fica%20%E2%80%94%201.jpg")
                .doesNotContain("=?UTF-8?");
    }
}
