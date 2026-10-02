package co.sena.sicot.ia;

import co.sena.sicot.dto.documento.DocumentoResponse;
import co.sena.sicot.dto.etapa.EtapaResponse;
import co.sena.sicot.dto.etapa.SubetapaResponse;
import co.sena.sicot.dto.ia.ChatResponse;
import co.sena.sicot.dto.ia.ChatResponse.TipoAccion;
import co.sena.sicot.entity.enums.EstadoDocumento;
import co.sena.sicot.entity.enums.EstadoEtapa;
import co.sena.sicot.entity.enums.EstadoSubetapa;
import co.sena.sicot.entity.enums.TipoDocumento;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Las órdenes del supervisor al Copiloto, con la matriz de la auditoría del
 * 2-10-2026: cada una se atiende sin modelo y devuelve la pantalla que hay que
 * abrir, nunca una acción que firme, marque o genere.
 */
class OrdenDelSupervisorTest {

    private final OrdenDelSupervisor orden = new OrdenDelSupervisor();
    private final AtomicInteger lecturasDeDocumentos = new AtomicInteger();

    private static SubetapaResponse sub(long id, String codigo, String nombre, EstadoSubetapa estado) {
        return new SubetapaResponse(id, codigo, nombre, null, estado, "Supervisor");
    }

    /** Un contrato en el paso 3: el Acta de Inicio firmada, la foto de 3.1 cargada y el 3.2 pendiente. */
    private static List<EtapaResponse> contratoEnElPaso3() {
        return List.of(
                new EtapaResponse(2L, 2, "INICIO — Acta de Inicio", EstadoEtapa.COMPLETADA, 100, List.of(
                        sub(23, "2.3", "Verificación de pólizas", EstadoSubetapa.COMPLETADA),
                        sub(27, "2.7", "Firma del Acta de Inicio (GCCON-F-018)", EstadoSubetapa.COMPLETADA))),
                new EtapaResponse(3L, 3, "INSPECCIÓN — Monitoreo y Ejecución", EstadoEtapa.EN_CURSO, 25, List.of(
                        sub(31, "3.1", "Verificación física de la entrega en bodega", EstadoSubetapa.COMPLETADA),
                        sub(32, "3.2", "Carga de evidencia fotográfica georreferenciada", EstadoSubetapa.PENDIENTE),
                        sub(33, "3.3", "Comparación cantidad/calidad vs. ficha técnica", EstadoSubetapa.PENDIENTE),
                        sub(34, "3.4", "Firma del Informe de Supervisión (GCCON-F-031)", EstadoSubetapa.PENDIENTE))),
                new EtapaResponse(4L, 4, "RECEPCIÓN — Acta de Recibo", EstadoEtapa.PENDIENTE, 0, List.of(
                        sub(43, "4.3", "Firma del Acta de Recibo (GIL-F-010)", EstadoSubetapa.PENDIENTE))),
                new EtapaResponse(5L, 5, "CERTIFICACIÓN — Cumplimiento y Trámite de Pago", EstadoEtapa.PENDIENTE, 0,
                        List.of(sub(53, "5.3", "Firma de la Certificación de cumplimiento", EstadoSubetapa.PENDIENTE))));
    }

    /** El Acta de Inicio firmada (id 900) y un borrador del Acta de Recibo sin firmar (id 901). */
    private List<DocumentoResponse> documentos() {
        lecturasDeDocumentos.incrementAndGet();
        return List.of(
                documento(900L, 27L, Instant.parse("2026-09-20T15:00:00Z")),
                documento(901L, 43L, null));
    }

    private static DocumentoResponse documento(long id, long subetapaId, Instant fechaFirma) {
        return new DocumentoResponse(id, 1L, subetapaId, null, null, null, "Documento " + id, TipoDocumento.PDF,
                null, fechaFirma == null ? EstadoDocumento.PENDIENTE : EstadoDocumento.APROBADO, 1000L, true,
                fechaFirma == null ? null : "FIRMA-1", fechaFirma, null, null, "Alex Zapata", Instant.now(), null,
                null, null);
    }

