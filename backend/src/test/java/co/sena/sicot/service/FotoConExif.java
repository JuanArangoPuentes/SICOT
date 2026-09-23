package co.sena.sicot.service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Fabrica fotos JPEG con el EXIF que escribe un teléfono: fecha de captura,
 * desfase horario y posición GPS, cada uno opcional.
 *
 * <p>Existe para las pruebas automáticas y nada más. No reemplaza la
 * verificación con fotos reales de teléfono, que es la que dice si la lectura
 * sirve de verdad; esto solo fija el comportamiento del lector ante cada
 * combinación de datos presentes y ausentes.
 *
 * <p>Se escribe el EXIF a mano, byte a byte, en lugar de añadir una librería de
 * escritura: la única de Apache (Commons Imaging) sigue en versión alfa, y un
 * bloque EXIF mínimo son unas pocas decenas de bytes con una estructura fija.
 * Referencia: EXIF 2.32 (CIPA DC-008), que monta sobre TIFF 6.0.
 */
public final class FotoConExif {

    // Tipos TIFF de cada entrada de un directorio (IFD).
    private static final short ASCII = 2;
    private static final short LONG = 4;
    private static final short RATIONAL = 5;

    private String fechaOriginal;
    private String desfase;
    private Double latitud;
    private Double longitud;

    public static FotoConExif nueva() {
        return new FotoConExif();
    }

    /** Hora local del teléfono, en el formato del EXIF: {@code 2026:09:23 14:03:00}. */
    public FotoConExif tomadaEl(String fechaOriginal) {
        this.fechaOriginal = fechaOriginal;
        return this;
    }

    /** {@code OffsetTimeOriginal}, p. ej. {@code -05:00}. Los teléfonos viejos no lo escriben. */
    public FotoConExif conDesfase(String desfase) {
        this.desfase = desfase;
        return this;
    }

    public FotoConExif en(double latitud, double longitud) {
        this.latitud = latitud;
        this.longitud = longitud;
        return this;
    }

