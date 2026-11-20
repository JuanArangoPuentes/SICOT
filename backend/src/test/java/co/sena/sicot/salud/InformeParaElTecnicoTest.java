package co.sena.sicot.salud;

import co.sena.sicot.service.EmailService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.ObjectProvider;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * El informe al técnico es lo último que queda cuando el sistema ya intentó
 * repararse y no pudo. Era la clase menos probada de todo el backend junto con
 * el planificador: 12,3 % de líneas.
 *
 * <h2>Por qué eso era grave y no una estadística</h2>
 * Esta clase existe por una lección concreta, la del 11-11-2026: el servidor
 * del Centro llevaba seis días sin respaldo, el sistema <b>sí lo había
 * detectado</b> y lo había escrito en el log, y no sirvió de nada porque nadie
 * lee un log mientras todo parece funcionar. El informe es la respuesta a eso.
 *
 * <p>Es decir: es el único componente cuyo trabajo es <b>que alguien se
 * entere</b>, y corre siempre en el camino de error de otro. Si falla, falla
 * callado y por segunda vez el Centro se queda sin saber que algo está roto.
 * Sus dos reglas duras —no lanzar nunca, y no intentar avisar por el canal que
 * está caído— no tenían ni una prueba.
 */
class InformeParaElTecnicoTest {

    private static final SaludDelSistema.Incidencia INCIDENCIA = new SaludDelSistema.Incidencia(
            EstadoDeServicio.CAIDO, 3, Instant.now().minus(Duration.ofHours(2)),
            "el más reciente tiene 119 h de antigüedad", 2, false);

    @Test
    @DisplayName("el informe se escribe en un archivo que sobrevive al reinicio del contenedor")
    void seEscribeUnArchivoPorIncidencia(@TempDir Path directorio) {
        InformeParaElTecnico informe = new InformeParaElTecnico(
                sinCorreo(), "tecnico@ejemplo.co", directorio.toString());

        informe.informarCaida(Servicio.RESPALDO, INCIDENCIA);

        List<Path> archivos = listar(directorio);
        assertThat(archivos)
                .as("el log de Docker se pierde al recrear el contenedor; el archivo es lo que queda")
                .hasSize(1);
        assertThat(archivos.get(0).getFileName().toString()).endsWith("-respaldo.txt");
        assertThat(leer(archivos.get(0)))
                .contains("119 h de antigüedad")
                // El informe sirve si dice QUÉ comprobar; si solo dijera «falló
                // el respaldo», el técnico tendría que empezar por donde ya
                // empezó la última vez.
                .isNotBlank();
    }

    @Test
    @DisplayName("el informe se manda por correo a quien puede arreglarlo")
    void seMandaPorCorreo(@TempDir Path directorio) {
        EmailService email = mock(EmailService.class);
        InformeParaElTecnico informe = new InformeParaElTecnico(
                proveedor(email), "tecnico@ejemplo.co", directorio.toString());

        informe.informarCaida(Servicio.RESPALDO, INCIDENCIA);

        verify(email).enviar(eq("tecnico@ejemplo.co"), anyString(), anyString());
    }

    @Test
    @DisplayName("cuando lo caído es el correo, el informe NO se manda por correo")
    void noSeAvisaPorElCanalQueEstaCaido(@TempDir Path directorio) {
        EmailService email = mock(EmailService.class);
        InformeParaElTecnico informe = new InformeParaElTecnico(
                proveedor(email), "tecnico@ejemplo.co", directorio.toString());

        informe.informarCaida(Servicio.CORREO, INCIDENCIA);

        // Por el mismo motivo por el que no se telefonea para avisar de que el
        // teléfono no funciona. Y porque el fallo del envío registraría otro
        // fallo de CORREO, que dispararía otro informe: un ciclo.
        verify(email, never()).enviar(anyString(), anyString(), anyString());
        assertThat(listar(directorio))
                .as("el archivo sí se escribe: es el canal que sigue en pie")
                .hasSize(1);
    }

