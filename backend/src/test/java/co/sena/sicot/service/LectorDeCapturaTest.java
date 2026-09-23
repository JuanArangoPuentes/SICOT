package co.sena.sicot.service;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Qué se lee de una foto de evidencia y qué se dice cuando falta (MDL-205).
 *
 * <p>Las fotos se fabrican con {@link FotoConExif}: sirven para fijar cada
 * combinación de datos presentes y ausentes, no para dar por buena la lectura
 * de las fotos de un teléfono de verdad.
 */
class LectorDeCapturaTest {

    private final LectorDeCaptura lector = new LectorDeCaptura(Clock.system(ZoneId.of("America/Bogota")));

    /** Un punto cualquiera del Valle de Aburrá, con longitud oeste (negativa). */
    private static final double LATITUD = 6.17194;
    private static final double LONGITUD = -75.61139;

    @Test
    void leeLaFechaYLaUbicacionDeUnaFotoCompleta() {
        byte[] foto = FotoConExif.nueva()
                .tomadaEl("2026:09:23 14:03:00").conDesfase("-05:00")
                .en(LATITUD, LONGITUD)
                .jpeg();

        LectorDeCaptura.Captura captura = lector.leer(foto);

        assertThat(captura.fecha()).isEqualTo(Instant.parse("2026-09-23T19:03:00Z"));
        assertThat(captura.latitud()).isCloseTo(LATITUD, within(0.00001));
        assertThat(captura.longitud()).isCloseTo(LONGITUD, within(0.00001));
        assertThat(lector.describir(captura))
                .isEqualTo("Foto tomada el 23/09/2026 a las 14:03 en 6.17194, -75.61139.");
    }

    /**
     * Sin {@code OffsetTimeOriginal}, la hora es la del teléfono y se lee en la
     * zona del Centro. En la zona de la JVM (UTC en el contenedor) la foto
     * quedaría tomada cinco horas antes.
     */
    @Test
    void unaHoraSinDesfaseSeLeeEnLaZonaDelCentro() {
        byte[] foto = FotoConExif.nueva().tomadaEl("2026:09:23 14:03:00").jpeg();

        assertThat(lector.leer(foto).fecha()).isEqualTo(Instant.parse("2026-09-23T19:03:00Z"));
    }

    /** Si la foto dice su desfase, manda ese: puede venir de un teléfono en otra zona. */
    @Test
    void elDesfaseDeLaFotoMandaSobreLaZonaDelCentro() {
        byte[] foto = FotoConExif.nueva().tomadaEl("2026:09:23 14:03:00").conDesfase("+00:00").jpeg();

        assertThat(lector.leer(foto).fecha()).isEqualTo(Instant.parse("2026-09-23T14:03:00Z"));
    }

    @Test
    void unaFotoSinExifNoInventaNiFechaNiLugar() {
        LectorDeCaptura.Captura captura = lector.leer(FotoConExif.jpegSinExif());

        assertThat(captura).isEqualTo(LectorDeCaptura.Captura.SIN_DATOS);
        assertThat(lector.describir(captura)).isEqualTo("La foto no trae fecha de captura ni ubicación.");
    }

    @Test
    void unPngSinExifTampoco() {
        assertThat(lector.leer(FotoConExif.png())).isEqualTo(LectorDeCaptura.Captura.SIN_DATOS);
    }

    @Test
    void conFechaPeroSinUbicacionLoDice() {
        LectorDeCaptura.Captura captura = lector.leer(
                FotoConExif.nueva().tomadaEl("2026:09:23 14:03:00").conDesfase("-05:00").jpeg());

        assertThat(captura.tieneUbicacion()).isFalse();
        assertThat(lector.describir(captura)).isEqualTo("Foto tomada el 23/09/2026 a las 14:03; no trae ubicación.");
    }

    @Test
    void conUbicacionPeroSinFechaLoDice() {
        LectorDeCaptura.Captura captura = lector.leer(FotoConExif.nueva().en(LATITUD, LONGITUD).jpeg());

        assertThat(captura.fecha()).isNull();
        assertThat(lector.describir(captura))
                .isEqualTo("Foto tomada en 6.17194, -75.61139; no trae fecha de captura.");
    }

    /**
     * Algunos teléfonos escriben ceros cuando no tienen señal de GPS. (0, 0) está
     * en el océano, frente a África: tomarlo por una ubicación sería afirmar que
     * la entrega se recibió allí.
     */
    @Test
    void laUbicacionCeroCeroNoEsUnaUbicacion() {
        LectorDeCaptura.Captura captura = lector.leer(FotoConExif.nueva().en(0, 0).jpeg());

        assertThat(captura.tieneUbicacion()).isFalse();
    }

    /** Un archivo que no es una imagen legible no hace fallar la carga: se queda sin datos. */
    @Test
    void unArchivoIlegibleNoLanza() {
        byte[] basura = "esto no es una foto".getBytes(StandardCharsets.UTF_8);

        assertThat(lector.leer(basura)).isEqualTo(LectorDeCaptura.Captura.SIN_DATOS);
    }
}
