package co.sena.sicot.config;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La vigilancia que convierte el RPO de ADR-002 en algo comprobable.
 *
 * <h2>Qué se está probando de verdad</h2>
 * No es la lectura de un directorio: es que el sistema pueda distinguir tres
 * situaciones que antes eran indistinguibles desde dentro.
 *
 * <ol>
 *   <li><b>Sin configurar.</b> Nadie apuntó al directorio de respaldos. No se
 *       puede afirmar nada, y afirmar «todo bien» sería peor que callar.</li>
 *   <li><b>Configurado y vacío.</b> El cron no está instalado o no funciona. Es
 *       el escenario que la auditoría encontró y que el RPO de 24 h daba por
 *       resuelto.</li>
 *   <li><b>Configurado y al día.</b> El compromiso se está cumpliendo.</li>
 * </ol>
 *
 * <p>La métrica es lo que separa la primera de las otras dos: {@code -1} no es
 * una antigüedad, es «no se sabe».
 */
class VigilanciaDelRespaldoTest {

    private static final String METRICA = "sicot.respaldo.antiguedad.horas";

    @Test
    void sinDirectorioConfiguradoDejaLaMetricaEnDesconocido() {
        MeterRegistry registro = new SimpleMeterRegistry();
        VigilanciaDelRespaldo vigilancia = new VigilanciaDelRespaldo(registro, "", 24);

        vigilancia.comprobarCadaDia();

        assertThat(valorDe(registro))
                .as("-1 significa 'no se sabe', que es distinto de cualquier antigüedad real")
                .isEqualTo(-1);
    }

    /**
     * El escenario que motivó esta clase: el compromiso escrito en ADR-002 sin
     * el cron que lo cumple. Antes, el sistema no tenía forma de notarlo.
     */
    @Test
    void unDirectorioSinRespaldosDejaLaMetricaEnDesconocido(@TempDir Path vacio) {
        MeterRegistry registro = new SimpleMeterRegistry();
        VigilanciaDelRespaldo vigilancia = new VigilanciaDelRespaldo(registro, vacio.toString(), 24);

        vigilancia.comprobarCadaDia();

        assertThat(valorDe(registro)).isEqualTo(-1);
    }

    @Test
    void conUnRespaldoRecienteMideSuAntiguedadEnHoras(@TempDir Path directorio) throws IOException {
        crearRespaldoDeHace(directorio, "sicot-2026-09-08.dump", Duration.ofHours(3));

        MeterRegistry registro = new SimpleMeterRegistry();
        new VigilanciaDelRespaldo(registro, directorio.toString(), 24).comprobarCadaDia();

        assertThat(valorDe(registro)).isEqualTo(3);
    }

    @Test
    void conUnRespaldoMasViejoQueElRpoLoReporta(@TempDir Path directorio) throws IOException {
        crearRespaldoDeHace(directorio, "sicot-viejo.dump", Duration.ofHours(50));

        MeterRegistry registro = new SimpleMeterRegistry();
        new VigilanciaDelRespaldo(registro, directorio.toString(), 24).comprobarCadaDia();

        assertThat(valorDe(registro))
                .as("50 h con un RPO de 24 h: hay dos días de trabajo sin respaldar")
                .isEqualTo(50);
    }

    /**
     * Con varios respaldos, lo que importa es el más reciente. Un directorio con
     * rotación de catorce días —los que fija ADR-002— tiene siempre archivos
     * viejos, y confundirlos con el estado actual daría una alarma permanente.
     */
    @Test
    void conVariosRespaldosMiraElMasReciente(@TempDir Path directorio) throws IOException {
        crearRespaldoDeHace(directorio, "sicot-antiguo.dump", Duration.ofDays(13));
        crearRespaldoDeHace(directorio, "sicot-intermedio.dump", Duration.ofDays(5));
        crearRespaldoDeHace(directorio, "sicot-nuevo.dump", Duration.ofHours(2));

        MeterRegistry registro = new SimpleMeterRegistry();
        new VigilanciaDelRespaldo(registro, directorio.toString(), 24).comprobarCadaDia();

        assertThat(valorDe(registro)).isEqualTo(2);
    }

    /** Se comprueba también al arrancar, que es cuando alguien mira el log. */
    @Test
    void tambienComprueaAlArrancar(@TempDir Path directorio) throws IOException {
        crearRespaldoDeHace(directorio, "sicot.dump", Duration.ofHours(6));

        MeterRegistry registro = new SimpleMeterRegistry();
        new VigilanciaDelRespaldo(registro, directorio.toString(), 24).comprobarAlArrancar();

        assertThat(valorDe(registro)).isEqualTo(6);
    }

    /**
     * Un directorio que no existe no puede tumbar el arranque: esto es
     * instrumentación, y la instrumentación nunca debe ser el motivo de que una
     * aplicación no levante. Mismo criterio que {@code VigilanciaDeAlmacenamiento}.
     */
    @Test
    void unaRutaInexistenteNoRompeNada() {
        MeterRegistry registro = new SimpleMeterRegistry();
        VigilanciaDelRespaldo vigilancia =
                new VigilanciaDelRespaldo(registro, "/ruta/que/no/existe/en/ninguna/maquina", 24);

        vigilancia.comprobarCadaDia();

        assertThat(valorDe(registro)).isEqualTo(-1);
    }

    private void crearRespaldoDeHace(Path directorio, String nombre, Duration antiguedad) throws IOException {
        Path archivo = Files.writeString(directorio.resolve(nombre), "contenido de prueba");
        Files.setLastModifiedTime(archivo, FileTime.from(Instant.now().minus(antiguedad)));
    }

    private long valorDe(MeterRegistry registro) {
        return (long) registro.get(METRICA).gauge().value();
    }
}
