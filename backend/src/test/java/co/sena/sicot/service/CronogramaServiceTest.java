package co.sena.sicot.service;

import co.sena.sicot.automatizacion.FotoDelContrato;
import co.sena.sicot.automatizacion.LectorDeContratos;
import co.sena.sicot.entity.enums.EstadoContrato;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * El «hoy» del cronograma es el del Centro, no el de la JVM (MDL-214).
 *
 * <p>Dentro del contenedor la JVM corre en UTC, y a las 19:00 de Bogotá ya es
 * mañana en UTC. Las pruebas fijan el reloj a ambos lados de esa frontera, con
 * un contrato cuya etapa en curso debe cerrarse justo hoy: a las 18:59 y a las
 * 19:01 de Bogotá la etapa vence hoy, no está atrasada.
 */
@ExtendWith(MockitoExtension.class)
class CronogramaServiceTest {

    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
    private static final LocalDate HOY_EN_BOGOTA = LocalDate.of(2026, 9, 23);

    @Mock
    private LectorDeContratos lectorDeContratos;

    @Mock
    private ContratoService contratoService;

    /**
     * Seis etapas y el contrato en la última: el cierre esperado de la etapa en
     * curso es la fecha de fin, que es hoy.
     */
    private final FotoDelContrato contratoQueCierraHoy = new FotoDelContrato(
            7L, "CTMA-2026-0184", "Suministro de mobiliario",
            LocalDate.of(2026, 3, 27), HOY_EN_BOGOTA, EstadoContrato.ACTIVO,
            "Supervisor", "supervisor@soy.sena.edu.co",
            27, 25, 6, 6);

    private Cronograma cronogramaA(String instanteUtc) {
        return cronogramaA(instanteUtc, BOGOTA);
    }

    private Cronograma cronogramaA(String instanteUtc, ZoneId zona) {
        when(lectorDeContratos.porId(7L)).thenReturn(Optional.of(contratoQueCierraHoy));
        Clock reloj = Clock.fixed(Instant.parse(instanteUtc), zona);
        return new CronogramaService(lectorDeContratos, contratoService, reloj).de(7L);
    }

    /**
     * El fallo tal como era: el mismo instante leído en UTC, que es la zona de
     * la JVM dentro del contenedor. Queda como prueba para que se vea qué
     * protege la elección de la zona.
     */
    @Test
    void enUtcElMismoInstanteDabaLaEtapaPorAtrasada() {
        Cronograma cronograma = cronogramaA("2026-09-24T00:01:00Z", ZoneOffset.UTC);

        assertThat(cronograma.diasDeAtraso()).isEqualTo(1);
    }

    @Test
    void unMinutoAntesDeLaFronteraLaEtapaVenceHoy() {
        // 18:59 del 23 en Bogotá = 23:59 del 23 en UTC.
        Cronograma cronograma = cronogramaA("2026-09-23T23:59:00Z");

        assertThat(cronograma.cierreEsperado()).isEqualTo(HOY_EN_BOGOTA);
        assertThat(cronograma.diasDeAtraso()).isZero();
    }

    @Test
    void unMinutoDespuesDeLaFronteraSigueSiendoHoyEnElCentro() {
        // 19:01 del 23 en Bogotá = 00:01 del 24 en UTC. Con LocalDate.now() de
        // la JVM, aquí la etapa aparecía atrasada un día y el mensaje decía
        // «debía cerrarse hacia el 2026-09-23, hace 1 día(s)».
        Cronograma cronograma = cronogramaA("2026-09-24T00:01:00Z");

        assertThat(cronograma.cierreEsperado()).isEqualTo(HOY_EN_BOGOTA);
        assertThat(cronograma.diasDeAtraso()).isZero();
        assertThat(cronograma.mensaje()).contains("debería cerrarse hacia el 2026-09-23").doesNotContain("hace 1 día");
    }
}
