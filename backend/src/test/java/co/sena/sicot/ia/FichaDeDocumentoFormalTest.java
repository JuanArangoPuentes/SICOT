package co.sena.sicot.ia;

import co.sena.sicot.dto.etapa.EtapaResponse;
import co.sena.sicot.dto.etapa.SubetapaResponse;
import co.sena.sicot.entity.enums.EstadoEtapa;
import co.sena.sicot.entity.enums.EstadoSubetapa;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * «¿Qué es el GCCON-F-031, quién lo firma y en qué sub-paso se genera?» se
 * contesta con el catálogo, no con el modelo.
 *
 * <p>Las cuatro preguntas de {@link #reconoceLasCuatroSugerenciasRapidasDeDocumentos()}
 * son, letra por letra, las de {@code QUICK_SUGGESTIONS} en
 * {@code frontend/src/screens/SupervisorPanel.tsx}. Si alguien cambia allí una
 * pregunta y deja de entrar por aquí, esta prueba no se entera: por eso el
 * comentario de esa constante pide actualizar esta lista a la vez.
 */
class FichaDeDocumentoFormalTest {

    private final FichaDeDocumentoFormal ficha = new FichaDeDocumentoFormal();

    static final String SUGERENCIA_F031 = "¿Qué es el GCCON-F-031, quién lo firma y en qué sub-paso se genera?";
    static final String SUGERENCIA_F010 = "¿Qué es el GIL-F-010, quién lo firma y en qué sub-paso se genera?";
    static final String SUGERENCIA_ESUCON =
            "¿Qué es \"ESUCON\"? ¿Tiene código de formato oficial confirmado? ¿En qué sub-paso se genera y quién lo firma?";
    static final String SUGERENCIA_F030 =
            "¿Qué es el GCCON-F-030? ¿Es lo mismo que el acta de liquidación? ¿En qué sub-paso se genera?";

    /**
     * El flujo de la interfaz (contrato del chat, §2). Hasta el 2-10-2026 la
     * ficha decía que lo que SICOT no sabe «queda como dato pendiente» —la
     * interfaz se lo pide antes— y que él «lo revisa antes de firmarlo» sin
     * decir dónde.
     */
    private static final String COMO_SE_HACE = "Cómo se hace en SICOT: en el sub-paso del documento, pulse «Firmar "
            + "documento». SICOT le pide los datos que el contrato no tiene (y sus notas, si el formato lleva "
            + "observaciones), arma el borrador y se lo muestra en «Revisar antes de firmar»; ahí puede abrirlo con "
            + "«Ver borrador completo (PDF)» y, si el Copiloto redactó sus notas, ver qué cambió frente a lo que usted "
            + "escribió. Lo que deje sin diligenciar sale marcado como «dato pendiente». La firma solo se aplica "
            + "cuando usted pulsa «Firmar»; «Cancelar» cierra sin firmar y el borrador queda pendiente.";
    private static final String FIRMA = "Quién firma: usted, como supervisor, con la firma electrónica que el "
            + "Administrador le asignó a su cuenta.";

    private static SubetapaResponse sub(String codigo, String nombre, EstadoSubetapa estado) {
        return new SubetapaResponse(1L, codigo, nombre, null, estado, "Supervisor");
    }

    /** Los pasos 3, 5 y 6 con los nombres reales de GcconP010Plantilla. */
    private static List<EtapaResponse> contratoEnInspeccion() {
        return List.of(
                new EtapaResponse(3L, 3, "INSPECCIÓN — Monitoreo y Ejecución", EstadoEtapa.EN_CURSO, 50, List.of(
                        sub("3.1", "Verificación física de la entrega en bodega", EstadoSubetapa.COMPLETADA),
                        sub("3.4", "Firma del Informe de Supervisión (GCCON-F-031)", EstadoSubetapa.EN_CURSO))),
                new EtapaResponse(5L, 5, "CERTIFICACIÓN — Cumplimiento y Trámite de Pago", EstadoEtapa.PENDIENTE, 0,
                        List.of(sub("5.3", "Firma de la Certificación de cumplimiento", EstadoSubetapa.PENDIENTE))),
                new EtapaResponse(6L, 6, "CIERRE — Informe Final y Archivo (GCCON-F-030)", EstadoEtapa.PENDIENTE, 0,
                        List.of(sub("6.3", "Firma del Informe Final de Supervisión (GCCON-F-030)",
                                EstadoSubetapa.PENDIENTE))));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Cuándo contesta
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("las cuatro sugerencias rápidas de documentos entran sin modelo")
    void reconoceLasCuatroSugerenciasRapidasDeDocumentos() {
        assertThat(ficha.puedeResponder(SUGERENCIA_F031)).isTrue();
        assertThat(ficha.puedeResponder(SUGERENCIA_F010)).isTrue();
        assertThat(ficha.puedeResponder(SUGERENCIA_ESUCON)).isTrue();
        assertThat(ficha.puedeResponder(SUGERENCIA_F030)).isTrue();
    }

    @Test
    @DisplayName("reconoce el código escrito de varias formas y el nombre del documento")
    void reconoceElCodigoYElNombre() {
        assertThat(ficha.puedeResponder("¿qué es el gccon f031?")).isTrue();
        assertThat(ficha.puedeResponder("que es el GCCONF031")).isTrue();
        assertThat(ficha.puedeResponder("¿Quién firma el acta de inicio?")).isTrue();
        assertThat(ficha.puedeResponder("¿En qué paso se firma la certificación de cumplimiento?")).isTrue();
        assertThat(ficha.puedeResponder("¿Quién firma el oficio de pago?")).isTrue();
        // Nombrado a secas también es una pregunta por la ficha.
        assertThat(ficha.puedeResponder("GCCON-F-031")).isTrue();
        assertThat(ficha.puedeResponder("¿Y el informe final?")).isTrue();
    }

    @Test
    @DisplayName("deja al modelo lo que no pregunta por el documento en sí")
    void dejaAlModeloLoQueNoEsLaFicha() {
        assertThat(ficha.puedeResponder("¿Ya puedo firmar el Acta de Inicio?")).isFalse();
        assertThat(ficha.puedeResponder("¿Qué es un CDP?")).isFalse();
        assertThat(ficha.puedeResponder("hola")).isFalse();
        assertThat(ficha.puedeResponder(null)).isFalse();
        // Condicional: pide consejo sobre un caso, aunque traiga «qué» y el documento.
        assertThat(ficha.puedeResponder("¿Qué hago si el contratista no firma el acta de inicio?")).isFalse();
    }

    /**
     * «F-010» a secas, o con otro prefijo, puede ser otro formato del SENA.
     * Contestar con la ficha del GIL-F-010 sería afirmar algo falso con toda
     * seguridad; mejor que conteste el modelo, que dirá que no lo sabe.
     */
    @Test
    @DisplayName("un código con otro prefijo no se toma por uno de los nuestros")
    void unCodigoConOtroPrefijoNoEsElNuestro() {
        assertThat(ficha.puedeResponder("¿Qué es el GCCON-F-010?")).isFalse();
        assertThat(ficha.puedeResponder("¿Qué es el F-031?")).isFalse();
        assertThat(ficha.puedeResponder("¿Qué es el GCCON-F-0311?")).isFalse();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Qué contesta: el texto exacto
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("GCCON-F-031: código, sub-paso, quién firma y su estado en este contrato")
    void fichaDelInformeDeSupervision() {
        assertThat(ficha.responder(SUGERENCIA_F031, contratoEnInspeccion()).orElseThrow()).isEqualTo("""
                Informe de Supervisión (GCCON-F-031).

                Qué contiene: control de la ejecución, inspección física, novedades y avances del contrato.
                Dónde se genera: en el sub-paso 3.4 (Paso 3: INSPECCIÓN — Monitoreo y Ejecución).
                %s
                %s
                En este contrato, el sub-paso 3.4 está en curso.

                Si necesita más detalle, pregúnteme.""".formatted(FIRMA, COMO_SE_HACE));
    }

    @Test
    @DisplayName("ESUCON: dice que no tiene código oficial en vez de inventarle uno")
    void fichaDeLaCertificacionSinCodigo() {
        assertThat(ficha.responder(SUGERENCIA_ESUCON, contratoEnInspeccion()).orElseThrow()).isEqualTo("""
                Certificación de cumplimiento («ESUCON»).

                Qué es: la certificación del supervisor que respalda el trámite de pago.
                Código: no tiene un código de formato oficial confirmado; «ESUCON» es como la llaman en el Centro. \
                Si necesita el código, confírmelo con la Unidad de Gestión Contractual.
                Dónde se genera: en el sub-paso 5.3 (Paso 5: CERTIFICACIÓN — Cumplimiento y Trámite de Pago).
                %s
                %s
                En este contrato, el sub-paso 5.3 está pendiente.

                Si necesita más detalle, pregúnteme.""".formatted(FIRMA, COMO_SE_HACE));
    }

    @Test
    @DisplayName("GCCON-F-030: aclara que no es el Acta de Liquidación")
    void fichaDelInformeFinal() {
        assertThat(ficha.responder(SUGERENCIA_F030, contratoEnInspeccion()).orElseThrow()).isEqualTo("""
                Informe Final de Supervisión (GCCON-F-030).

                Qué contiene: el cumplimiento de las obligaciones y del objeto del contrato, y su estado financiero \
                al cierre.
                Ojo: no es el Acta de Liquidación.
                Dónde se genera: en el sub-paso 6.3 (Paso 6: CIERRE — Informe Final y Archivo (GCCON-F-030)).
                %s
                %s
                En este contrato, el sub-paso 6.3 está pendiente.

                Si necesita más detalle, pregúnteme.""".formatted(FIRMA, COMO_SE_HACE));
    }

    /**
     * Sin las etapas del contrato (o con un contrato que no tiene ese
     * sub-paso) no se inventa en qué paso está ni su estado: se dice solo lo
     * que es fijo.
     */
    @Test
    @DisplayName("GIL-F-010 sin las etapas del contrato: solo lo fijo, sin paso ni estado inventados")
    void fichaSinEtapas() {
        assertThat(ficha.responder(SUGERENCIA_F010, List.of()).orElseThrow()).isEqualTo("""
                Acta de Recibo a Satisfacción de Bienes (GIL-F-010).

                Qué contiene: la recepción formal de los bienes, con su cantidad, calidad y especificaciones técnicas.
                Dónde se genera: en el sub-paso 4.3.
                %s
                %s

                Si necesita más detalle, pregúnteme.""".formatted(FIRMA, COMO_SE_HACE));
        assertThat(ficha.responder(SUGERENCIA_F010, null)).isPresent();
    }

    @Test
    @DisplayName("Acta de Inicio: dice que también la firma el representante legal")
    void fichaDelActaDeInicio() {
        assertThat(ficha.responder("¿Quién firma el acta de inicio?", List.of()).orElseThrow())
                .contains("Acta de Inicio (GCCON-F-018).")
                .contains("Dónde se genera: en el sub-paso 2.7.")
                .contains(FIRMA + " El formato lleva además la firma del representante legal del contratista.");
    }

    @Test
    @DisplayName("Oficio de Pago: lo firma el Ordenador del gasto y SICOT no lo arma")
    void fichaDelOficioDePago() {
        assertThat(ficha.responder("¿Quién firma el GRF-F-089?", contratoEnInspeccion()).orElseThrow()).isEqualTo("""
                Oficio de Pago (GRF-F-089).

                Quién firma: el Ordenador del gasto (Subdirector), no el supervisor.
                En SICOT: no es uno de los documentos que usted firma, y SICOT no lo arma. Lo que usted firma para \
                el pago es la Certificación de cumplimiento, en el sub-paso 5.3.

                Si necesita más detalle, pregúnteme.""");
    }

    @Test
    @DisplayName("si nombra dos documentos, da las dos fichas en el orden del procedimiento")
    void dosDocumentosDosFichas() {
        String r = ficha.responder("¿Qué diferencia hay entre el informe final y el informe de supervisión?",
                contratoEnInspeccion()).orElseThrow();

        assertThat(r.indexOf("Informe de Supervisión (GCCON-F-031).")).isZero();
        assertThat(r).contains("\n\nInforme Final de Supervisión (GCCON-F-030).");
        assertThat(r).endsWith("Si necesita más detalle, pregúnteme.");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // De dónde salen los datos
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * El código y el nombre salen del mismo catálogo con el que se arma el
     * PDF. Si alguien corrige un código en PlantillaDocumentoIA, la ficha lo
     * dice igual sin tocar esta clase.
     */
    @Test
    @DisplayName("el código y el nombre son los de PlantillaDocumentoIA, el mismo catálogo del PDF")
    void losDatosSonLosDelCatalogoDelPdf() {
        for (String clave : List.of("ACTA_INICIO", "INFORME_SUPERVISION", "ACTA_RECIBO", "INFORME_FINAL")) {
            PlantillaDocumentoIA plantilla = PlantillaDocumentoIA.CATALOGO.get(clave);
            assertThat(FichaDeDocumentoFormal.DOCUMENTOS)
                    .anySatisfy(d -> {
                        assertThat(d.codigo()).isEqualTo(plantilla.codigo());
                        assertThat(d.nombre()).isEqualTo(plantilla.nombre());
                    });
        }
        assertThat(FichaDeDocumentoFormal.delSubpaso("5.3").orElseThrow().codigo()).isNull();
        assertThat(FichaDeDocumentoFormal.delSubpaso("3.2")).isEmpty();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 2-10-2026
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * «Qué es» casaba con «qué escribo» y «qué está» (borde de palabra solo a
     * la izquierda), y la ficha genérica contestaba preguntas abiertas sobre la
     * redacción, que es donde el modelo sí aporta.
     */
    @Test
    @DisplayName("«qué escribo…» y «qué está mal…» no se toman por «qué es»")
    void queEscriboNoEsQueEs() {
        assertThat(ficha.puedeResponder("¿Qué escribo en las observaciones del Informe Final?")).isFalse();
        assertThat(ficha.puedeResponder("¿Qué está mal en el acta de inicio que firmé?")).isFalse();
        assertThat(ficha.puedeResponder("¿Qué es el informe final?")).isTrue();
    }

    /**
     * «¿En qué paso se firma el informe?» no reconocía ningún documento y caía
     * en la guía, que contestaba el paso en curso: otra pregunta. Ahora da las
     * fichas de los dos candidatos, sin elegir uno.
     */
    @Test
    @DisplayName("«el informe» o «el acta» a secas dan las fichas de los dos candidatos")
    void elNombreAbreviadoDaLosDosCandidatos() {
        String informe = ficha.responder("¿En qué paso se firma el informe?", contratoEnInspeccion()).orElseThrow();
        assertThat(informe).startsWith("Informe de Supervisión (GCCON-F-031).")
                .contains("\n\nInforme Final de Supervisión (GCCON-F-030).");

        String acta = ficha.responder("en que paso se firma el acta", List.of()).orElseThrow();
        assertThat(acta).startsWith("Acta de Inicio (GCCON-F-018).")
                .contains("\n\nActa de Recibo a Satisfacción de Bienes (GIL-F-010).");

        // «El acta» a secas no es una pregunta: sin frase de ficha no contesta.
        assertThat(ficha.puedeResponder("el acta")).isFalse();
        // «Acta de liquidación» no es un documento de SICOT.
        assertThat(ficha.puedeResponder("¿Qué es el acta de liquidación?")).isFalse();
    }

    @Test
    @DisplayName("la segunda mitad de una pregunta doble la cubre si sigue hablando del documento")
    void cubreLaSegundaMitadSiSigueHablandoDelDocumento() {
        assertThat(ficha.cubre("quién lo firma")).isTrue();
        assertThat(ficha.cubre("En qué sub-paso se genera")).isTrue();
        assertThat(ficha.cubre("dónde consigo la póliza")).isFalse();
        assertThat(ficha.cubre("cuánto vale el contrato")).isFalse();
    }
}
