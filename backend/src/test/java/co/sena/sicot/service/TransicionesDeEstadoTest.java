package co.sena.sicot.service;

import co.sena.sicot.entity.enums.EstadoContrato;
import co.sena.sicot.entity.enums.EstadoEtapa;
import co.sena.sicot.entity.enums.EstadoSubetapa;
import co.sena.sicot.exception.BusinessException;
import co.sena.sicot.service.TransicionesDeEstado.Sentido;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TransicionesDeEstadoTest {

    // ---------------- Subetapa ----------------

    @Test
    void subetapaClasificaLosAvancesDelFlujoRealDelSupervisor() {
        assertThat(TransicionesDeEstado.validarSubetapa(EstadoSubetapa.PENDIENTE, EstadoSubetapa.EN_CURSO))
                .isEqualTo(Sentido.AVANCE);
        // Salto directo: el panel del supervisor marca sub-pasos que nunca "inició".
        assertThat(TransicionesDeEstado.validarSubetapa(EstadoSubetapa.PENDIENTE, EstadoSubetapa.COMPLETADA))
                .isEqualTo(Sentido.AVANCE);
        assertThat(TransicionesDeEstado.validarSubetapa(EstadoSubetapa.EN_CURSO, EstadoSubetapa.COMPLETADA))
                .isEqualTo(Sentido.AVANCE);
    }

    @Test
    void subetapaClasificaLasReaperturasComoRetrocesoPeroNoLasRechaza() {
        assertThat(TransicionesDeEstado.validarSubetapa(EstadoSubetapa.COMPLETADA, EstadoSubetapa.PENDIENTE))
                .isEqualTo(Sentido.RETROCESO);
        assertThat(TransicionesDeEstado.validarSubetapa(EstadoSubetapa.COMPLETADA, EstadoSubetapa.EN_CURSO))
                .isEqualTo(Sentido.RETROCESO);
        assertThat(TransicionesDeEstado.validarSubetapa(EstadoSubetapa.EN_CURSO, EstadoSubetapa.PENDIENTE))
                .isEqualTo(Sentido.RETROCESO);
    }

    @Test
    void subetapaEnElMismoEstadoEsSinCambio() {
        assertThat(TransicionesDeEstado.validarSubetapa(EstadoSubetapa.COMPLETADA, EstadoSubetapa.COMPLETADA))
                .isEqualTo(Sentido.SIN_CAMBIO);
    }

    @Test
    void subetapaSinDestinoSeRechaza() {
        assertThatThrownBy(() -> TransicionesDeEstado.validarSubetapa(EstadoSubetapa.PENDIENTE, null))
                .isInstanceOf(BusinessException.class);
    }

    // ---------------- Etapa (clasificación del recálculo) ----------------

    @Test
    void etapaDetectaAvanceRetrocesoYSinCambio() {
        assertThat(TransicionesDeEstado.sentidoRecalculoEtapa(
                EstadoEtapa.EN_CURSO, 33, EstadoEtapa.EN_CURSO, 67)).isEqualTo(Sentido.AVANCE);
        assertThat(TransicionesDeEstado.sentidoRecalculoEtapa(
                EstadoEtapa.COMPLETADA, 100, EstadoEtapa.EN_CURSO, 67)).isEqualTo(Sentido.RETROCESO);
        assertThat(TransicionesDeEstado.sentidoRecalculoEtapa(
                EstadoEtapa.EN_CURSO, 67, EstadoEtapa.EN_CURSO, 67)).isEqualTo(Sentido.SIN_CAMBIO);
        // Mismo porcentaje pero el estado regresa: sigue siendo retroceso.
        assertThat(TransicionesDeEstado.sentidoRecalculoEtapa(
                EstadoEtapa.EN_CURSO, 0, EstadoEtapa.PENDIENTE, 0)).isEqualTo(Sentido.RETROCESO);
    }

    // ---------------- Contrato ----------------

    @Test
    void contratoPermiteLaTransicionInicialConocida() {
        assertThat(TransicionesDeEstado.validarContrato(EstadoContrato.BORRADOR, EstadoContrato.ACTIVO))
                .isEqualTo(Sentido.AVANCE);
    }

    @Test
    void contratoNoPuedeRegresarABorradorDesdeNingunEstado() {
        for (EstadoContrato origen : EstadoContrato.values()) {
            if (origen == EstadoContrato.BORRADOR) {
                continue;
            }
            assertThatThrownBy(() -> TransicionesDeEstado.validarContrato(origen, EstadoContrato.BORRADOR))
                    .as("%s -> BORRADOR", origen)
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("BORRADOR");
        }
    }

    @Test
    void contratoEnElMismoEstadoEsSinCambio() {
        assertThat(TransicionesDeEstado.validarContrato(EstadoContrato.ACTIVO, EstadoContrato.ACTIVO))
                .isEqualTo(Sentido.SIN_CAMBIO);
    }

    @Test
    void contratoDejaPasarLoNoConfirmadoInstitucionalmente() {
        // PENDIENTE_DE_DEFINIR: la máquina de estados del contrato no está en la
        // documentación del SENA. Estas NO se adivinan -> se permiten.
        assertThat(TransicionesDeEstado.validarContrato(EstadoContrato.FINALIZADO, EstadoContrato.ACTIVO))
                .isNotNull();
        assertThat(TransicionesDeEstado.validarContrato(EstadoContrato.CANCELADO, EstadoContrato.ACTIVO))
                .isNotNull();
        assertThat(TransicionesDeEstado.validarContrato(EstadoContrato.ACTIVO, EstadoContrato.SUSPENDIDO))
                .isNotNull();
        assertThat(TransicionesDeEstado.validarContrato(EstadoContrato.SUSPENDIDO, EstadoContrato.ACTIVO))
                .isNotNull();
        assertThat(TransicionesDeEstado.validarContrato(EstadoContrato.ACTIVO, EstadoContrato.FINALIZADO))
                .isNotNull();
        assertThat(TransicionesDeEstado.validarContrato(EstadoContrato.ACTIVO, EstadoContrato.CANCELADO))
                .isNotNull();
    }

    @Test
    void contratoSinDestinoSeRechaza() {
        assertThatThrownBy(() -> TransicionesDeEstado.validarContrato(EstadoContrato.ACTIVO, null))
                .isInstanceOf(BusinessException.class);
    }
}
