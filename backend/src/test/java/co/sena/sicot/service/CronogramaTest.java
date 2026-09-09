package co.sena.sicot.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El único cálculo de cronograma del sistema.
 *
 * <p>Estas pruebas son la red que impide que vuelva a haber dos. Cubren el
 * cálculo que alimenta a la vez el panel del supervisor
 * ({@code GET /api/contratos/{id}/cronograma}) y la regla que persiste la
 * alerta: si alguien cambia el criterio, lo cambia para los dos o rompe esto.
 */
class CronogramaTest {

    private static final LocalDate HOY = LocalDate.of(2026, 3, 15);
    private static final int SEIS_ETAPAS = 6;

    @Test
    void sinFechasCompletasNoAfirmaNadaEnVezDeSuponerQueVaBien() {
        Cronograma sinFin = Cronograma.calcular(HOY.minusDays(30), null, 27, 5, 2, SEIS_ETAPAS, HOY);

        assertThat(sinFin.semaforo()).isEqualTo(Cronograma.Semaforo.SIN_DATOS);
        assertThat(sinFin.mereceAlerta()).isFalse();
        assertThat(sinFin.mensaje()).contains("no se puede estimar");
    }

    /**
     * Un contrato sin subetapas sembradas tiene 0 % de avance en aritmética
     * pura, y eso lo pintaría en rojo. Sería una afirmación sobre un dato que no
     * se tiene: no es que no haya avanzado, es que no hay flujo con el que
     * comparar.
     */
    @Test
    void sinSubetapasSembradasNoInventaUnAvanceDelCero() {
        Cronograma c = Cronograma.calcular(HOY.minusDays(90), HOY.plusDays(10), 0, 0, 1, SEIS_ETAPAS, HOY);

        assertThat(c.semaforo()).isEqualTo(Cronograma.Semaforo.SIN_DATOS);
        assertThat(c.fraccionDeAvance()).isNull();
        assertThat(c.mereceAlerta()).isFalse();
    }

    @Test
    void enVerdeCuandoElAvanceAcompanaAlPlazo() {
        // 50 % del plazo, 13 de 27 subetapas (48 %): brecha de 2 puntos.
        Cronograma c = Cronograma.calcular(HOY.minusDays(50), HOY.plusDays(50), 27, 13, 3, SEIS_ETAPAS, HOY);

        assertThat(c.semaforo()).isEqualTo(Cronograma.Semaforo.VERDE);
        assertThat(c.mereceAlerta()).isFalse();
    }

    @Test
    void enAmarilloConUnaBrechaIntermedia() {
        // 50 % del plazo, 9 de 27 (33 %): brecha de 17 puntos.
        Cronograma c = Cronograma.calcular(HOY.minusDays(50), HOY.plusDays(50), 27, 9, 3, SEIS_ETAPAS, HOY);

        assertThat(c.semaforo()).isEqualTo(Cronograma.Semaforo.AMARILLO);
        // Amarillo avisa en pantalla pero NO persiste alerta: si cada desvío
        // menor dejara constancia, la bandeja se volvería ruido y dejaría de
        // mirarse justo antes de que apareciera un rojo de verdad.
        assertThat(c.mereceAlerta()).isFalse();
    }

    @Test
    void enRojoYConAlertaCuandoLaBrechaEsGrande() {
        // 50 % del plazo, 3 de 27 (11 %): brecha de 39 puntos.
        Cronograma c = Cronograma.calcular(HOY.minusDays(50), HOY.plusDays(50), 27, 3, 3, SEIS_ETAPAS, HOY);

        assertThat(c.semaforo()).isEqualTo(Cronograma.Semaforo.ROJO);
        assertThat(c.mereceAlerta()).isTrue();
        assertThat(c.tramoDeBrecha()).isEqualTo(30);
    }

