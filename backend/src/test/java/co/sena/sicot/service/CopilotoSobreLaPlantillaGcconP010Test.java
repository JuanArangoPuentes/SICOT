package co.sena.sicot.service;

import co.sena.sicot.dto.etapa.EtapaResponse;
import co.sena.sicot.entity.Contrato;
import co.sena.sicot.entity.Etapa;
import co.sena.sicot.entity.Subetapa;
import co.sena.sicot.entity.enums.EstadoSubetapa;
import co.sena.sicot.ia.FichaDeDocumentoFormal;
import co.sena.sicot.ia.GuiaDelPasoActual;
import co.sena.sicot.mapper.EtapaMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Los atajos sin modelo del Copiloto, sobre las 27 subetapas que se siembran de
 * verdad en cada contrato nuevo.
 *
 * <p>{@code FichaDeDocumentoFormal} y {@code GuiaDelPasoActual} saben en qué
 * sub-paso se arma cada documento y en cuáles se carga la foto. Si alguien
 * renumera {@link GcconP010Plantilla}, esas respuestas mandarían al supervisor
 * a un sub-paso que no es: estas pruebas son las que se ponen rojas.
 */
class CopilotoSobreLaPlantillaGcconP010Test {

    private final FichaDeDocumentoFormal ficha = new FichaDeDocumentoFormal();
    private final GuiaDelPasoActual guia = new GuiaDelPasoActual();

    private static List<Etapa> contratoNuevo() {
        return GcconP010Plantilla.crearEtapas(new Contrato());
    }

    private static List<EtapaResponse> comoLasDevuelveLaApi(List<Etapa> etapas) {
        return etapas.stream().map(EtapaMapper::toResponse).toList();
    }

    private static Subetapa subetapa(List<Etapa> etapas, String codigo) {
        return etapas.stream().flatMap(e -> e.getSubEtapas().stream())
                .filter(s -> s.getCodigo().equals(codigo)).findFirst().orElseThrow();
    }

    @ParameterizedTest(name = "{0} se arma en {1}")
    @CsvSource(delimiter = '|', value = {
            "¿Qué es el GCCON-F-018?             | 2.7 | GCCON-F-018",
            "¿Qué es el GCCON-F-031?             | 3.4 | GCCON-F-031",
            "¿Qué es el GIL-F-010?               | 4.3 | GIL-F-010",
            "¿Qué es la certificación de cumplimiento? | 5.3 | Certificación de cumplimiento",
            "¿Qué es el GCCON-F-030?             | 6.3 | GCCON-F-030"})
    @DisplayName("la ficha señala el sub-paso de la plantilla que de verdad firma ese documento")
    void laFichaSenalaElSubPasoQueFirmaEseDocumento(String pregunta, String codigo, String documento) {
        List<Etapa> etapas = contratoNuevo();
        Subetapa sub = subetapa(etapas, codigo);
        Etapa etapa = sub.getEtapa();

        assertThat(sub.getNombre()).startsWith("Firma").contains(documento);
        assertThat(ficha.responder(pregunta, comoLasDevuelveLaApi(etapas)).orElseThrow())
                .contains("Dónde se genera: en el sub-paso %s (Paso %d: %s)."
                        .formatted(codigo, etapa.getNumero(), etapa.getNombre()))
                .contains("En este contrato, el sub-paso %s está pendiente.".formatted(codigo));
    }

    /**
     * Lo que ve un supervisor que acaba de recibir el contrato y pulsa la
     * primera sugerencia rápida. El paso 1 lo hacen otros (el área requirente,
     * la Unidad de Contratación, el Ordenador del gasto), y la guía tiene que
     * decirlo en vez de mandarlo a hacerlo él.
     */
    @Test
    @DisplayName("contrato recién creado: el texto exacto de la sugerencia «¿Qué me falta en este paso?»")
    void laSugerenciaDelPasoActualEnUnContratoNuevo() {
        List<EtapaResponse> etapas = comoLasDevuelveLaApi(contratoNuevo());
        String pregunta = "¿Qué me falta en el paso en el que estoy y cómo lo registro en SICOT?";

        assertThat(ficha.puedeResponder(pregunta)).isFalse();
        assertThat(guia.puedeResponder(pregunta)).isTrue();
        assertThat(guia.responder(etapas).orElseThrow()).isEqualTo("""
                Está en el paso 1: INICIO — Estudios y Suscripción.

                Lo que le falta aquí:

                1.1 Identificación de la necesidad institucional  ← en curso
                1.2 Conformación de la Unidad de Contratación
                1.3 Elaboración de estudios previos (GCCON-F-046)
                1.4 Expedición del CDP y aprobación de garantías
                1.5 Suscripción y publicación en SECOP II
                1.6 Designación formal del supervisor

                Empiece por 1.1: Identificación de la necesidad institucional. Planteamiento de la necesidad de \
                bienes y servicios (PAA). Responsable: Área requirente.

                En SICOT: este sub-paso lo realiza Área requirente; márquelo como completado cuando le confirmen \
                que está hecho.

                Si necesita detalle de alguno de estos sub-pasos —qué documento sirve de soporte, de dónde sale \
                un insumo— pregúnteme por él y se lo explico.""");
    }

    @ParameterizedTest(name = "en {0} la guía dice «Cargar evidencia»")
    @CsvSource({"3.1", "3.2"})
    @DisplayName("en los sub-pasos de la foto la guía dice cómo cargarla, como el panel")
    void enLosSubPasosDeLaFotoLaGuiaDiceComoCargarla(String codigo) {
        List<Etapa> etapas = contratoNuevo();
        // Todo cerrado hasta justo antes de ese sub-paso.
        etapas.stream().flatMap(e -> e.getSubEtapas().stream())
                .takeWhile(s -> !s.getCodigo().equals(codigo))
                .forEach(s -> s.setEstado(EstadoSubetapa.COMPLETADA));
        subetapa(etapas, codigo).setEstado(EstadoSubetapa.EN_CURSO);

        assertThat(guia.responder(comoLasDevuelveLaApi(etapas)).orElseThrow())
                .contains("Empiece por %s: ".formatted(codigo))
                .contains("pulse «Cargar evidencia» y, cuando aparezca como cargada, marque el sub-paso como "
                        + "completado.");
    }
}
