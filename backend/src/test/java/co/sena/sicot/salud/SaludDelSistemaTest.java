package co.sena.sicot.salud;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Lo que se prueba aquí es que los avisos no se conviertan en ruido, porque un
 * aviso ruidoso se ignora y entonces da igual que exista — que es exactamente
 * cómo el respaldo pasó seis días caído con la vigilancia funcionando.
 */
class SaludDelSistemaTest {

    /**
     * Un informe que no manda nada y solo cuenta. Subclase y no un mock para no
     * atar la prueba a un framework: lo único que hace falta saber es cuántas
     * veces se llamó y con qué.
     */
    private static class InformeDePrueba extends InformeParaElTecnico {
        final List<Servicio> caidas = new ArrayList<>();
        final List<Servicio> recuperaciones = new ArrayList<>();

        InformeDePrueba() {
            super(null, "", "");
        }

        @Override
        public void informarCaida(Servicio servicio, SaludDelSistema.Incidencia incidencia) {
            caidas.add(servicio);
        }

        @Override
        public void informarRecuperacion(Servicio servicio, SaludDelSistema.Incidencia incidencia) {
            recuperaciones.add(servicio);
        }
    }

    private MeterRegistry registro;
    private InformeDePrueba informe;
    private SaludDelSistema salud;

    @BeforeEach
    void preparar() {
        registro = new SimpleMeterRegistry();
        informe = new InformeDePrueba();
        salud = new SaludDelSistema(registro, informe);
    }

    private double medidorDe(Servicio servicio) {
        return registro.get("sicot.salud.estado")
                .tag("servicio", servicio.name().toLowerCase())
                .gauge().value();
    }

    @Test
    @DisplayName("sin fallos, todo está operativo y no se molesta a nadie")
    void loNormalEsSilencio() {
        assertThat(salud.estado(Servicio.IA)).isEqualTo(EstadoDeServicio.OPERATIVO);
        assertThat(salud.loQueDebeSaberElUsuario()).isEmpty();
        assertThat(informe.caidas).isEmpty();
        assertThat(medidorDe(Servicio.IA)).isZero();
    }

    @Test
    @DisplayName("un fallo suelto de la IA no es una caída: no se avisa a nadie todavía")
    void unTropiezoNoLevantaANadie() {
        salud.fallo(Servicio.IA, "timeout");

        assertThat(salud.estado(Servicio.IA)).isEqualTo(EstadoDeServicio.DEGRADADO);
        // Lo importante: el usuario no ve nada y el técnico no recibe nada.
        assertThat(salud.loQueDebeSaberElUsuario()).isEmpty();
        assertThat(informe.caidas).isEmpty();
    }

    @Test
    @DisplayName("tres fallos seguidos de la IA sí la dan por caída, y el informe sale UNA vez")
    void laInsistenciaSiLevantaANadie() {
        salud.fallo(Servicio.IA, "timeout");
        salud.fallo(Servicio.IA, "timeout");
        salud.fallo(Servicio.IA, "connection refused");

        assertThat(salud.estado(Servicio.IA)).isEqualTo(EstadoDeServicio.CAIDO);
        assertThat(salud.loQueDebeSaberElUsuario()).containsExactly(Servicio.IA);
        assertThat(informe.caidas).containsExactly(Servicio.IA);
        assertThat(medidorDe(Servicio.IA)).isEqualTo(EstadoDeServicio.CAIDO.ordinal());

        // Y ahora lo que de verdad decide si el técnico sigue leyendo los
        // avisos dentro de un mes: veinte fallos más no son veinte informes.
        for (int i = 0; i < 20; i++) {
            salud.fallo(Servicio.IA, "sigue sin responder");
        }
        assertThat(informe.caidas).hasSize(1);
    }

    @Test
    @DisplayName("un solo fallo del respaldo ya es una caída: ahí no hay duda que resolver")
    void elRespaldoNoNecesitaTresMiradas() {
        salud.fallo(Servicio.RESPALDO, "el respaldo más reciente tiene 136 h");

        assertThat(salud.estado(Servicio.RESPALDO)).isEqualTo(EstadoDeServicio.CAIDO);
        assertThat(informe.caidas).containsExactly(Servicio.RESPALDO);
    }

