package co.sena.sicot.ia;

import co.sena.sicot.dto.etapa.EtapaResponse;
import co.sena.sicot.dto.etapa.SubetapaResponse;
import co.sena.sicot.entity.enums.EstadoEtapa;
import co.sena.sicot.entity.enums.EstadoSubetapa;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Las respuestas de los casos son las que dio qwen2.5:7b en vivo el 18-10-2026
 * contra el contrato CO1.PCCNTR.10057820, copiadas tal cual.
 */
class CorrectorDeCargasTest {

    /** Los sub-pasos del proceso real (V20) que importan aquí, con sus nombres de verdad. */
    private static final List<EtapaResponse> ETAPAS = List.of(
            new EtapaResponse(2L, 2, "INICIO — Acta de Inicio y arranque (GCCON-F-018)", EstadoEtapa.EN_CURSO, 0, List.of(
                    new SubetapaResponse(21L, "2.1", "Firma y devolución del Acta de Inicio (GCCON-F-018)", null,
                            EstadoSubetapa.EN_CURSO, "Supervisor", FichaDeDocumentoFormal.ACTA_FIRMADA_Y_ENVIO),
                    new SubetapaResponse(24L, "2.4", "Contacto con el contratista y acuerdo de entregas", null,
                            EstadoSubetapa.PENDIENTE, "Supervisor"))),
            new EtapaResponse(3L, 3, "INSPECCIÓN", EstadoEtapa.PENDIENTE, 0, List.of(
                    new SubetapaResponse(31L, "3.1", "Verificación física de la entrega en bodega", null,
                            EstadoSubetapa.PENDIENTE, "Supervisor"),
                    new SubetapaResponse(34L, "3.4", "Informe de Supervisión (GCCON-F-031)", null,
                            EstadoSubetapa.PENDIENTE, "Supervisor"))),
            new EtapaResponse(4L, 4, "RECEPCIÓN", EstadoEtapa.PENDIENTE, 0, List.of(
                    new SubetapaResponse(41L, "4.1", "Verificación de aportes a seguridad social (PILA)", null,
                            EstadoSubetapa.PENDIENTE, "Supervisor"),
                    new SubetapaResponse(42L, "4.2", "Verificación de factura electrónica DIAN (FEV)", null,
                            EstadoSubetapa.PENDIENTE, "Supervisor"))),
            new EtapaResponse(5L, 5, "CERTIFICACIÓN", EstadoEtapa.PENDIENTE, 0, List.of(
                    new SubetapaResponse(51L, "5.1", "Verificación de vigencia de garantías", null,
                            EstadoSubetapa.PENDIENTE, "Supervisor"))));

    @Test
    void quitaElEnvieLaFacturaASicotYDiceComoSeRegistraEl42() {
        String delModelo = "Verifique la factura electrónica en el portal de la DIAN para asegurarse de que esté "
                + "correctamente emitida y que coincida con los datos del contrato. Luego, envíe la factura a SICOT y "
                + "pulse «Marcar completado» en el sub-paso correspondiente.";

        String corregida = CorrectorDeCargas.corregir(delModelo,
                "¿Qué debo revisar al recibir la factura electrónica del contratista?", ETAPAS);

        assertThat(corregida)
                .startsWith("Verifique la factura electrónica en el portal de la DIAN")
                .doesNotContain("envíe la factura a SICOT")
                .contains("En el 4.2 (Verificación de factura electrónica DIAN (FEV)) SICOT no recibe ningún archivo.")
                .contains("márquelo como completado");
    }

    @Test
    void quitaElCargueLaEvidenciaConCargarEnLasPolizas() {
        String delModelo = "Verifique las pólizas de seguro del contratista en la página web de la aseguradora o en "
                + "SECOP II. Una vez verificado que estén vigentes, cargue la evidencia en SICOT pulsando «Cargar» y "
                + "luego marque el sub-paso como completado.";

        String corregida = CorrectorDeCargas.corregir(delModelo, "¿Dónde reviso que las pólizas estén vigentes?", ETAPAS);

        assertThat(corregida)
                .startsWith("Verifique las pólizas de seguro del contratista en la página web de la aseguradora")
                .doesNotContain("«Cargar»")
                .contains("En el 5.1 (Verificación de vigencia de garantías) SICOT no recibe ningún archivo.");
    }

    @Test
    void unaRespuestaQueYaEsCorrectaNoSeToca() {
        String delModelo = "Verifique la planilla PILA del contratista con el operador de seguridad social. Una vez "
                + "verificado, pulse «Marcar completado» en el 4.1.";

        assertThat(CorrectorDeCargas.corregir(delModelo, "¿Cómo verifico la planilla PILA del contratista?", ETAPAS))
                .isEqualTo(delModelo);
    }

    @Test
    void elRecordatorioDelActaEnEl21SeDeja() {
        // Medido el mismo día: el modelo recordó el paso en curso. Es correcto
        // —el acta sí se carga, en el 2.1— y no es lo que el corrector vigila.
        String delModelo = "Verifique la factura en la DIAN. Mientras tanto, cargue en SICOT el acta firmada y la "
                + "evidencia del envío en el sub-paso 2.1.";

        assertThat(CorrectorDeCargas.corregir(delModelo, "¿qué reviso de la factura?", ETAPAS)).isEqualTo(delModelo);
    }

    @Test
    void unaNegacionSeDeja() {
        String delModelo = "Revise la factura en la DIAN. No tiene que cargar nada en SICOT: solo márquelo como "
                + "completado.";

        assertThat(CorrectorDeCargas.corregir(delModelo, "¿y la factura la subo?", ETAPAS)).isEqualTo(delModelo);
    }

    @Test
    void siLaPreguntaNoEsDeUnInsumoQueSeVerificaAfueraNoSeToca() {
        // Las fotos de la entrega sí se cargan: aquí «cargue … en SICOT» es la
        // instrucción correcta, y la pregunta no nombra factura, PILA ni pólizas.
        String delModelo = "Tome la foto de la entrega y cárguela en SICOT con «Cargar evidencia».";

        assertThat(CorrectorDeCargas.corregir(delModelo, "¿Cómo dejo constancia de la entrega?", ETAPAS))
                .isEqualTo(delModelo);
    }

    @Test
    void siElContratoNoTieneEseSubpasoNoSeInventaUno() {
        String delModelo = "Revise la factura y súbala a SICOT.";
        List<EtapaResponse> soloElInicio = List.of(ETAPAS.get(0));

        assertThat(CorrectorDeCargas.corregir(delModelo, "¿qué hago con la factura?", soloElInicio))
                .isEqualTo(delModelo);
    }

    @Test
    void siLaRespuestaEraSoloLaCargaQuedaLaIndicacionCorrecta() {
        String corregida = CorrectorDeCargas.corregir("Adjunte la factura en SICOT.", "¿dónde va la factura?", ETAPAS);

        assertThat(corregida).startsWith("En el 4.2 (Verificación de factura electrónica DIAN (FEV)) SICOT no recibe");
    }

    @Test
    void conservaLosSaltosDeLineaDeLaRespuesta() {
        String delModelo = "1. Revise la factura en la DIAN.\n2. Compare el valor con el contrato.\n3. Súbala a SICOT.";

        String corregida = CorrectorDeCargas.corregir(delModelo, "pasos para revisar la factura", ETAPAS);

        assertThat(corregida)
                .startsWith("1. Revise la factura en la DIAN.\n2. Compare el valor con el contrato.\n")
                .doesNotContain("Súbala");
    }
}
