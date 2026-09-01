package co.sena.sicot.esquema;

import co.sena.sicot.entity.enums.EstadoContrato;
import co.sena.sicot.entity.enums.EstadoDocumento;
import co.sena.sicot.entity.enums.EstadoEtapa;
import co.sena.sicot.entity.enums.EstadoFormato;
import co.sena.sicot.entity.enums.EstadoSubetapa;
import co.sena.sicot.entity.enums.PrioridadAlerta;
import co.sena.sicot.entity.enums.Rol;
import co.sena.sicot.entity.enums.TipoAlerta;
import co.sena.sicot.entity.enums.TipoDocumento;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Construye el esquema con Flyway sobre PostgreSQL de verdad y comprueba que
 * Hibernate lo valide sin quejas.
 *
 * <p><b>El agujero que cierra.</b> El resto de la suite corre sobre H2 con
 * {@code ddl-auto=create-drop}: Hibernate genera el esquema a partir de las
 * propias entidades y Flyway ni siquiera se ejecuta. Ese montaje es rápido,
 * pero es incapaz por construcción de detectar una discrepancia entre las
 * migraciones y el mapeo — el esquema que valida es el que él mismo acaba de
 * derivar de las entidades, así que siempre coincide. Producción hace lo
 * contrario: Flyway crea el esquema y {@code ddl-auto=validate} lo compara con
 * las entidades. Hasta ahora, la primera vez que esa comparación ocurría de
 * verdad era en el arranque del servidor de producción, y si fallaba, el
 * backend no arrancaba.
 *
 * <p>Esta prueba adelanta esa comparación a la integración continua. Además
 * verifica contra el catálogo de PostgreSQL las tres cosas que
 * {@code validate} <b>no</b> mira y que ya causaron un desfase real entre las
 * bases del equipo: los CHECK de los enums, las restricciones de unicidad y el
 * índice parcial de firmas.
 *
 * <p><b>Cómo ejecutarla.</b> Se salta sola si no hay base. Para correrla, con
 * la base del proyecto arriba:
 *
 * <pre>
 *   SICOT_IT_DB_URL=jdbc:postgresql://localhost:5432/sicot \
 *   SICOT_IT_DB_USERNAME=sicot \
 *   SICOT_IT_DB_PASSWORD=... \
 *   ./mvnw test -Dtest=EsquemaPostgreSqlIntegrationTest
 * </pre>
 *
 * <p>No toca los datos del equipo: trabaja sobre un esquema propio
 * ({@value #ESQUEMA}) que borra y recrea vacío en cada corrida. El esquema
 * {@code public} de esa base queda intacto.
 */
@SpringBootTest
@ActiveProfiles("esquema-it")
@EnabledIfEnvironmentVariable(named = "SICOT_IT_DB_URL", matches = ".+",
        disabledReason = "Requiere PostgreSQL: defina SICOT_IT_DB_URL para ejecutarla.")
class EsquemaPostgreSqlIntegrationTest {

    /**
     * Esquema desechable. Deliberadamente NO es {@code public}: la prueba borra
     * su esquema entero antes de empezar, y apuntarla a {@code public} borraría
     * la base de desarrollo de quien la ejecute en su máquina.
     */
    static final String ESQUEMA = "sicot_verificacion_esquema";

    private static final Pattern VALOR_DEL_CHECK = Pattern.compile("'([^']*)'::");

    @Autowired
    private JdbcTemplate jdbc;

    @DynamicPropertySource
    static void apuntarAPostgreSql(DynamicPropertyRegistry registry) {
        String url = System.getenv("SICOT_IT_DB_URL");
        String usuario = System.getenv().getOrDefault("SICOT_IT_DB_USERNAME", "sicot");
        String clave = System.getenv().getOrDefault("SICOT_IT_DB_PASSWORD", "");

        recrearEsquemaVacio(url, usuario, clave);

        registry.add("spring.datasource.url", () -> url);
        registry.add("spring.datasource.username", () -> usuario);
        registry.add("spring.datasource.password", () -> clave);
        registry.add("spring.flyway.schemas", () -> ESQUEMA);
        registry.add("spring.flyway.default-schema", () -> ESQUEMA);
        registry.add("spring.jpa.properties.hibernate.default_schema", () -> ESQUEMA);
    }

    /**
     * Deja {@value #ESQUEMA} recién creado y vacío para que Flyway lo construya
     * desde la primera migración. Se ejecuta antes de que exista el contexto de
     * Spring, así que usa JDBC directo.
     */
    private static void recrearEsquemaVacio(String url, String usuario, String clave) {
        try (Connection conexion = DriverManager.getConnection(url, usuario, clave);
             Statement sentencia = conexion.createStatement()) {
            sentencia.execute("DROP SCHEMA IF EXISTS " + ESQUEMA + " CASCADE");
            sentencia.execute("CREATE SCHEMA " + ESQUEMA);
        } catch (SQLException e) {
            throw new IllegalStateException(
                    "No se pudo preparar el esquema de verificación en " + url
                            + ". Verifique SICOT_IT_DB_URL / SICOT_IT_DB_USERNAME / SICOT_IT_DB_PASSWORD.", e);
        }
    }

    /**
     * Que el contexto haya arrancado ya demuestra lo esencial: con
     * {@code ddl-auto=validate}, Hibernate comparó cada entidad contra el
     * esquema que acababa de construir Flyway y no encontró ninguna columna
     * ausente, sobrante o de tipo incompatible. Si hubiera desfase, esta clase
     * ni siquiera llegaría a ejecutarse.
     *
     * <p>Lo que sí queda por comprobar aquí es que todas las migraciones se
     * aplicaran: Flyway registra las fallidas sin abortar en algunos modos, y
     * una migración a medias deja un esquema que valida por casualidad.
     */
    @Test
    void flywayAplicaTodasLasMigracionesYHibernateValidaElEsquemaResultante() {
        List<String> fallidas = jdbc.queryForList(
                "SELECT version || ' — ' || description FROM " + ESQUEMA
                        + ".flyway_schema_history WHERE success = FALSE",
                String.class);
        assertThat(fallidas).as("migraciones que Flyway marcó como fallidas").isEmpty();

        Integer aplicadas = jdbc.queryForObject(
                "SELECT count(*) FROM " + ESQUEMA + ".flyway_schema_history WHERE type = 'SQL'",
                Integer.class);
        assertThat(aplicadas)
                .as("no se aplicó ninguna migración: revise spring.flyway.locations")
                .isNotNull()
                .isGreaterThan(0);
    }

    /**
     * {@code ddl-auto=validate} compara tablas, columnas y tipos, pero ignora
     * por completo los CHECK. Un enum al que se le agrega una constante sin
     * ampliar su restricción pasa validación y falla al primer INSERT real.
     */
    @Test
    void losCheckDeEnumEnLaBaseListanExactamenteLosValoresDeCadaEnum() {
        assertThat(valoresPermitidosPor("ck_usuarios_rol")).containsExactlyInAnyOrderElementsOf(nombresDe(Rol.values()));
        assertThat(valoresPermitidosPor("ck_contratos_estado")).containsExactlyInAnyOrderElementsOf(nombresDe(EstadoContrato.values()));
        assertThat(valoresPermitidosPor("ck_etapas_estado")).containsExactlyInAnyOrderElementsOf(nombresDe(EstadoEtapa.values()));
        assertThat(valoresPermitidosPor("ck_subetapas_estado")).containsExactlyInAnyOrderElementsOf(nombresDe(EstadoSubetapa.values()));
        assertThat(valoresPermitidosPor("ck_documentos_tipo")).containsExactlyInAnyOrderElementsOf(nombresDe(TipoDocumento.values()));
        assertThat(valoresPermitidosPor("ck_documentos_estado")).containsExactlyInAnyOrderElementsOf(nombresDe(EstadoDocumento.values()));
        assertThat(valoresPermitidosPor("ck_alertas_tipo")).containsExactlyInAnyOrderElementsOf(nombresDe(TipoAlerta.values()));
        assertThat(valoresPermitidosPor("ck_alertas_prioridad")).containsExactlyInAnyOrderElementsOf(nombresDe(PrioridadAlerta.values()));
        assertThat(valoresPermitidosPor("ck_formatos_tipo_archivo")).containsExactlyInAnyOrderElementsOf(nombresDe(TipoDocumento.values()));
        assertThat(valoresPermitidosPor("ck_formatos_estado")).containsExactlyInAnyOrderElementsOf(nombresDe(EstadoFormato.values()));
    }

    /**
     * Las reglas de integridad que Hibernate no valida y que son justamente las
     * que se habían perdido en las bases antiguas del equipo (ver
     * {@code V10__reconcilia_esquema_con_la_linea_base.sql}). Si una migración
     * futura las elimina sin querer, esto lo detecta.
     */
    @Test
    void elEsquemaConservaLasRestriccionesDeIntegridadQueElCodigoDaPorSentadas() {
        assertThat(nombresDeRestricciones())
                .contains("ck_contratos_valor", "ck_contratos_fechas", "ck_etapas_numero",
                        "ck_documentos_tamanio", "ck_formatos_tamanio",
                        "uq_firmas_electronicas_firma_id",
                        "uq_etapas_contrato_numero", "uq_subetapas_etapa_codigo");

        // Índice parcial: no es una restricción, así que vive en pg_indexes.
        // Es el que garantiza "una sola firma activa por usuario", la regla en
        // la que se apoya DocumentoService.firmar.
        assertThat(nombresDeIndices()).contains("uq_firma_activa_por_usuario");
    }

    /** Las nueve tablas del modelo, más el historial de Flyway. */
    @Test
    void elEsquemaTieneLasTablasDelModeloDeDatos() {
        List<String> tablas = jdbc.queryForList(
                "SELECT tablename FROM pg_tables WHERE schemaname = ? ORDER BY tablename",
                String.class, ESQUEMA);

        assertThat(tablas).containsExactlyInAnyOrder(
                "usuarios", "contratos", "etapas", "subetapas", "documentos",
                "alertas", "registros", "formatos_documentales", "firmas_electronicas",
                "flyway_schema_history");
    }

    /**
     * La migración de reconciliación tiene que hacer dos cosas a la vez:
     * restaurar lo que falta en una base desfasada y no estorbar en una base
     * que ya está al día. Las corridas normales de esta clase solo ejercitan el
     * segundo caso, porque V1 crea los objetos y V10 los encuentra ya puestos.
     *
     * <p>Esta prueba ejercita el primero: borra a mano los siete objetos que se
     * habían perdido en la base antigua del equipo, vuelve a ejecutar V10 y
     * comprueba que reaparezcan. Después la ejecuta una tercera vez para
     * confirmar que repetirla no falla — de eso depende que sea seguro
     * reaplicarla sobre bases en cualquier estado.
     *
     * <p>Va al final del archivo por orden de lectura, pero no depende del
     * orden de ejecución: deja el esquema como lo encontró.
     */
    @Test
    void laReconciliacionRestauraLosObjetosPerdidosYSePuedeRepetir() {
        List<String> restricciones = List.of("ck_contratos_valor", "ck_contratos_fechas",
                "ck_etapas_numero", "ck_documentos_tamanio", "ck_formatos_tamanio",
                "uq_firmas_electronicas_firma_id");

        enElEsquemaDeVerificacion("""
                ALTER TABLE contratos             DROP CONSTRAINT ck_contratos_valor;
                ALTER TABLE contratos             DROP CONSTRAINT ck_contratos_fechas;
                ALTER TABLE etapas                DROP CONSTRAINT ck_etapas_numero;
                ALTER TABLE documentos            DROP CONSTRAINT ck_documentos_tamanio;
                ALTER TABLE formatos_documentales DROP CONSTRAINT ck_formatos_tamanio;
                ALTER TABLE firmas_electronicas   DROP CONSTRAINT uq_firmas_electronicas_firma_id;
                DROP INDEX uq_firma_activa_por_usuario;
                """);
        assertThat(nombresDeRestricciones()).doesNotContainAnyElementsOf(restricciones);
        assertThat(nombresDeIndices()).doesNotContain("uq_firma_activa_por_usuario");

        enElEsquemaDeVerificacion(migracionDeReconciliacion());

        assertThat(nombresDeRestricciones())
                .as("V10 debe restaurar las restricciones ausentes")
                .containsAll(restricciones);
        assertThat(nombresDeIndices())
                .as("V10 debe restaurar el índice parcial de firmas")
                .contains("uq_firma_activa_por_usuario");

        // Repetirla sobre un esquema ya completo no debe hacer nada ni fallar.
        enElEsquemaDeVerificacion(migracionDeReconciliacion());
        assertThat(nombresDeRestricciones()).containsAll(restricciones);
    }

    private static String migracionDeReconciliacion() {
        try (var entrada = new ClassPathResource(
                "db/migration/V10__reconcilia_esquema_con_la_linea_base.sql").getInputStream()) {
            return new String(entrada.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer la migración de reconciliación.", e);
        }
    }

    /**
     * Ejecuta SQL con {@code search_path} apuntando al esquema desechable, que
     * es lo que hace Flyway al migrar. Sin eso, el {@code current_schema()} de
     * la migración devolvería {@code public} y la prueba estaría comprobando
     * otra cosa. El {@code search_path} se restaura antes de devolver la
     * conexión al pool para no contaminar el resto de las consultas.
     */
    private void enElEsquemaDeVerificacion(String sql) {
        jdbc.execute((ConnectionCallback<Void>) conexion -> {
            try (Statement sentencia = conexion.createStatement()) {
                sentencia.execute("SET search_path TO " + ESQUEMA);
                try {
                    sentencia.execute(sql);
                } finally {
                    sentencia.execute("SET search_path TO public");
                }
            }
            return null;
        });
    }

    /**
     * Extrae la lista de valores de un {@code CHECK (col IN (...))} tal como lo
     * guarda PostgreSQL, que lo reescribe a la forma
     * {@code (col)::text = ANY ((ARRAY['A'::character varying, …])::text[])}.
     */
    private Set<String> valoresPermitidosPor(String restriccion) {
        String definicion = jdbc.queryForObject("""
                SELECT pg_get_constraintdef(c.oid)
                  FROM pg_constraint c
                  JOIN pg_namespace n ON n.oid = c.connamespace
                 WHERE n.nspname = ? AND c.conname = ?
                """, String.class, ESQUEMA, restriccion);

        assertThat(definicion).as("no existe la restricción %s en el esquema", restriccion).isNotNull();

        Set<String> valores = new LinkedHashSet<>();
        Matcher literal = VALOR_DEL_CHECK.matcher(definicion);
        while (literal.find()) {
            valores.add(literal.group(1));
        }
        assertThat(valores).as("no se pudo leer ningún valor de %s: %s", restriccion, definicion).isNotEmpty();
        return valores;
    }

    private List<String> nombresDeRestricciones() {
        return jdbc.queryForList("""
                SELECT c.conname
                  FROM pg_constraint c
                  JOIN pg_namespace n ON n.oid = c.connamespace
                 WHERE n.nspname = ?
                """, String.class, ESQUEMA);
    }

    private List<String> nombresDeIndices() {
        return jdbc.queryForList(
                "SELECT indexname FROM pg_indexes WHERE schemaname = ?", String.class, ESQUEMA);
    }

    private static Set<String> nombresDe(Enum<?>[] constantes) {
        Set<String> nombres = new LinkedHashSet<>();
        for (Enum<?> constante : constantes) {
            nombres.add(constante.name());
        }
        return nombres;
    }
}
