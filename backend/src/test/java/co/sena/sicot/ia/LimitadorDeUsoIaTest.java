package co.sena.sicot.ia;

import co.sena.sicot.entity.Usuario;
import co.sena.sicot.entity.enums.Rol;
import co.sena.sicot.exception.DemasiadasSolicitudesException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * El freno que impide que el Copiloto se lleve por delante al resto del sistema.
 *
 * <p>Importa probarlo bien porque su fallo no se parece a un fallo de la IA:
 * cada llamada a Ollama retiene un hilo de Tomcat, y esos hilos los comparte
 * TODA la aplicación. Si este limitador no acota, un pico de uso del chat deja
 * sin hilos al login y a los listados — el síntoma aparece lejos de la causa.
 */
class LimitadorDeUsoIaTest {

    @AfterEach
    void limpiarAutenticacion() {
        SecurityContextHolder.clearContext();
    }

    private void autenticarComo(long id) {
        Usuario usuario = new Usuario();
        usuario.setId(id);
        usuario.setNombre("Usuario " + id);
        usuario.setEmail("usuario" + id + "@soy.sena.edu.co");
        usuario.setRol(Rol.SUPERVISOR);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(usuario, null, List.of()));
    }

    @Test
    void dejaPasarUnaPeticionNormal() {
        LimitadorDeUsoIa limitador = new LimitadorDeUsoIa(2, 10);
        autenticarComo(1);

        assertThat(limitador.ejecutar("prueba", () -> "respuesta")).isEqualTo("respuesta");
    }

    @Test
    void bloqueaAlSuperarLasPeticionesPorMinutoDeUnUsuario() {
        LimitadorDeUsoIa limitador = new LimitadorDeUsoIa(5, 3);
        autenticarComo(1);

        for (int i = 0; i < 3; i++) {
            limitador.ejecutar("prueba", () -> "ok");
        }

        assertThatThrownBy(() -> limitador.ejecutar("prueba", () -> "ok"))
                .isInstanceOf(DemasiadasSolicitudesException.class)
                .hasMessageContaining("demasiadas consultas");
    }

    /**
     * El límite es POR CUENTA: que una persona agote su cupo no puede dejar sin
     * copiloto al resto del centro de formación.
     */
    @Test
    void elLimiteDeUnUsuarioNoAfectaAOtro() {
        LimitadorDeUsoIa limitador = new LimitadorDeUsoIa(5, 2);

        autenticarComo(1);
        limitador.ejecutar("prueba", () -> "ok");
        limitador.ejecutar("prueba", () -> "ok");
        assertThatThrownBy(() -> limitador.ejecutar("prueba", () -> "ok"))
                .isInstanceOf(DemasiadasSolicitudesException.class);

        autenticarComo(2);
        assertThatCode(() -> limitador.ejecutar("prueba", () -> "ok")).doesNotThrowAnyException();
    }

    /** El 429 debe decir cuánto esperar: es lo que alimenta la cabecera Retry-After. */
    @Test
    void elRechazoIndicaCuantoEsperar() {
        LimitadorDeUsoIa limitador = new LimitadorDeUsoIa(5, 1);
        autenticarComo(1);
        limitador.ejecutar("prueba", () -> "ok");

        DemasiadasSolicitudesException ex = org.junit.jupiter.api.Assertions.assertThrows(
                DemasiadasSolicitudesException.class,
                () -> limitador.ejecutar("prueba", () -> "ok"));

        assertThat(ex.getSegundosDeEspera()).isPositive();
    }

    /**
     * Sin usuario autenticado no hay a quién contarle las peticiones. No debe
     * reventar: el semáforo de concurrencia sigue aplicando.
     */
    @Test
    void funcionaSinUsuarioAutenticado() {
        LimitadorDeUsoIa limitador = new LimitadorDeUsoIa(2, 1);

        assertThatCode(() -> limitador.ejecutar("prueba", () -> "ok")).doesNotThrowAnyException();
        assertThatCode(() -> limitador.ejecutar("prueba", () -> "ok")).doesNotThrowAnyException();
    }

    /**
     * El permiso del semáforo se libera aunque la tarea falle. Si no, cada error
     * de Ollama reduciría el cupo de forma permanente hasta el siguiente
     * reinicio, y el sistema acabaría rechazando toda petición de IA sin que
     * nada explique por qué.
     */
    @Test
    void libera_el_cupo_aunque_la_tarea_lance() {
        LimitadorDeUsoIa limitador = new LimitadorDeUsoIa(1, 100);
        autenticarComo(1);

        for (int i = 0; i < 3; i++) {
            assertThatThrownBy(() -> limitador.<String>ejecutar("prueba", () -> {
                throw new IaNoDisponibleException("Ollama caído");
            })).isInstanceOf(IaNoDisponibleException.class);
        }

        // Si el cupo se hubiera fugado, esta llamada fallaría con 429.
        assertThatCode(() -> limitador.ejecutar("prueba", () -> "ok")).doesNotThrowAnyException();
    }

    /**
     * Con un solo permiso, dos peticiones simultáneas no pueden estar dentro a
     * la vez. La segunda espera un poco y, si no hay cupo, se rechaza rápido en
     * vez de encolarse — una petición encolada sigue costando un hilo.
     */
    @Test
    void acotaLaConcurrenciaGlobal() throws Exception {
        LimitadorDeUsoIa limitador = new LimitadorDeUsoIa(1, 100);
        AtomicInteger dentroALaVez = new AtomicInteger();
        AtomicInteger maximoObservado = new AtomicInteger();
        AtomicInteger rechazos = new AtomicInteger();
        CountDownLatch listas = new CountDownLatch(4);
        CountDownLatch terminadas = new CountDownLatch(4);

        for (int i = 0; i < 4; i++) {
            final long id = i + 1;
            Thread hilo = new Thread(() -> {
                autenticarComo(id);
                listas.countDown();
                try {
                    limitador.ejecutar("prueba", () -> {
                        int ahora = dentroALaVez.incrementAndGet();
                        maximoObservado.updateAndGet(m -> Math.max(m, ahora));
                        try {
                            Thread.sleep(150);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                        dentroALaVez.decrementAndGet();
                        return "ok";
                    });
                } catch (DemasiadasSolicitudesException e) {
                    rechazos.incrementAndGet();
                } finally {
                    SecurityContextHolder.clearContext();
                    terminadas.countDown();
                }
            });
            hilo.start();
        }

        assertThat(listas.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(terminadas.await(15, TimeUnit.SECONDS)).isTrue();

        // Lo esencial: jamás hubo dos ejecuciones simultáneas dentro del semáforo.
        assertThat(maximoObservado.get()).isEqualTo(1);
        // Y todas terminaron: o entraron, o fueron rechazadas con 429.
        assertThat(rechazos.get()).isBetween(0, 3);
    }
}
