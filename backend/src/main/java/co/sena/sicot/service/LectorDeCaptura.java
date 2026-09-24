package co.sena.sicot.service;

import com.drew.imaging.ImageMetadataReader;
import com.drew.imaging.ImageProcessingException;
import com.drew.lang.GeoLocation;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifSubIFDDirectory;
import com.drew.metadata.exif.GpsDirectory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.Locale;

/**
 * Lee de una foto cuándo y dónde se tomó, a partir de su EXIF (MDL-205).
 *
 * <h2>Por qué importa</h2>
 * La subetapa 3.2 del GCCON-P-010 pide evidencia fotográfica georreferenciada
 * de la entrega en bodega. La foto se carga tal como sale de la cámara para no
 * perder esos datos, y esta clase es la que los saca a la luz.
 *
 * <h2>Lo que no hace, a propósito</h2>
 * <ul>
 *   <li><b>No inventa.</b> Si la foto no trae fecha o ubicación, el dato queda
 *       vacío. Nunca se completa con la fecha de carga, porque sería afirmar
 *       que la foto se tomó cuando se subió, y en evidencia eso es falso.</li>
 *   <li><b>No rechaza la foto.</b> Un EXIF ausente, dañado o ilegible no
 *       impide cargarla. La foto sigue siendo evidencia; solo que sin fecha ni
 *       lugar comprobables, y así se dice.</li>
 *   <li><b>No toma (0, 0) por una ubicación.</b> Algunos teléfonos escriben
 *       ceros cuando no tienen señal de GPS. El punto 0, 0 está en el océano
 *       Atlántico, frente a África; ninguna entrega al Centro se recibe
 *       allí.</li>
 * </ul>
 *
 * <h2>Una fecha mal escrita cuenta como ausente</h2>
 * La fecha se lee con el formato del estándar ({@code 2026:09:23 14:03:00}) y
 * de forma estricta. La lectura de la librería es tolerante, y con un valor
 * inválido rellena lo que falta: la cámara del emulador de Android escribe
 * {@code 2026:09:24 24:34:39} (hora 24), y la librería lo convertía en el 1 de
 * enero de 2026 a medianoche. Registrar eso como fecha de captura sería
 * inventarla, así que una fecha que no se puede leer entera se trata como si no
 * viniera. Se descubrió el 23-09-2026 al recorrer la cámara en el emulador.
 *
 * <h2>La hora sin desfase</h2>
 * El EXIF guarda la hora local del teléfono. Los teléfonos recientes añaden
 * {@code OffsetTimeOriginal} (p. ej. {@code -05:00}), y si está, manda ese. Si
 * no está, la hora se interpreta en la zona del Centro ({@link Clock} de
 * {@code ZonaHoraria}), que es la del teléfono de un supervisor que recibe en el
 * Centro. Leerla en la zona de la JVM (UTC en el contenedor) la correría cinco
 * horas.
 */
@Component
public class LectorDeCaptura {

    private static final Logger log = LoggerFactory.getLogger(LectorDeCaptura.class);

    private static final DateTimeFormatter FECHA_Y_HORA =
            DateTimeFormatter.ofPattern("d/MM/yyyy 'a las' HH:mm", Locale.of("es", "CO"));

    /** El formato de DateTimeOriginal en el estándar EXIF, leído sin tolerancias. */
    private static final DateTimeFormatter FECHA_EXIF =
            DateTimeFormatter.ofPattern("uuuu:MM:dd HH:mm:ss").withResolverStyle(ResolverStyle.STRICT);

    private final ZoneId zonaDelCentro;

    public LectorDeCaptura(Clock reloj) {
        this.zonaDelCentro = reloj.getZone();
    }

    /**
     * Lo que la foto dice de sí misma. Cualquiera de los datos puede faltar, y
     * la ubicación viene completa o no viene: una coordenada sin la otra no es
     * un lugar.
     */
    public record Captura(Instant fecha, Double latitud, Double longitud) {

        public static final Captura SIN_DATOS = new Captura(null, null, null);

        public boolean tieneUbicacion() {
            return latitud != null && longitud != null;
        }
    }

    public Captura leer(byte[] contenido) {
        Metadata metadatos;
        try {
            metadatos = ImageMetadataReader.readMetadata(new ByteArrayInputStream(contenido), contenido.length);
        } catch (ImageProcessingException | IOException | RuntimeException e) {
            // Debug y no warn: una foto sin EXIF legible es un caso normal (una
            // captura de pantalla, un PNG exportado), no una anomalía del sistema.
            log.debug("No se pudo leer el EXIF de la foto; se carga sin fecha ni ubicación.", e);
            return Captura.SIN_DATOS;
        }
        GeoLocation lugar = ubicacionDe(metadatos);
        return new Captura(
                fechaDe(metadatos),
                lugar == null ? null : lugar.getLatitude(),
                lugar == null ? null : lugar.getLongitude());
    }

    /**
     * La frase que queda en el registro del contrato. Dice lo que falta, en vez
     * de callarlo: «sin ubicación» también es información para quien revisa la
     * evidencia.
     */
    public String describir(Captura captura) {
        String fecha = captura.fecha() == null
                ? null
                : "tomada el " + FECHA_Y_HORA.format(captura.fecha().atZone(zonaDelCentro));
        String lugar = captura.tieneUbicacion()
                ? String.format(Locale.ROOT, "en %.5f, %.5f", captura.latitud(), captura.longitud())
                : null;

        if (fecha != null && lugar != null) {
            return "Foto " + fecha + " " + lugar + ".";
        }
        if (fecha != null) {
            return "Foto " + fecha + "; no trae ubicación.";
        }
        if (lugar != null) {
            return "Foto tomada " + lugar + "; no trae fecha de captura.";
        }
        return "La foto no trae fecha de captura ni ubicación.";
    }

    private Instant fechaDe(Metadata metadatos) {
        for (ExifSubIFDDirectory exif : metadatos.getDirectoriesOfType(ExifSubIFDDirectory.class)) {
            String fecha = exif.getString(ExifSubIFDDirectory.TAG_DATETIME_ORIGINAL);
            if (fecha == null) {
                continue;
            }
            try {
                LocalDateTime local = LocalDateTime.parse(fecha.trim(), FECHA_EXIF);
                return local.atZone(zonaDe(exif.getString(ExifSubIFDDirectory.TAG_TIME_ZONE_ORIGINAL))).toInstant();
            } catch (DateTimeException e) {
                log.debug("La foto trae una fecha de captura ilegible ({}); se trata como ausente.", fecha);
            }
        }
        return null;
    }

    /** El desfase que escribió el teléfono ({@code -05:00}) o, si no lo hay o es ilegible, la zona del Centro. */
    private ZoneId zonaDe(String desfase) {
        if (desfase != null) {
            try {
                return ZoneOffset.of(desfase.trim());
            } catch (DateTimeException e) {
                log.debug("Desfase horario ilegible en la foto ({}); se usa la zona del Centro.", desfase);
            }
        }
        return zonaDelCentro;
    }

    private static GeoLocation ubicacionDe(Metadata metadatos) {
        for (GpsDirectory gps : metadatos.getDirectoriesOfType(GpsDirectory.class)) {
            GeoLocation lugar = gps.getGeoLocation();
            if (lugar != null && !lugar.isZero()
                    && Double.isFinite(lugar.getLatitude()) && Double.isFinite(lugar.getLongitude())
                    && Math.abs(lugar.getLatitude()) <= 90 && Math.abs(lugar.getLongitude()) <= 180) {
                return lugar;
            }
        }
        return null;
    }
}
