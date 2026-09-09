package co.sena.sicot;

import co.sena.sicot.ia.LimitadorDeUsoIa;
import co.sena.sicot.security.LoginAttemptService;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Base de toda prueba de integración que arranca la aplicación completa.
 *
 * <h2>El problema que resuelve</h2>
 * Todas las pruebas de integración comparten una única base H2
 * ({@code DB_CLOSE_DELAY=-1}) y un único contexto de Spring por combinación de
 * beans. Sin nada que las separe, lo que una prueba escribe sigue ahí para la
 * siguiente: el usuario que crea {@code AuthIntegrationTest} aparece en el
 * listado que cuenta otra clase, y los contadores en memoria de
 * {@link LoginAttemptService} —que no viven en la base y por tanto ningún
 * {@code rollback} deshace— cruzan de una clase a otra y pueden bloquear un
 * login que la prueba daba por bueno.
 *
 * <p>El resultado era una suite <b>dependiente del orden por construcción</b>:
 * que pasara en verde no decía que fuera correcta, decía que el orden elegido
 * esa vez funcionó. Con la CI bloqueando el merge sobre esta misma suite, eso
 * dejó de ser una molestia local.
 *
 * <h2>Cómo lo resuelve</h2>
 * Antes de <b>cada</b> prueba se devuelve el sistema a su estado de arranque:
 * se vacían todas las tablas, se vuelve a sembrar por el mismo camino que usa
 * la aplicación de verdad ({@code DataInitializer}), y se limpia el estado que
 * vive en memoria y no en la base.
 *
 * <h2>Por qué vaciar y resembrar, y no {@code @Transactional}</h2>
 * Poner {@code @Transactional} en la clase de prueba es más rápido —deshace por
 * rollback en vez de borrar— pero mete el código bajo prueba dentro de la
 * transacción del test. En SICOT eso cambiaría justo lo que varias de estas
 * pruebas verifican: la huella de integridad del documento firmado, el bloqueo
 * optimista y el comportamiento ante un fallo a mitad de operación dependen de
 * commits reales. Se prefirió el método más lento que no altera la semántica de
 * lo que se está probando.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class PruebaDeIntegracion {

    @Autowired
    private DataSource dataSource;

    /**
     * El mismo sembrador que usa la aplicación en los perfiles {@code dev} y
     * {@code test}. Se reutiliza a propósito en vez de copiar aquí los tres
     * usuarios: si mañana cambia una cuenta semilla, las pruebas la recogen
     * solas y no hay dos definiciones que puedan contradecirse.
     */
    @Autowired
    @Qualifier("seedUsers")
    private CommandLineRunner sembradorDeUsuarios;

    @Autowired
    private LoginAttemptService loginAttemptService;

    @Autowired
    private LimitadorDeUsoIa limitadorDeUsoIa;

    @BeforeEach
    void devolverElSistemaASuEstadoDeArranque() throws Exception {
        vaciarTodasLasTablas();
        sembradorDeUsuarios.run();

        // Estado en memoria: no está en la base, así que vaciar tablas no lo
        // alcanza. Son las dos fuentes de contagio entre clases que quedaban.
        loginAttemptService.reiniciar();
        limitadorDeUsoIa.reiniciar();

        // Una prueba que autentica a mano y no limpia dejaría al siguiente test
        // corriendo con la identidad de otro.
        SecurityContextHolder.clearContext();
    }

    /**
     * Vacía cada tabla de la aplicación y reinicia sus secuencias, de modo que
     * cada prueba empieza con identificadores desde 1 y no hereda ninguna fila.
     *
     * <p>Las tablas se descubren leyendo el catálogo, no de una lista escrita a
     * mano: una migración que añada una tabla queda cubierta sin que nadie
     * tenga que acordarse de actualizar este archivo — que es exactamente el
     * tipo de olvido que reintroduce el problema meses después.
     */
    private void vaciarTodasLasTablas() throws SQLException {
        try (Connection conexion = dataSource.getConnection();
             Statement sentencia = conexion.createStatement()) {

            // Solo el esquema de la aplicación. Preguntar por "todos los
            // esquemas" no sirve: H2 corre en MODE=PostgreSQL y expone además
            // el catálogo de compatibilidad (pg_catalog: pg_am, pg_class...),
            // que es de solo lectura — intentar truncarlo aborta la prueba con
            // «Tabla "pg_am" no encontrada» antes de que llegue a ejecutarse.
            String esquemaDeLaAplicacion = conexion.getSchema();

            List<String> tablas = new ArrayList<>();
            try (ResultSet rs = conexion.getMetaData()
                    .getTables(null, esquemaDeLaAplicacion, "%", new String[]{"TABLE"})) {
                while (rs.next()) {
                    tablas.add(rs.getString("TABLE_NAME"));
                }
            }

            // Las tablas se referencian entre sí, así que no hay un orden de
            // borrado que funcione siempre. Se desactiva la integridad
            // referencial mientras dura el vaciado y se restablece después: si
            // quedara desactivada, una prueba podría insertar una fila huérfana
            // y pasar cuando en producción habría fallado.
            sentencia.execute("SET REFERENTIAL_INTEGRITY FALSE");
            try {
                for (String tabla : tablas) {
                    sentencia.execute("TRUNCATE TABLE \"" + tabla + "\" RESTART IDENTITY");
                }
            } finally {
                sentencia.execute("SET REFERENTIAL_INTEGRITY TRUE");
            }
        }
    }
}