    private Optional<ChatResponse> interpretar(String pregunta) {
        Supplier<List<DocumentoResponse>> docs = this::documentos;
        return orden.interpretar(pregunta, contratoEnElPaso3(), docs, () -> Optional.of("Va algo por detrás."));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // La matriz
    // ─────────────────────────────────────────────────────────────────────────

    @ParameterizedTest(name = "«{0}» → {1} {2}")
    @CsvSource(delimiter = '|', textBlock = """
            genera el acta de recibo            | ABRIR_DOCUMENTO     | 4.3
            redacta el informe de supervisión   | ABRIR_DOCUMENTO     | 3.4
            firma el documento                  | ABRIR_DOCUMENTO     | 3.4
            fírmalo                             | ABRIR_DOCUMENTO     | 3.4
            marca como completado el 3.3        | IR_A_SUBPASO        | 3.3
            márcalo como hecho                  | ABRIR_EVIDENCIA     | 3.2
            cierra el paso 3                    | IR_A_PASO           |
            llévame al paso 3                   | IR_A_PASO           |
            abre el 3.3                         | IR_A_SUBPASO        | 3.3
            abre la carga de fotos              | ABRIR_EVIDENCIA     | 3.2
            sube la foto de la entrega          | ABRIR_EVIDENCIA     | 3.2
            llévame al siguiente paso           | ABRIR_EVIDENCIA     | 3.2
            descarga el acta de inicio          | DESCARGAR_DOCUMENTO | 2.7
            descarga los documentos             | MOSTRAR_DOCUMENTOS  |
            muéstrame las alertas               | MOSTRAR_ALERTAS     |
            cambia el tema a claro              | IR_A_CONFIGURACION  |
            por favor llévame al 4.3            | ABRIR_DOCUMENTO     | 4.3
            ¿me puedes abrir el paso 4?         | IR_A_PASO           |
            q me abras el paso 5                |                     |
            """)
    @DisplayName("cada orden de la matriz abre la pantalla que corresponde")
    void cadaOrdenAbreLaPantallaQueCorresponde(String pregunta, TipoAccion tipo, String subpaso) {
        Optional<ChatResponse> r = interpretar(pregunta);
        if (tipo == null) {
            assertThat(r).as(pregunta).isEmpty();
            return;
        }
        assertThat(r).as(pregunta).isPresent();
        assertThat(r.get().fuente()).isEqualTo(ChatResponse.Fuente.SISTEMA);
        assertThat(r.get().accion().tipo()).as(pregunta).isEqualTo(tipo);
        assertThat(r.get().accion().subpaso()).as(pregunta).isEqualTo(subpaso);
        // Nada en el texto puede decir que ya se hizo.
        assertThat(r.get().respuesta()).doesNotContainIgnoringCase("listo,").doesNotContain("ya firmé")
                .doesNotContain("ya marqué").doesNotContain("ya generé");
    }

    @Test
    @DisplayName("«firma…» nunca firma: dice que la firma es del supervisor y lleva a «Revisar antes de firmar»")
    void firmarNuncaFirma() {
        ChatResponse r = interpretar("firma el informe de supervisión").orElseThrow();

        assertThat(r.respuesta())
                .startsWith("Yo no puedo firmar: la firma de «Informe de Supervisión» (GCCON-F-031) es suya")
                .contains("Se firma en el sub-paso 3.4 (paso 3: INSPECCIÓN — Monitoreo y Ejecución), que está "
                        + "pendiente.")
                .contains("Así se hace: " + FlujoDeFirma.COMPLETO)
                .endsWith("Con el botón de abajo abre ese sub-paso.");
        assertThat(r.accion().documentoTipo()).isEqualTo("INFORME_SUPERVISION");
        assertThat(r.accion().paso()).isEqualTo(3);
        assertThat(r.accion().documentoId()).isNull();
    }

    @Test
    @DisplayName("«genera…» no redacta nada en el chat")
    void generarNoRedactaEnElChat() {
        assertThat(interpretar("redacta el informe de supervisión").orElseThrow().respuesta())
                .startsWith("Yo no genero ni redacto documentos en el chat: SICOT arma «Informe de Supervisión» "
                        + "(GCCON-F-031) con los datos exactos del contrato y solo usted lo firma.");
    }

    @Test
    @DisplayName("«marca como completado el 3.3» dice que lo marca él y le deja el botón")
    void marcarLoHaceElSupervisor() {
        ChatResponse r = interpretar("marca como completado el 3.3").orElseThrow();

        assertThat(r.respuesta()).isEqualTo("Yo no marco sub-pasos: lo hace usted cuando lo haya verificado. "
                + "Sub-paso 3.3: Comparación cantidad/calidad vs. ficha técnica, pendiente. En SICOT: cuando lo "
                + "haya hecho, márquelo como completado. Con el botón de abajo abre ese sub-paso.");
        assertThat(r.accion().etiqueta()).isEqualTo("Abrir el sub-paso 3.3");
    }

    @Test
    @DisplayName("descargar solo se ofrece con un documento firmado que existe")
    void descargarSoloUnDocumentoFirmado() {
        ChatResponse firmada = interpretar("descarga el acta de inicio").orElseThrow();
        assertThat(firmada.accion().tipo()).isEqualTo(TipoAccion.DESCARGAR_DOCUMENTO);
        assertThat(firmada.accion().documentoId()).isEqualTo(900L);
        assertThat(firmada.accion().etiqueta()).isEqualTo("Descargar Acta de Inicio");

        ChatResponse borrador = interpretar("descarga el acta de recibo").orElseThrow();
        assertThat(borrador.accion().tipo()).isEqualTo(TipoAccion.ABRIR_DOCUMENTO);
        assertThat(borrador.accion().documentoId()).isNull();
        assertThat(borrador.respuesta()).contains("todavía no está firmado: hay un borrador pendiente en el sub-paso 4.3");

        ChatResponse noExiste = interpretar("descarga el informe de supervisión").orElseThrow();
        assertThat(noExiste.accion().tipo()).isEqualTo(TipoAccion.ABRIR_DOCUMENTO);
        assertThat(noExiste.respuesta()).contains("todavía no existe en este contrato: se arma en el sub-paso 3.4");
    }

    @Test
    @DisplayName("los documentos solo se leen si la orden necesita saber si uno está firmado")
    void losDocumentosSoloSeLeenSiHaceFalta() {
        interpretar("firma el informe de supervisión");
        interpretar("llévame al paso 3");
        assertThat(lecturasDeDocumentos.get()).isZero();

        interpretar("descarga el acta de inicio");
        assertThat(lecturasDeDocumentos.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("«el acta» o «el informe» a secas: pregunta cuál, sin adivinar ni ofrecer botón")
    void unNombreAmbiguoPideAclaracion() {
        ChatResponse r = interpretar("genera el acta").orElseThrow();

        assertThat(r.respuesta()).isEqualTo("¿Cuál de ellos? «Acta de Inicio» (GCCON-F-018) va en el sub-paso 2.7 y "
                + "«Acta de Recibo a Satisfacción de Bienes» (GIL-F-010) va en el sub-paso 4.3. Dígame cuál y le "
                + "dejo el botón para abrirlo.");
        assertThat(r.accion()).isNull();
    }

    @Test
    @DisplayName("el Oficio de Pago no es del supervisor: lo dice y lleva a la Certificación (5.3)")
    void elOficioDePagoNoEsDelSupervisor() {
        ChatResponse r = interpretar("genera el oficio de pago").orElseThrow();

        assertThat(r.respuesta()).startsWith("«Oficio de Pago» (GRF-F-089) lo firma el Ordenador del gasto");
        assertThat(r.accion().subpaso()).isEqualTo("5.3");
    }

    @Test
    @DisplayName("cargar otro archivo que no sea la foto: dice que en SICOT no hay dónde, sin botón")
    void cargarOtroArchivoNoExiste() {
        ChatResponse r = interpretar("sube la factura").orElseThrow();

        assertThat(r.respuesta()).startsWith("En SICOT solo se cargan fotos de la entrega, en los sub-pasos 3.1 y 3.2.");
        assertThat(r.accion()).isNull();
    }

    @Test
    @DisplayName("cambiar un dato del contrato: lo diligencia Gestión")
    void cambiarUnDatoDelContrato() {
        ChatResponse r = interpretar("cambia el valor del contrato").orElseThrow();

        assertThat(r.respuesta()).contains("los diligencia Gestión");
        assertThat(r.accion()).isNull();
    }

    @Test
    @DisplayName("«¿qué puedes hacer?» dice lo que hace y lo que no, sin modelo")
    void quePuedesHacer() {
        ChatResponse r = interpretar("¿Qué puedes hacer?").orElseThrow();

        assertThat(r.respuesta()).isEqualTo(OrdenDelSupervisor.LO_QUE_HACE)
                .contains("no firmo, no genero documentos, no marco sub-pasos");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Lo que no es una orden sigue su camino
    // ─────────────────────────────────────────────────────────────────────────

    @ParameterizedTest
    @ValueSource(strings = {
            "¿cómo se genera el acta de inicio?", "que pasa si no firma el acta de inicio", "¿firma el contratista el acta?",
            "¿quién firma el acta de inicio?", "firma el acta si ya está todo", "hola", "hazme un resumen del contrato",
            "cierra la sesión", "¿en qué paso voy?", "", "abre"})
    @DisplayName("lo que no es una orden reconocible no se atiende aquí")
    void loQueNoEsUnaOrdenSigueSuCamino(String pregunta) {
        assertThat(interpretar(pregunta)).as(pregunta).isEmpty();
    }

    @Test
    @DisplayName("sin etapas no se inventa a dónde llevarlo")
    void sinEtapasNoHayOrden() {
        assertThat(orden.interpretar("llévame al paso 3", List.of(), List::of, Optional::empty)).isEmpty();
        assertThat(orden.interpretar(null, null, List::of, Optional::empty)).isEmpty();
    }
}