    @Test
    @DisplayName("si el envío del correo falla, el informe NO propaga la excepción")
    void unFalloDelCorreoNoTapaElFalloOriginal(@TempDir Path directorio) {
        EmailService email = mock(EmailService.class);
        doThrow(new RuntimeException("el servidor SMTP rechazó la conexión"))
                .when(email).enviar(anyString(), anyString(), anyString());
        InformeParaElTecnico informe = new InformeParaElTecnico(
                proveedor(email), "tecnico@ejemplo.co", directorio.toString());

        assertThatCode(() -> informe.informarCaida(Servicio.RESPALDO, INCIDENCIA))
                .as("esto corre en el camino de error de otro componente: lanzar taparía el fallo original")
                .doesNotThrowAnyException();
        assertThat(listar(directorio)).hasSize(1);
    }

    @Test
    @DisplayName("si el directorio de informes no se puede escribir, tampoco propaga")
    void unDirectorioInservibleNoPropaga(@TempDir Path directorio) throws Exception {
        // Un archivo donde debería ir el directorio: createDirectories falla.
        Path comoArchivo = directorio.resolve("informes");
        Files.writeString(comoArchivo, "esto no es un directorio");
        EmailService email = mock(EmailService.class);
        InformeParaElTecnico informe = new InformeParaElTecnico(
                proveedor(email), "tecnico@ejemplo.co", comoArchivo.toString());

        assertThatCode(() -> informe.informarCaida(Servicio.RESPALDO, INCIDENCIA))
                .doesNotThrowAnyException();
        // El correo sale igual: perder un canal no puede perder los otros.
        verify(email).enviar(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("sin destinatario ni directorio configurados, el informe sigue sin romper nada")
    void sinConfiguracionNoRompe() {
        InformeParaElTecnico informe = new InformeParaElTecnico(sinCorreo(), "", "");

        assertThatCode(() -> informe.informarCaida(Servicio.ALMACENAMIENTO, INCIDENCIA))
                .doesNotThrowAnyException();
        assertThatCode(() -> informe.informarRecuperacion(Servicio.ALMACENAMIENTO, INCIDENCIA))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("la recuperación también avisa: nadie debe salir a buscar un fallo que ya no está")
    void laRecuperacionCierraElAviso(@TempDir Path directorio) {
        EmailService email = mock(EmailService.class);
        InformeParaElTecnico informe = new InformeParaElTecnico(
                proveedor(email), "tecnico@ejemplo.co", directorio.toString());

        informe.informarRecuperacion(Servicio.IA, INCIDENCIA);

        verify(email).enviar(eq("tecnico@ejemplo.co"),
                eq("SICOT: el asistente de IA volvió a funcionar"), anyString());
        // La recuperación no escribe archivo: lo que hay que conservar entre
        // reinicios es el fallo, no la buena noticia.
        assertThat(listar(directorio)).isEmpty();
    }

    // ── Utilidades ──────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private static ObjectProvider<EmailService> proveedor(EmailService email) {
        ObjectProvider<EmailService> proveedor = mock(ObjectProvider.class);
        when(proveedor.getObject()).thenReturn(email);
        return proveedor;
    }

    @SuppressWarnings("unchecked")
    private static ObjectProvider<EmailService> sinCorreo() {
        ObjectProvider<EmailService> proveedor = mock(ObjectProvider.class);
        when(proveedor.getObject()).thenThrow(new IllegalStateException("sin EmailService en esta prueba"));
        return proveedor;
    }

    private static List<Path> listar(Path directorio) {
        try (var archivos = Files.list(directorio)) {
            return archivos.filter(Files::isRegularFile).toList();
        } catch (Exception e) {
            return List.of();
        }
    }

    private static String leer(Path archivo) {
        try {
            return Files.readString(archivo);
        } catch (Exception e) {
            throw new AssertionError("no se pudo leer el informe", e);
        }
    }
}
