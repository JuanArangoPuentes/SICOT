package co.sena.sicot.service;

import co.sena.sicot.entity.Contrato;
import co.sena.sicot.entity.Etapa;
import co.sena.sicot.entity.Subetapa;
import co.sena.sicot.entity.Usuario;
import co.sena.sicot.entity.enums.EstadoEtapa;
import co.sena.sicot.entity.enums.EstadoSubetapa;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Las etapas con que nace un contrato. Las dos primeras siguen el proceso real
 * que documentó el supervisor el 15-10-2026: la designación es trabajo de
 * Contratación y del apoyo a la supervisión, y el supervisor empieza en el acta.
 */
class GcconP010PlantillaTest {

    private static Contrato conSupervisor() {
        Contrato c = new Contrato();
        c.setSupervisor(new Usuario());
        return c;
    }

    @Test
    @DisplayName("con supervisor, la designación nace hecha y el proceso arranca en el acta")
    void conSupervisorArrancaEnElActa() {
        List<Etapa> etapas = GcconP010Plantilla.crearEtapas(conSupervisor());

        assertThat(etapas).hasSize(6);
        assertThat(etapas.stream().mapToInt(e -> e.getSubEtapas().size()).sum()).isEqualTo(21);
        assertThat(etapas.get(0).getEstado()).isEqualTo(EstadoEtapa.COMPLETADA);
        assertThat(etapas.get(0).getPorcentaje()).isEqualTo(100);
        assertThat(etapas.get(0).getSubEtapas()).allMatch(s -> s.getEstado() == EstadoSubetapa.COMPLETADA);

        Subetapa acta = etapas.get(1).getSubEtapas().get(0);
        assertThat(etapas.get(1).getEstado()).isEqualTo(EstadoEtapa.EN_CURSO);
        assertThat(acta.getCodigo()).isEqualTo("2.1");
        assertThat(acta.getEstado()).isEqualTo(EstadoSubetapa.EN_CURSO);
        assertThat(acta.getDocumentosRequeridos()).isEqualTo(GcconP010Plantilla.ACTA_FIRMADA_Y_ENVIO);
        // SICOT ya no genera el acta: la recibe.
        assertThat(acta.getDescripcion()).contains("cargue aquí el acta firmada").doesNotContain("SICOT arma");
    }

    @Test
    @DisplayName("los pasos 2 a 5 del proceso real son las subetapas de la etapa 2, en ese orden")
    void laEtapa2SonLosPasosDelProcesoReal() {
        List<Subetapa> etapa2 = GcconP010Plantilla.crearEtapas(conSupervisor()).get(1).getSubEtapas();

        assertThat(etapa2).extracting(Subetapa::getNombre).containsExactly(
                "Firma y devolución del Acta de Inicio (GCCON-F-018)",
                "Aviso a los profesionales SST y ambiental",
                "Verificación de especificaciones en SECOP II",
                "Contacto con el contratista y acuerdo de entregas");
        assertThat(etapa2.subList(1, 4)).allMatch(s -> s.getDocumentosRequeridos() == null);
    }

    @Test
    @DisplayName("sin supervisor, la designación queda en curso hasta que se le asigne")
    void sinSupervisorLaDesignacionQuedaEnCurso() {
        List<Etapa> etapas = GcconP010Plantilla.crearEtapas(new Contrato());

        assertThat(etapas.get(0).getEstado()).isEqualTo(EstadoEtapa.EN_CURSO);
        assertThat(etapas.get(0).getSubEtapas().get(0).getEstado()).isEqualTo(EstadoSubetapa.EN_CURSO);
        assertThat(etapas.get(1).getEstado()).isEqualTo(EstadoEtapa.PENDIENTE);

        assertThat(GcconP010Plantilla.completarDesignacion(etapas)).isTrue();
        assertThat(etapas.get(0).getEstado()).isEqualTo(EstadoEtapa.COMPLETADA);
        assertThat(etapas.get(1).getSubEtapas().get(0).getEstado()).isEqualTo(EstadoSubetapa.EN_CURSO);
        // Una segunda asignación (cambio de supervisor) no vuelve a tocar nada.
        assertThat(GcconP010Plantilla.completarDesignacion(etapas)).isFalse();
    }

    @Test
    @DisplayName("a un contrato sembrado con la plantilla anterior no se le completa la etapa 1")
    void laPlantillaAnteriorNoSeToca() {
        Etapa vieja = new Etapa();
        vieja.setNombre("INICIO — Estudios y Suscripción");
        vieja.setEstado(EstadoEtapa.EN_CURSO);
        vieja.setSubEtapas(new java.util.ArrayList<>());
        Etapa segunda = new Etapa();
        segunda.setNombre("INICIO — Acta de Inicio (GCCON-F-018)");
        segunda.setEstado(EstadoEtapa.PENDIENTE);
        segunda.setSubEtapas(new java.util.ArrayList<>());

        assertThat(GcconP010Plantilla.completarDesignacion(List.of(vieja, segunda))).isFalse();
        assertThat(vieja.getEstado()).isEqualTo(EstadoEtapa.EN_CURSO);
    }
}