    /** Una foto real de 16×16 píxeles codificada por ImageIO, sin EXIF. */
    public static byte[] jpegSinExif() {
        try {
            ByteArrayOutputStream salida = new ByteArrayOutputStream();
            ImageIO.write(new BufferedImage(16, 16, BufferedImage.TYPE_INT_RGB), "jpg", salida);
            return salida.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static byte[] png() {
        try {
            ByteArrayOutputStream salida = new ByteArrayOutputStream();
            ImageIO.write(new BufferedImage(16, 16, BufferedImage.TYPE_INT_RGB), "png", salida);
            return salida.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * El JPEG de {@link #jpegSinExif()} con un segmento APP1 «Exif» insertado
     * justo después del marcador de inicio (FFD8), que es donde lo ponen las
     * cámaras.
     */
    public byte[] jpeg() {
        byte[] tiff = tiff();
        byte[] carga = concatenar("Exif\0\0".getBytes(StandardCharsets.US_ASCII), tiff);
        byte[] base = jpegSinExif();

        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        salida.write(base, 0, 2);                    // FFD8
        salida.write(0xFF);
        salida.write(0xE1);                          // APP1
        int longitud = carga.length + 2;             // incluye los dos bytes del largo
        salida.write((longitud >> 8) & 0xFF);
        salida.write(longitud & 0xFF);
        salida.writeBytes(carga);
        salida.write(base, 2, base.length - 2);
        return salida.toByteArray();
    }

    private byte[] tiff() {
        List<Entrada> exif = new ArrayList<>();
        if (fechaOriginal != null) {
            exif.add(ascii(0x9003, fechaOriginal));   // DateTimeOriginal
        }
        if (desfase != null) {
            exif.add(ascii(0x9011, desfase));         // OffsetTimeOriginal
        }
        List<Entrada> gps = new ArrayList<>();
        if (latitud != null) {
            gps.add(ascii(0x0001, latitud >= 0 ? "N" : "S"));
            gps.add(gradosMinutosSegundos(0x0002, Math.abs(latitud)));
            gps.add(ascii(0x0003, longitud >= 0 ? "E" : "W"));
            gps.add(gradosMinutosSegundos(0x0004, Math.abs(longitud)));
        }

        // Cabecera TIFF (8 bytes) → IFD0 → IFD de EXIF → IFD de GPS. El IFD0 solo
        // lleva los dos punteros, así que su tamaño no depende de nada.
        int desplazamientoIfd0 = 8;
        int tamanioIfd0 = tamanio(List.of(puntero(0x8769, 0), puntero(0x8825, 0)));
        int desplazamientoExif = desplazamientoIfd0 + tamanioIfd0;
        int desplazamientoGps = desplazamientoExif + tamanio(exif);

        List<Entrada> ifd0 = List.of(
                puntero(0x8769, desplazamientoExif),  // ExifIFDPointer
                puntero(0x8825, desplazamientoGps));  // GPSInfoIFDPointer

        ByteBuffer cabecera = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);
        cabecera.put((byte) 'I').put((byte) 'I').putShort((short) 42).putInt(desplazamientoIfd0);

        return concatenar(cabecera.array(),
                directorio(ifd0, desplazamientoIfd0),
                directorio(exif, desplazamientoExif),
                directorio(gps, desplazamientoGps));
    }

    private record Entrada(int etiqueta, short tipo, int cantidad, byte[] valor) {
    }

    private static Entrada ascii(int etiqueta, String texto) {
        byte[] conNulo = (texto + "\0").getBytes(StandardCharsets.US_ASCII);
        return new Entrada(etiqueta, ASCII, conNulo.length, conNulo);
    }

    private static Entrada puntero(int etiqueta, int desplazamiento) {
        return new Entrada(etiqueta, LONG, 1,
                ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(desplazamiento).array());
    }

    /** Grados, minutos y segundos como tres fracciones, que es como los guarda el GPS. */
    private static Entrada gradosMinutosSegundos(int etiqueta, double decimal) {
        int grados = (int) decimal;
        double restoMinutos = (decimal - grados) * 60;
        int minutos = (int) restoMinutos;
        long segundosPorMil = Math.round((restoMinutos - minutos) * 60 * 1000);
        ByteBuffer valor = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN);
        valor.putInt(grados).putInt(1).putInt(minutos).putInt(1).putInt((int) segundosPorMil).putInt(1000);
        return new Entrada(etiqueta, RATIONAL, 3, valor.array());
    }

    /** Número de entradas, 12 bytes por entrada, puntero al siguiente IFD y los valores que no caben en 4 bytes. */
    private static int tamanio(List<Entrada> entradas) {
        int datos = 0;
        for (Entrada e : entradas) {
            if (e.valor().length > 4) {
                datos += e.valor().length + (e.valor().length % 2);
            }
        }
        return 2 + 12 * entradas.size() + 4 + datos;
    }

    private static byte[] directorio(List<Entrada> entradas, int desplazamiento) {
        int inicioDeDatos = desplazamiento + 2 + 12 * entradas.size() + 4;
        ByteBuffer indice = ByteBuffer.allocate(2 + 12 * entradas.size() + 4).order(ByteOrder.LITTLE_ENDIAN);
        ByteArrayOutputStream datos = new ByteArrayOutputStream();
        indice.putShort((short) entradas.size());
        for (Entrada e : entradas) {
            indice.putShort((short) e.etiqueta()).putShort(e.tipo()).putInt(e.cantidad());
            if (e.valor().length <= 4) {
                indice.put(Arrays.copyOf(e.valor(), 4));
            } else {
                indice.putInt(inicioDeDatos + datos.size());
                datos.writeBytes(e.valor());
                if (e.valor().length % 2 == 1) {
                    datos.write(0);                   // los valores empiezan en posición par
                }
            }
        }
        indice.putInt(0);                             // no hay IFD siguiente
        return concatenar(indice.array(), datos.toByteArray());
    }

    private static byte[] concatenar(byte[]... partes) {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        for (byte[] parte : partes) {
            salida.writeBytes(parte);
        }
        return salida.toByteArray();
    }
}
