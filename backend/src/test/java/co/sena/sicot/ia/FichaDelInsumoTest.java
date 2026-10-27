package co.sena.sicot.ia;

import co.sena.sicot.dto.etapa.EtapaResponse;
import co.sena.sicot.dto.etapa.SubetapaResponse;
import co.sena.sicot.dto.ia.ChatResponse;
import co.sena.sicot.entity.enums.EstadoEtapa;
import co.sena.sicot.entity.enums.EstadoSubetapa;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FichaDelInsumoTest {

    /** Los sub-pasos del proceso real (V20), con el contrato en el 2.1 como el CO1.PCCNTR.10057820 del 18-10. */
    private static final List<EtapaResponse> ETAPAS = List.of(
            new EtapaResponse(2L, 2, "INICIO — Acta de Inicio y arranque (GCCON-F-018)", EstadoEtapa.EN_CURSO, 0, List.of(
                    new SubetapaResponse(21L, "2.1", "Firma y devolución del Acta de Inicio (GCCON-F-018)", null,
                            EstadoSubetapa.EN_CURSO, "Supervisor", FichaDeDocumentoFormal.ACTA_FIRMADA_Y_ENVIO))),
            new EtapaResponse(4L, 4, "RECEPCIÓN — Recibo a satisfacción", EstadoEtapa.PENDIENTE, 0, List.of(
                    new SubetapaResponse(41L, "4.1", "Verificación de aportes a seguridad social (PILA)", null,
                            EstadoSubetapa.PENDIENTE, "Supervisor"),
                    new SubetapaResponse(42L, "4.2", "Verificación de factura electrónica DIAN (FEV)", null,
                            EstadoSubetapa.PENDIENTE, "Supervisor"))),
            new EtapaResponse(5L, 5, "CERTIFICACIÓN — Cumplimiento y pago", EstadoEtapa.PENDIENTE, 0, List.of(
                    new SubetapaResponse(51L, "5.1", "Verificación de vigencia de garantías", null,
                            EstadoSubetapa.COMPLETADA, "Supervisor"))));

    @Test
    void laFacturaDiceDondeSeVerificaQueSubpasoEsYQueNoSeCarga() {
        ChatResponse r = FichaDelInsumo.responder(
                "¿Qué debo revisar al recibir la factura electrónica del contratista?", ETAPAS).orElseThrow();

        assertThat(r.respuesta()).isEqualTo("""
                La factura electrónica se verifica en el portal de la DIAN.

                Es el sub-paso 4.2: Verificación de factura electrónica DIAN (FEV), del paso 4: RECEPCIÓN — Recibo \
                a satisfacción, que en este contrato está pendiente.

                En ese sub-paso SICOT no recibe ningún archivo. En SICOT: cuando lo haya hecho, márquelo como \
                completado.

                Qué revisar en detalle no lo trae SICOT: si tiene dudas, confírmelo con la Unidad de Gestión \
                Contractual.""");
        assertThat(r.fuente()).isEqualTo(ChatResponse.Fuente.SISTEMA);
        assertThat(r.accion()).isNotNull();
    }

    @Test
    void lasPolizasVanAlSubpasoDeGarantiasYDicenSuEstado() {
        // Medido el 18-10: el modelo contestó esta pregunta sobre el 2.1 y el 2.4.
        String r = FichaDelInsumo.responder("¿Dónde reviso que las pólizas estén vigentes?", ETAPAS)
                .orElseThrow().respuesta();

        assertThat(r).startsWith("Las pólizas se consultan con la aseguradora o en el SECOP II.")
                .contains("Es el sub-paso 5.1: Verificación de vigencia de garantías")
                .contains("ya está completado")
                .doesNotContain("2.1");
    }

    @Test
    void laPilaNoMandaATramitesQueNoEstanEnElProceso() {
        // Medido el 18-10: «envíe la planilla a la Unidad de Gestión Contractual para revisión».
        String r = FichaDelInsumo.responder("¿Cómo verifico la planilla PILA del contratista y qué hago en SICOT?",
                ETAPAS).orElseThrow().respuesta();

        assertThat(r).startsWith("La planilla PILA la entrega el contratista, o se consulta en el operador de "
                        + "seguridad social.")
                .contains("Es el sub-paso 4.1")
                .doesNotContain("envíe");
    }

    @Test
    void elSecopUsaLaGuiaDel23YNoDiceQueFaltaElDetalle() {
        // Medido el 18-10: «verifique … enviando el número de contrato para
        // obtener la ficha técnica», que no es como se consulta el SECOP.
        // Con el 1.1 delante, como en el contrato real: su nombre también dice
        // «SECOP II», y en vivo la respuesta caía ahí.
        List<EtapaResponse> conEl23 = List.of(
                new EtapaResponse(1L, 1, "INICIO — Designación de la supervisión (GCCON-F-032)",
                        EstadoEtapa.COMPLETADA, 100, List.of(
                        new SubetapaResponse(11L, "1.1", "Suscripción y publicación del contrato en SECOP II", null,
                                EstadoSubetapa.COMPLETADA, "Unidad de Contratación"))),
                new EtapaResponse(2L, 2, "INICIO", EstadoEtapa.EN_CURSO, 0, List.of(
                        new SubetapaResponse(23L, "2.3", "Verificación de especificaciones en SECOP II", null,
                                EstadoSubetapa.PENDIENTE, "Supervisor"))));

        String r = FichaDelInsumo.responder(
                "¿Qué tengo que revisar en el SECOP II para verificar las especificaciones del contrato?", conEl23)
                .orElseThrow().respuesta();

        assertThat(r).startsWith("Las cantidades, las especificaciones y los valores del contrato se verifican en "
                        + "el SECOP II, entrando al proceso con su enlace.")
                .contains("Es el sub-paso 2.3")
                .contains("la oferta económica y el estudio previo")
                .doesNotContain("Qué revisar en detalle no lo trae SICOT");
    }

    @Test
    void lasPolizasEnElSecopSonDePolizas() {
        String r = FichaDelInsumo.responder("¿Dónde reviso las pólizas en el SECOP?", ETAPAS).orElseThrow().respuesta();

        assertThat(r).contains("Es el sub-paso 5.1");
    }

    @Test
    void elTrozoQueHagoEnSicotQuedaCubierto() {
        assertThat(FichaDelInsumo.cubre("qué hago en SICOT")).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "¿Qué hago si la póliza está vencida?",
            "La factura no coincide con el valor del contrato, ¿qué hago?",
            "¿Por qué piden la planilla PILA?",
            "¿Qué pasa con la factura cuando hay una adición?",
            "¿Qué es el GCCON-F-031?",
            "El contratista no ha respondido el correo para acordar las entregas, ¿qué hago?"})
    void losCasosConProblemaYLoQueNoEsDeInsumosSiguenSuCamino(String pregunta) {
        assertThat(FichaDelInsumo.responder(pregunta, ETAPAS)).isEmpty();
    }

    @Test
    void enUnContratoDelProcesoAnteriorNoCaeEnUnSubpasoQueSoloSeLlamaParecido() {
        // Nombres reales del contrato 6 de la base de desarrollo, de antes del
        // 15-10-2026: con «garantias» a secas la pregunta caía en el 1.4, que hace
        // Contratación. Sin el sub-paso del proceso real, va al modelo.
        List<EtapaResponse> anterior = List.of(new EtapaResponse(1L, 1, "INICIO", EstadoEtapa.COMPLETADA, 100, List.of(
                new SubetapaResponse(14L, "1.4", "Expedición del CDP y aprobación de garantías", null,
                        EstadoSubetapa.COMPLETADA, "Unidad de Contratación"),
                new SubetapaResponse(25L, "2.5", "Verificación de afiliaciones a seguridad social", null,
                        EstadoSubetapa.PENDIENTE, "Supervisor"))));

        assertThat(FichaDelInsumo.responder("¿Dónde reviso las pólizas?", anterior)).isEmpty();
        assertThat(FichaDelInsumo.responder("¿Cómo verifico la planilla PILA?", anterior)).isEmpty();
    }

    @Test
    void siElContratoNoTieneEseSubpasoNoSeInventaUno() {
        assertThat(FichaDelInsumo.responder("¿Dónde verifico la factura?", List.of(ETAPAS.get(0)))).isEmpty();
    }
}
