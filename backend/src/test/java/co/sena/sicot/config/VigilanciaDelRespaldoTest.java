package co.sena.sicot.config;

import co.sena.sicot.salud.AvisoDeSalud;
import co.sena.sicot.salud.Servicio;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.scheduling.annotation.Scheduled;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

        vigilancia.comprobarCadaTantasHoras();

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

        vigilancia.comprobarCadaTantasHoras();

        assertThat(valorDe(registro)).isEqualTo(-1);
    }

    @Test
    void conUnRespaldoRecienteMideSuAntiguedadEnHoras(@TempDir Path directorio) throws IOException {
        crearRespaldoDeHace(directorio, "sicot-2026-09-08.dump", Duration.ofHours(3));

        MeterRegistry registro = new SimpleMeterRegistry();
        new VigilanciaDelRespaldo(registro, directorio.toString(), 24).comprobarCadaTantasHoras();

        assertThat(valorDe(registro)).isEqualTo(3);
    }

    @Test
    void conUnRespaldoMasViejoQueElRpoLoReporta(@TempDir Path directorio) throws IOException {
        crearRespaldoDeHace(directorio, "sicot-viejo.dump", Duration.ofHours(50));

        MeterRegistry registro = new SimpleMeterRegistry();
        new VigilanciaDelRespaldo(registro, directorio.toString(), 24).comprobarCadaTantasHoras();

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
        new VigilanciaDelRespaldo(registro, directorio.toString(), 24).comprobarCadaTantasHoras();

        assertThat(valorDe(registro)).isEqualTo(2);
    }

    /**
     * El fallo que el CI cobró el 22 de septiembre: {@code registry.gauge(...)}
     * sostiene con una referencia DÉBIL el objeto que alimenta la métrica. Si el
     * recolector de basura pasa entre la medición y la lectura, el gauge pasa a
     * valer NaN — y {@code (long) NaN} es 0, que se lee como «el respaldo es de
     * hace un momento», justo lo contrario de una alarma. Aquí se fuerza esa
     * recolección para que el fallo no dependa de cuándo decida pasar.
     */
    @Test
    void laMetricaSobreviveAUnaRecoleccionDeBasura(@TempDir Path directorio) throws IOException {
        crearRespaldoDeHace(directorio, "sicot.dump", Duration.ofHours(6));

        MeterRegistry registro = new SimpleMeterRegistry();
        new VigilanciaDelRespaldo(registro, directorio.toString(), 24).comprobarCadaTantasHoras();

        System.gc();
        System.gc();

        assertThat(valorDe(registro)).isEqualTo(6);
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

        vigilancia.comprobarCadaTantasHoras();

        assertThat(valorDe(registro)).isEqualTo(-1);
    }

    /**
     * Lo que no es un respaldo terminado no cuenta como respaldo, por reciente
     * que sea. Antes valía cualquier archivo: el volcado a medias de un
     * respaldo que falló esa misma noche, un archivo vacío o el log del cron
     * dejado en la carpeta apagaban el aviso de RPO justo cuando el respaldo
     * había dejado de funcionar.
     */
    @Test
    void soloCuentaUnVolcadoTerminadoYNoVacio(@TempDir Path directorio) throws IOException {
        crearRespaldoDeHace(directorio, "sicot-20261001-020000.dump", Duration.ofHours(50));
        crearRespaldoDeHace(directorio, "sicot-20261003-020000.dump.parcial", Duration.ofHours(2));
        crearRespaldoDeHace(directorio, "sicot-respaldo.log", Duration.ofHours(2));
        Path vacio = Files.createFile(directorio.resolve("sicot-20261003-030000.dump"));
        Files.setLastModifiedTime(vacio, FileTime.from(Instant.now().minus(Duration.ofHours(1))));
        MeterRegistry registro = new SimpleMeterRegistry();

        new VigilanciaDelRespaldo(registro, directorio.toString(), 24).comprobarCadaTantasHoras();

        assertThat(valorDe(registro)).isEqualTo(50);
    }

    /**
     * Un respaldo cifrado sigue siendo un respaldo.
     *
     * <p>Desde el 20-11-2026 el script puede cifrar el volcado, y el archivo
     * resultante se llama {@code .dump.gpg}. Sin contarlo aquí, encender el
     * cifrado habría dejado CIEGA a esta vigilancia: el directorio se llenaría
     * de respaldos correctos y el sistema avisaría cada día de que no hay
     * ninguno, hasta que alguien apagara el aviso por ruidoso. Es decir, el
     * control que iba a mejorar el respaldo habría provocado exactamente el
     * fallo que esta clase existe para evitar.
     */
    @Test
    void unRespaldoCifradoCuentaIgual(@TempDir Path directorio) throws IOException {
        crearRespaldoDeHace(directorio, "sicot-20261120-020000.dump.gpg", Duration.ofHours(4));

        MeterRegistry registro = new SimpleMeterRegistry();
        new VigilanciaDelRespaldo(registro, directorio.toString(), 24).comprobarCadaTantasHoras();

        assertThat(valorDe(registro)).isEqualTo(4);
    }

    @Test
    void unCifradoVacioNoCuentaComoRespaldo(@TempDir Path directorio) throws IOException {
        crearRespaldoDeHace(directorio, "sicot-20261001-020000.dump", Duration.ofHours(50));
        // Lo que deja un cifrado que falló a mitad: reciente y sin contenido.
        Path vacio = Files.createFile(directorio.resolve("sicot-20261120-020000.dump.gpg"));
        Files.setLastModifiedTime(vacio, FileTime.from(Instant.now().minus(Duration.ofHours(1))));

        MeterRegistry registro = new SimpleMeterRegistry();
        new VigilanciaDelRespaldo(registro, directorio.toString(), 24).comprobarCadaTantasHoras();

        assertThat(valorDe(registro)).isEqualTo(50);
    }

    /**
     * Lo que pasó el 17-11-2026: el equipo estuvo apagado del 12 al 16, al
     * prenderlo el último volcado tenía 138 h y el técnico recibió «falló el
     * respaldo» a las 08:59. El temporizador hizo el volcado a las 09:00:31.
     * Al arrancar se mide, pero todavía no se cuenta como caída.
     */
    @Test
    void alArrancarUnRespaldoViejoTodaviaNoEsUnaCaida(@TempDir Path directorio) throws IOException {
        crearRespaldoDeHace(directorio, "sicot-20261111-140937.dump", Duration.ofHours(138));
        AvisoAnotado aviso = new AvisoAnotado();
        MeterRegistry registro = new SimpleMeterRegistry();

        new VigilanciaDelRespaldo(registro, directorio.toString(), 24, aviso).comprobarAlArrancar();

        assertThat(aviso.fallos)
                .as("el temporizador tiene unos minutos para ponerse al día; avisar ya es una falsa alarma")
                .isEmpty();
        assertThat(valorDe(registro)).as("la medición sí se publica").isEqualTo(138);
    }

    /** Lo mismo con la carpeta vacía: al encender, el temporizador también puede llenarla. */
    @Test
    void alArrancarSinNingunRespaldoTampocoSeAvisaTodavia(@TempDir Path vacio) {
        AvisoAnotado aviso = new AvisoAnotado();

        new VigilanciaDelRespaldo(new SimpleMeterRegistry(), vacio.toString(), 24, aviso).comprobarAlArrancar();

        assertThat(aviso.fallos).isEmpty();
    }

    /** El final feliz del 17-11: el temporizador se pone al día y no llega nada. */
    @Test
    void siElTemporizadorSePoneAlDiaNoLlegaNingunInforme(@TempDir Path directorio) throws IOException {
        crearRespaldoDeHace(directorio, "sicot-20261111-140937.dump", Duration.ofHours(138));
        AvisoAnotado aviso = new AvisoAnotado();
        VigilanciaDelRespaldo vigilancia =
                new VigilanciaDelRespaldo(new SimpleMeterRegistry(), directorio.toString(), 24, aviso);

        vigilancia.comprobarAlArrancar();
        crearRespaldoDeHace(directorio, "sicot-20261117-090032.dump", Duration.ofMinutes(14));
        vigilancia.comprobarCadaTantasHoras();

        assertThat(aviso.fallos).isEmpty();
    }

    /**
     * El margen no puede tapar una avería de verdad: si después de él el
     * respaldo sigue viejo, el temporizador no funcionó y eso sí se informa.
     */
    @Test
    void siElTemporizadorNoSePoneAlDiaLaSegundaMiradaSiAvisa(@TempDir Path directorio) throws IOException {
        crearRespaldoDeHace(directorio, "sicot-20261111-140937.dump", Duration.ofHours(138));
        AvisoAnotado aviso = new AvisoAnotado();
        VigilanciaDelRespaldo vigilancia =
                new VigilanciaDelRespaldo(new SimpleMeterRegistry(), directorio.toString(), 24, aviso);

        vigilancia.comprobarAlArrancar();
        vigilancia.comprobarCadaTantasHoras();

        assertThat(aviso.fallos).hasSize(1);
        assertThat(aviso.fallos.get(0)).contains("138 h");
    }

    /**
     * El margen vale lo que valga la espera del temporizador. Si alguien sube
     * {@code RandomizedDelaySec} por encima del margen, o quita
     * {@code Persistent=true}, la falsa alarma de cada encendido vuelve sin que
     * nada lo diga: por eso se lee el archivo de verdad y no una copia del
     * número.
     */
    @Test
    void elMargenAlArrancarCubreLaEsperaDelTemporizador() throws Exception {
        Scheduled programada = VigilanciaDelRespaldo.class
                .getMethod("comprobarCadaTantasHoras").getAnnotation(Scheduled.class);
        assertThat(programada.initialDelayString()).isEqualTo(VigilanciaDelRespaldo.MARGEN_AL_ARRANCAR);

        String temporizador = Files.readString(Path.of("../scripts/systemd/sicot-respaldo.timer"));
        assertThat(temporizador)
                .as("sin Persistent=true el respaldo perdido no se recupera al encender y el margen no sirve")
                .containsPattern("(?m)^Persistent=true\\s*$");
        Matcher espera = Pattern.compile("(?m)^RandomizedDelaySec=(\\d+)\\s*$").matcher(temporizador);
        assertThat(espera.find())
                .as("RandomizedDelaySec en segundos, para poder compararlo con el margen")
                .isTrue();

        // Cinco minutos más que la espera al azar, para el pg_dump y el arranque de Docker.
        assertThat(Duration.parse(VigilanciaDelRespaldo.MARGEN_AL_ARRANCAR))
                .isGreaterThanOrEqualTo(Duration.ofSeconds(Long.parseLong(espera.group(1))).plusMinutes(5));
    }

    /** Anota lo que la vigilancia le cuenta a la salud del sistema. */
    private static final class AvisoAnotado implements AvisoDeSalud {
        private final List<String> fallos = new ArrayList<>();

        @Override
        public void exito(Servicio servicio) {
            // En estas pruebas solo importa si hubo fallo.
        }

        @Override
        public void fallo(Servicio servicio, String detalle) {
            fallos.add(detalle);
        }
    }

    private void crearRespaldoDeHace(Path directorio, String nombre, Duration antiguedad) throws IOException {
        Path archivo = Files.writeString(directorio.resolve(nombre), "contenido de prueba");
        Files.setLastModifiedTime(archivo, FileTime.from(Instant.now().minus(antiguedad)));
    }

    private long valorDe(MeterRegistry registro) {
        return (long) registro.get(METRICA).gauge().value();
    }
}
