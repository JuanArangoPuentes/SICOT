package co.sena.sicot.ia;

import co.sena.sicot.dto.etapa.SubetapaResponse;
import co.sena.sicot.entity.enums.EstadoSubetapa;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La explicación de cada paso y de cada sub-paso, que es lo que el supervisor
 * pide cuando pregunta por uno.
 *
 * <h2>Qué vigila esta prueba</h2>
 * Que no vuelva a pasar lo del 17-11-2026: que preguntar por un sub-paso
 * devuelva su título dicho otra vez. Por eso no se limita a comprobar que hay
 * texto —lo había— sino que lo hay <b>para los 21 sub-pasos y los 6 pasos</b>
 * de la plantilla, y que dice algo más que el nombre.
 */
class ExplicacionDelSubpasoTest {

    private static SubetapaResponse sub(String nombre) {
        return new SubetapaResponse(1L, "0.0", nombre, null, EstadoSubetapa.PENDIENTE, "Supervisor");
    }

    /**
     * Los nombres de los 21 sub-pasos tal como los siembra
     * {@code GcconP010Plantilla}. Si allí se añade o se renombra uno, esta
     * prueba se pone roja y hay que escribir su explicación: ese es el punto.
     */
    @ParameterizedTest(name = "{0}")
    @CsvSource({
            "Suscripción y publicación del contrato en SECOP II",
            "Designación del supervisor y del apoyo a la supervisión (GCCON-F-032)",
            "Registro del contrato en SICOT y aviso al supervisor",
            "Firma y devolución del Acta de Inicio (GCCON-F-018)",
            "Aviso a los profesionales SST y ambiental",
            "Verificación de especificaciones en SECOP II",
            "Contacto con el contratista y acuerdo de entregas",
            "Verificación física de la entrega en bodega",
            "Carga de evidencia fotográfica georreferenciada",
            "Comparación cantidad/calidad vs. ficha técnica",
            "Firma del Informe de Supervisión (GCCON-F-031)",
            "Verificación de aportes a seguridad social (PILA)",
            "Verificación de factura electrónica DIAN (FEV)",
            "Firma del Acta de Recibo a Satisfacción (GIL-F-010)",
            "Verificación de vigencia de garantías",
            "Revisión de orden de pago y CRP",
            "Firma de la Certificación de cumplimiento",
            "Verificación de cumplimiento total del objeto contractual",
            "Evaluación de modificaciones o adiciones (si aplica)",
            "Firma del Informe Final de Supervisión (GCCON-F-030)",
            "Cierre y archivo del expediente digital en SIGEP",
    })
    @DisplayName("los 21 sub-pasos del proceso tienen explicación, y dice algo más que el título")
    void losVeintiunSubpasosTienenExplicacion(String nombre) {
        ExplicacionDelSubpaso.Explicacion e = ExplicacionDelSubpaso.delSubpaso(sub(nombre)).orElseThrow();

        assertThat(e.queEs()).isNotBlank().doesNotContain(nombre).hasSizeGreaterThan(60);
        assertThat(e.paraQue()).isNotBlank().hasSizeGreaterThan(40);
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({
            "INICIO — Designación de la supervisión (GCCON-F-032), deja el contrato publicado",
            "INICIO — Acta de Inicio y arranque (GCCON-F-018), arranca la ejecución",
            "INSPECCIÓN — Monitoreo y Ejecución, vigila la ejecución",
            "RECEPCIÓN — Acta de Recibo a Satisfacción, recibe formalmente",
            "CERTIFICACIÓN — Cumplimiento y Trámite de Pago, respalda el pago",
            "CIERRE — Informe Final y Archivo (GCCON-F-030), cierra el contrato",
    })
    @DisplayName("los 6 pasos dicen para qué son")
    void losSeisPasosDicenParaQueSon(String nombre, String empiezaPor) {
        assertThat(ExplicacionDelSubpaso.delPaso(nombre).orElseThrow()).startsWith(empiezaPor);
    }

    /**
     * Dónde se consigue la factura, la planilla o las pólizas lo dice
     * {@link FichaDelInsumo} cuando preguntan por el insumo. La explicación del
     * sub-paso no copia esa frase: se la pide. Si la tabla de la ficha cambia
     * de nombre de sub-paso, aquí se ve antes de que la línea desaparezca en
     * silencio de las respuestas.
     */
    @Test
    @DisplayName("la línea de «de dónde sale» es la misma frase que da la ficha del insumo")
    void laLineaDelInsumoSaleDeLaFicha() {
        List<String> conInsumo = List.of(
                "Verificación de aportes a seguridad social (PILA)",
                "Verificación de factura electrónica DIAN (FEV)",
                "Verificación de vigencia de garantías");
        for (String nombre : conInsumo) {
            assertThat(ExplicacionDelSubpaso.delSubpaso(sub(nombre)).orElseThrow().deDondeSale())
                    .as(nombre)
                    .isEqualTo(FichaDelInsumo.dondeSeConsigue(nombre).orElseThrow());
        }
        // La ficha técnica y el estudio previo no son insumos de la ficha: esos
        // los dice la tabla de su cosecha.
        assertThat(ExplicacionDelSubpaso.delSubpaso(sub("Comparación cantidad/calidad vs. ficha técnica"))
                .orElseThrow().deDondeSale()).contains("estudio previo del proceso, en el expediente del SECOP II");
    }

    /**
     * Los contratos de antes del 15-10-2026 tienen otros sub-pasos con los
     * mismos códigos: por eso se reconocen por el nombre. Si el nombre no es de
     * los del proceso, no hay explicación y quien pregunta se queda con la
     * descripción del propio sub-paso, no con la de un sub-paso que no es suyo.
     */
    @Test
    @DisplayName("un sub-paso que no es del proceso no recibe la explicación de otro")
    void unSubpasoDeOtroProcesoNoSeExplicaConElDeAhora() {
        assertThat(ExplicacionDelSubpaso.delSubpaso(sub("Verificación de datos del contratista y NIT")))
                .isEqualTo(Optional.empty());
        assertThat(ExplicacionDelSubpaso.delSubpaso(sub("Revisar estudios previos"))).isEqualTo(Optional.empty());
        assertThat(ExplicacionDelSubpaso.delSubpaso(null)).isEqualTo(Optional.empty());
        assertThat(ExplicacionDelSubpaso.delPaso("INICIO — Estudios y Suscripción")).isEqualTo(Optional.empty());
        assertThat(ExplicacionDelSubpaso.delPaso(null)).isEqualTo(Optional.empty());
    }

    /**
     * El Informe de Supervisión (3.4) y el Informe Final (6.3) se llaman casi
     * igual, y el acta de inicio y la de recibo también: cada uno tiene que
     * recibir el suyo.
     */
    @Test
    @DisplayName("los nombres parecidos no se confunden entre sí")
    void losNombresParecidosNoSeConfunden() {
        assertThat(ExplicacionDelSubpaso.delSubpaso(sub("Firma del Informe de Supervisión (GCCON-F-031)"))
                .orElseThrow().queEs()).contains("inspección física, novedades y avances");
        assertThat(ExplicacionDelSubpaso.delSubpaso(sub("Firma del Informe Final de Supervisión (GCCON-F-030)"))
                .orElseThrow().queEs()).contains("No es el Acta de Liquidación");
        assertThat(ExplicacionDelSubpaso.delSubpaso(sub("Firma y devolución del Acta de Inicio (GCCON-F-018)"))
                .orElseThrow().queEs()).contains("la elabora la Unidad de Contratación");
        assertThat(ExplicacionDelSubpaso.delSubpaso(sub("Firma del Acta de Recibo a Satisfacción (GIL-F-010)"))
                .orElseThrow().queEs()).contains("recibe formalmente los bienes");
        assertThat(ExplicacionDelSubpaso.delSubpaso(sub("Firma de la Certificación de cumplimiento"))
                .orElseThrow().queEs()).contains("ESUCON");
        assertThat(ExplicacionDelSubpaso.delSubpaso(sub("Verificación de cumplimiento total del objeto contractual"))
                .orElseThrow().queEs()).contains("su objeto y sus obligaciones");
    }
}
