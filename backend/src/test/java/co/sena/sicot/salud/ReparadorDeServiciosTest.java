package co.sena.sicot.salud;

import co.sena.sicot.ia.OllamaClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Lo que el sistema intenta por su cuenta antes de molestar a una persona.
 * Estaba al 16,7 % de cobertura, y es uno de los dos componentes que corren
 * solos, de noche, en el servidor del Centro, sin nadie mirando.
 *
 * <h2>Lo que estas pruebas fijan</h2>
 * Las tres reglas que hacen que este reparador sea útil en vez de ruidoso:
 * <ol>
 *   <li>Con todo en orden <b>no hace ni una llamada</b>. Un reparador que
 *       sondea cada dos minutos aunque no haya nada roto es un reparador que
 *       alguien acabará apagando.</li>
 *   <li>Solo toca lo que se puede reparar con una petición HTTP. Un servidor de
 *       correo que rechaza la conexión o un respaldo que no se está ejecutando
 *       no se arreglan insistiendo.</li>
 *   <li>Si un intento explota, <b>sigue con el siguiente servicio</b>. Sin eso,
 *       un fallo al reparar la IA dejaría sin reparar todo lo demás, y el
 *       síntoma sería «el sistema no se recupera solo» sin nada que lo explique.</li>
 * </ol>
 */
class ReparadorDeServiciosTest {

    private SaludDelSistema salud;
    private OllamaClient ollama;
    private ReparadorDeServicios reparador;

    @BeforeEach
    void prepararDobles() {
        salud = mock(SaludDelSistema.class);
        ollama = mock(OllamaClient.class);
        reparador = new ReparadorDeServicios(salud, ollama);
    }

    @Test
    @DisplayName("sin incidencias abiertas no se intenta nada: no hay reparación que hacer")
    void sinIncidenciasNoHaceNada() {
        when(salud.incidenciasAbiertas()).thenReturn(Map.of());

        reparador.intentarLevantarLoCaido();

        verify(ollama, never()).intentarLevantar();
        verify(salud, never()).anotarReparacion(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("la IA caída se intenta levantar, y si sube se cierra la incidencia")
    void laIaQueSubeCierraLaIncidencia() {
        when(salud.incidenciasAbiertas()).thenReturn(Map.of(Servicio.IA, incidencia()));
        when(ollama.intentarLevantar()).thenReturn(true);

        reparador.intentarLevantarLoCaido();

        verify(salud).anotarReparacion(Servicio.IA);
        verify(ollama).intentarLevantar();
        // Esto es lo que quita el aviso de la pantalla del supervisor y le dice
        // al técnico que ya no hace falta que venga.
        verify(salud).exito(Servicio.IA);
        verify(salud, never()).fallo(eq(Servicio.IA), anyString());
    }

    @Test
    @DisplayName("si el intento no levanta la IA, se anota como otro fallo y no como éxito")
    void laIaQueNoSubeSumaUnFallo() {
        when(salud.incidenciasAbiertas()).thenReturn(Map.of(Servicio.IA, incidencia()));
        when(ollama.intentarLevantar()).thenReturn(false);

        reparador.intentarLevantarLoCaido();

        verify(salud).fallo(eq(Servicio.IA), anyString());
        verify(salud, never()).exito(Servicio.IA);
    }

    @Test
    @DisplayName("el correo y el respaldo no se tocan: no se arreglan insistiendo")
    void loQueNoSeRepararSeDejaEnPaz() {
        when(salud.incidenciasAbiertas()).thenReturn(Map.of(
                Servicio.CORREO, incidencia(),
                Servicio.RESPALDO, incidencia(),
                Servicio.ALMACENAMIENTO, incidencia()));

        reparador.intentarLevantarLoCaido();

        verify(ollama, never()).intentarLevantar();
        verify(salud, never()).anotarReparacion(Servicio.CORREO);
        verify(salud, never()).anotarReparacion(Servicio.RESPALDO);
        verify(salud, never()).anotarReparacion(Servicio.ALMACENAMIENTO);
    }

    @Test
    @DisplayName("un intento que explota no impide reparar el resto ni cancela el sondeo")
    void unIntentoQueExplotaNoDetieneLaRonda() {
        when(salud.incidenciasAbiertas()).thenReturn(Map.of(
                Servicio.IA, incidencia(),
                Servicio.CORREO, incidencia()));
        when(ollama.intentarLevantar()).thenThrow(new RuntimeException("Ollama respondió con un 500"));

        assertThatCode(() -> reparador.intentarLevantarLoCaido())
                .as("este método es @Scheduled: dejar escapar la excepción cancelaría las rondas futuras")
                .doesNotThrowAnyException();

        verify(salud).anotarReparacion(Servicio.IA);
    }

    private static SaludDelSistema.Incidencia incidencia() {
        return new SaludDelSistema.Incidencia(EstadoDeServicio.CAIDO, 2,
                Instant.now().minus(Duration.ofMinutes(30)), "no responde", 0, false);
    }
}