    @Test
    @DisplayName("el respaldo caído no se le cuenta al usuario: no cambia nada de lo que puede hacer")
    void alUsuarioSoloLoQueLeSirve() {
        salud.fallo(Servicio.RESPALDO, "no hay ningún respaldo");

        assertThat(salud.loQueDebeSaberElUsuario()).isEmpty();
    }

    @Test
    @DisplayName("cuando vuelve, se cierra la incidencia y se le dice al técnico que ya no hace falta")
    void laRecuperacionTambienSeCuenta() {
        salud.fallo(Servicio.IA, "timeout");
        salud.fallo(Servicio.IA, "timeout");
        salud.fallo(Servicio.IA, "timeout");
        assertThat(informe.caidas).containsExactly(Servicio.IA);

        salud.exito(Servicio.IA);

        assertThat(salud.estado(Servicio.IA)).isEqualTo(EstadoDeServicio.OPERATIVO);
        assertThat(salud.loQueDebeSaberElUsuario()).isEmpty();
        assertThat(salud.incidencia(Servicio.IA)).isEmpty();
        assertThat(informe.recuperaciones).containsExactly(Servicio.IA);
        assertThat(medidorDe(Servicio.IA)).isZero();
    }

    @Test
    @DisplayName("si no se había informado la caída, tampoco se informa la recuperación")
    void noSeCierraLoQueNuncaSeAbrio() {
        salud.fallo(Servicio.IA, "un tropiezo");

        salud.exito(Servicio.IA);

        assertThat(salud.estado(Servicio.IA)).isEqualTo(EstadoDeServicio.OPERATIVO);
        assertThat(informe.recuperaciones).isEmpty();
    }

    @Test
    @DisplayName("un éxito en medio reinicia la cuenta: tres fallos salteados no son una caída")
    void laCuentaEsDeFallosSEGUIDOS() {
        salud.fallo(Servicio.IA, "timeout");
        salud.fallo(Servicio.IA, "timeout");
        salud.exito(Servicio.IA);
        salud.fallo(Servicio.IA, "timeout");
        salud.fallo(Servicio.IA, "timeout");

        assertThat(salud.estado(Servicio.IA)).isEqualTo(EstadoDeServicio.DEGRADADO);
        assertThat(informe.caidas).isEmpty();
    }

    @Test
    @DisplayName("la incidencia guarda el último detalle y los intentos de reparación, que es lo que lleva el informe")
    void laIncidenciaGuardaLoQueElInformeNecesita() {
        salud.fallo(Servicio.IA, "primer motivo");
        salud.anotarReparacion(Servicio.IA);
        salud.anotarReparacion(Servicio.IA);
        salud.fallo(Servicio.IA, "el motivo de ahora");

        SaludDelSistema.Incidencia incidencia = salud.incidencia(Servicio.IA).orElseThrow();
        assertThat(incidencia.ultimoDetalle()).isEqualTo("el motivo de ahora");
        assertThat(incidencia.reparaciones()).isEqualTo(2);
        assertThat(incidencia.fallosSeguidos()).isEqualTo(2);
        assertThat(incidencia.duracion().isNegative()).isFalse();
    }

    @Test
    @DisplayName("cada servicio lleva su propia cuenta, sin contagiarse")
    void losServiciosNoSeMezclan() {
        salud.fallo(Servicio.IA, "timeout");
        salud.fallo(Servicio.CORREO, "conexión rechazada");

        assertThat(salud.estado(Servicio.IA)).isEqualTo(EstadoDeServicio.DEGRADADO);
        assertThat(salud.estado(Servicio.CORREO)).isEqualTo(EstadoDeServicio.DEGRADADO);
        assertThat(salud.estado(Servicio.RESPALDO)).isEqualTo(EstadoDeServicio.OPERATIVO);
        assertThat(salud.incidenciasAbiertas()).containsOnlyKeys(Servicio.IA, Servicio.CORREO);
    }
}
