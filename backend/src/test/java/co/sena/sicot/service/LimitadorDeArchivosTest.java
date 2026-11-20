package co.sena.sicot.service;

import co.sena.sicot.exception.DemasiadasSolicitudesException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Lo que este limitador tiene que garantizar es <b>una sola cosa</b>: que el
 * número de peticiones que manejan bytes a la vez no pueda pasar de los
 * permisos configurados. De eso depende que el peor caso de memoria del
 * backend sea una cifra calculable y no {@code hilos × 20 MB}.
 *
 * <p>Las pruebas usan hilos de verdad, y no dos llamadas seguidas, porque el
 * escenario que importa es exactamente el concurrente: una tras otra siempre
 * pasan, con permisos o sin ellos.
 */
class LimitadorDeArchivosTest {

    @Test
    @DisplayName("con cupo libre, la operación se ejecuta y devuelve su resultado")
    void conCupoSeEjecuta() {
        LimitadorDeArchivos limitador = new LimitadorDeArchivos(4);

        assertThat(limitador.ejecutar("prueba", () -> "hecho")).isEqualTo("hecho");
        assertThat(limitador.cupoDisponible()).isEqualTo(4);
    }

    @Test
    @DisplayName("el permiso se devuelve aunque la operación falle: una excepción no puede consumir cupo para siempre")
    void elPermisoSeLiberaAlFallar() {
        LimitadorDeArchivos limitador = new LimitadorDeArchivos(1);

        assertThatThrownBy(() -> limitador.ejecutar("prueba", () -> {
            throw new IllegalStateException("fallo dentro de la operación");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(limitador.cupoDisponible())
                .as("sin liberar en el finally, el cupo se agotaría hasta el siguiente reinicio")
                .isEqualTo(1);
        assertThat(limitador.ejecutar("prueba", () -> "sigue atendiendo")).isEqualTo("sigue atendiendo");
    }

    @Test
    @DisplayName("la petición que llega sin cupo recibe 429 con Retry-After, no se queda esperando")
    void sinCupoSeRechazaCon429() throws Exception {
        LimitadorDeArchivos limitador = new LimitadorDeArchivos(1);
        CountDownLatch dentro = new CountDownLatch(1);
        CountDownLatch suelta = new CountDownLatch(1);

        ExecutorService hilos = Executors.newSingleThreadExecutor();
        try {
            hilos.submit(() -> limitador.ejecutar("ocupa el único permiso", () -> {
                dentro.countDown();
                try {
                    suelta.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return null;
            }));
            assertThat(dentro.await(5, TimeUnit.SECONDS)).isTrue();

            long antes = System.nanoTime();
            assertThatThrownBy(() -> limitador.ejecutar("la segunda", () -> "no debería llegar aquí"))
                    .isInstanceOf(DemasiadasSolicitudesException.class)
                    .hasMessageContaining("Espere unos segundos")
                    // El mensaje tiene que decir que el trabajo no se perdió: en
                    // la práctica quien recibe esto es un supervisor subiendo una
                    // foto en bodega, y «429» a secas le dice que lo intente otra
                    // vez sin saber si ya se guardó.
                    .hasMessageContaining("no se perdió");
            long esperaMs = (System.nanoTime() - antes) / 1_000_000;

            assertThat(esperaMs)
                    .as("rechaza rápido: una petición encolada sigue costando un hilo de Tomcat")
                    .isLessThan(4_000);

            suelta.countDown();
        } finally {
            hilos.shutdownNow();
            assertThat(hilos.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    @DisplayName("nunca hay más operaciones dentro a la vez que permisos configurados")
    void nuncaSeSuperaElTechoDeConcurrencia() throws Exception {
        int permisos = 3;
        int peticiones = 40;
        LimitadorDeArchivos limitador = new LimitadorDeArchivos(permisos);

        AtomicInteger dentroAhora = new AtomicInteger();
        AtomicInteger maximoVisto = new AtomicInteger();
        AtomicInteger rechazadas = new AtomicInteger();
        CountDownLatch listas = new CountDownLatch(peticiones);
        CountDownLatch salida = new CountDownLatch(1);

        ExecutorService hilos = Executors.newFixedThreadPool(peticiones);
        try {
            for (int i = 0; i < peticiones; i++) {
                hilos.submit(() -> {
                    try {
                        salida.await(5, TimeUnit.SECONDS);
                        limitador.ejecutar("carga simultánea", () -> {
                            int ahora = dentroAhora.incrementAndGet();
                            maximoVisto.accumulateAndGet(ahora, Math::max);
                            try {
                                // Suficiente para que los hilos se solapen de
                                // verdad; sin esto el planificador podría
                                // serializarlos y la prueba no probaría nada.
                                Thread.sleep(25);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            }
                            dentroAhora.decrementAndGet();
                            return null;
                        });
                    } catch (DemasiadasSolicitudesException e) {
                        rechazadas.incrementAndGet();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        listas.countDown();
                    }
                });
            }
            salida.countDown();
            assertThat(listas.await(30, TimeUnit.SECONDS)).isTrue();
        } finally {
            hilos.shutdownNow();
        }

        assertThat(maximoVisto.get())
                .as("es la garantía entera: el peor caso de memoria es permisos x tamaño, no hilos x tamaño")
                .isLessThanOrEqualTo(permisos);
        assertThat(dentroAhora.get()).isZero();
        assertThat(maximoVisto.get() + rechazadas.get())
                .as("algo entró: si todo se rechazara, el limitador no estaría dejando trabajar a nadie")
                .isPositive();
        assertThat(limitador.cupoDisponible()).isEqualTo(permisos);
    }
}