    /**
     * El tramo es lo que hace que un atraso que empeora vuelva a avisar
     * (información nueva) y uno estable no (ruido). Se prueba aquí y no solo en
     * la regla porque es propiedad del cálculo, no de quien lo usa.
     */
    @Test
    void elTramoDeBrechaCambiaSoloCuandoElAtrasoEmpeoraDeVerdad() {
        Cronograma moderado = Cronograma.calcular(HOY.minusDays(50), HOY.plusDays(50), 27, 3, 3, SEIS_ETAPAS, HOY);
        Cronograma casiIgual = Cronograma.calcular(HOY.minusDays(51), HOY.plusDays(49), 27, 3, 3, SEIS_ETAPAS, HOY);
        Cronograma grave = Cronograma.calcular(HOY.minusDays(90), HOY.plusDays(10), 27, 3, 3, SEIS_ETAPAS, HOY);

        assertThat(casiIgual.tramoDeBrecha()).isEqualTo(moderado.tramoDeBrecha());
        assertThat(grave.tramoDeBrecha()).isGreaterThan(moderado.tramoDeBrecha());
    }

    /**
     * La estimación por etapa: el plazo se reparte en seis partes iguales. Es lo
     * que antes calculaba el navegador por su cuenta.
     */
    @Test
    void estimaElCierreDeLaEtapaEnCursoRepartiendoElPlazoEnSeis() {
        LocalDate inicio = LocalDate.of(2026, 1, 1);
        LocalDate fin = LocalDate.of(2026, 6, 30);  // 180 días, divisibles entre 6

        Cronograma c = Cronograma.calcular(inicio, fin, 27, 9, 3, SEIS_ETAPAS, HOY);

        // Tres sextos de 180 días = 90 días desde el 1 de enero.
        assertThat(c.cierreEsperado()).isEqualTo(LocalDate.of(2026, 4, 1));
        assertThat(c.etapaActual()).isEqualTo(3);
        assertThat(c.diasDeAtraso()).isNegative();  // el 15 de marzo aún hay margen
    }

    /** Un contrato con todas las etapas cerradas no tiene etapa que estimar. */
    @Test
    void sinEtapaEnCursoNoEstimaFechaDeCierre() {
        Cronograma c = Cronograma.calcular(HOY.minusDays(50), HOY.plusDays(50), 27, 27, null, SEIS_ETAPAS, HOY);

        assertThat(c.cierreEsperado()).isNull();
        assertThat(c.diasDeAtraso()).isNull();
        assertThat(c.semaforo()).isEqualTo(Cronograma.Semaforo.VERDE);
    }

    @Test
    void unContratoQueNoHaEmpezadoTieneCeroPlazoConsumido() {
        Cronograma c = Cronograma.calcular(HOY.plusDays(10), HOY.plusDays(100), 27, 0, 1, SEIS_ETAPAS, HOY);

        assertThat(c.fraccionDePlazo()).isZero();
        assertThat(c.semaforo()).isEqualTo(Cronograma.Semaforo.VERDE);
    }

    /**
     * El aviso de que es una estimación aparece SIEMPRE, no solo cuando hay
     * atraso. Si solo saliera en los casos malos, la lectura buena se leería
     * como un dato oficial — y no existe un plazo por etapa aprobado por el
     * SENA que respalde eso.
     */
    @Test
    void todoMensajeDeclaraQueEsUnaEstimacionYNoUnPlazoOficial() {
        Cronograma verde = Cronograma.calcular(HOY.minusDays(50), HOY.plusDays(50), 27, 13, 3, SEIS_ETAPAS, HOY);
        Cronograma rojo = Cronograma.calcular(HOY.minusDays(50), HOY.plusDays(50), 27, 3, 3, SEIS_ETAPAS, HOY);

        assertThat(verde.mensaje()).contains("no hay un plazo oficial por etapa");
        assertThat(rojo.mensaje()).contains("no hay un plazo oficial por etapa");
    }
}
