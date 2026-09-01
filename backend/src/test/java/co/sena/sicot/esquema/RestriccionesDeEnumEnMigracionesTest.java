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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Comprueba que cada columna de enum guardada como texto tenga en la base la
 * misma lista de valores que su enum de Java.
 *
 * <p><b>Qué falla sin esta prueba.</b> Los enums se persisten con
 * {@code @Enumerated(EnumType.STRING)} y las migraciones los acotan con un
 * {@code CHECK ... IN (...)}. Son dos listas escritas a mano en dos archivos
 * distintos. Agregar una constante nueva —digamos un {@code TipoAlerta.PAGO}—
 * compila, pasa toda la suite (H2 genera el esquema desde las entidades, sin
 * los CHECK) y arranca sin problemas. El fallo aparece la primera vez que
 * alguien intenta guardar una alerta de ese tipo <b>contra PostgreSQL</b>, es
 * decir, en producción, como una violación de restricción a mitad de una
 * operación de negocio.
 *
 * <p><b>Por qué se lee el SQL en vez de consultar una base.</b> Esta prueba no
 * necesita PostgreSQL: lee los archivos de migración del proyecto. Así corre en
 * cualquier máquina y en cada {@code mvn test}, sin Docker ni servidor. La
 * verificación contra el esquema realmente aplicado la hace
 * {@link EsquemaPostgreSqlIT}, que sí exige una base; esta es la red que
 * atrapa el error en el momento de escribirlo.
 */
class RestriccionesDeEnumEnMigracionesTest {

    private static final Path MIGRACIONES = Path.of("src/main/resources/db/migration");

    /**
     * Un {@code CONSTRAINT ck_algo CHECK (columna IN ('A', 'B'))} dentro de un
     * CREATE TABLE o de un ALTER TABLE. El grupo 1 es el nombre de la
     * restricción y el grupo 2 la lista literal de valores.
     */
    private static final Pattern CHECK_IN = Pattern.compile(
            "CONSTRAINT\\s+(ck_\\w+)\\s+CHECK\\s*\\(\\s*\\w+\\s+IN\\s*\\(([^)]*)\\)",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern LITERAL = Pattern.compile("'([^']*)'");

    /**
     * Nombre de la restricción en la base → enum de Java que la columna guarda.
     * Toda restricción {@code ck_*_IN} de las migraciones debe estar en este
     * mapa; la última comprobación de la prueba se encarga de que no se pueda
     * agregar una nueva sin registrarla aquí.
     */
    private static final Map<String, Class<? extends Enum<?>>> ENUM_POR_RESTRICCION = Map.of(
            "ck_usuarios_rol", Rol.class,
            "ck_contratos_estado", EstadoContrato.class,
            "ck_etapas_estado", EstadoEtapa.class,
            "ck_subetapas_estado", EstadoSubetapa.class,
            "ck_documentos_tipo", TipoDocumento.class,
            "ck_documentos_estado", EstadoDocumento.class,
            "ck_alertas_tipo", TipoAlerta.class,
            "ck_alertas_prioridad", PrioridadAlerta.class,
            "ck_formatos_tipo_archivo", TipoDocumento.class,
            "ck_formatos_estado", EstadoFormato.class);

    @Test
    void cadaCheckDeEnumListaExactamenteLosValoresDelEnumDeJava() {
        Map<String, Set<String>> enLasMigraciones = leerRestriccionesDeEnum();

        assertThat(enLasMigraciones)
                .as("las migraciones deben declarar un CHECK para cada enum persistido como texto")
                .containsOnlyKeys(ENUM_POR_RESTRICCION.keySet().toArray(String[]::new));

        enLasMigraciones.forEach((restriccion, valoresEnSql) -> {
            Set<String> valoresEnJava = valoresDe(ENUM_POR_RESTRICCION.get(restriccion));
            assertThat(valoresEnSql)
                    .as("%s debe listar exactamente los valores de %s. Si acaba de agregar o "
                                    + "renombrar una constante del enum, agregue una migración que "
                                    + "reemplace esta restricción.",
                            restriccion, ENUM_POR_RESTRICCION.get(restriccion).getSimpleName())
                    .containsExactlyInAnyOrderElementsOf(valoresEnJava);
        });
    }

    /** Lee todas las migraciones y devuelve, por restricción, los valores que permite. */
    private static Map<String, Set<String>> leerRestriccionesDeEnum() {
        Map<String, Set<String>> resultado = new LinkedHashMap<>();
        for (Path migracion : archivosDeMigracion()) {
            String sql = leer(migracion);
            Matcher restriccion = CHECK_IN.matcher(sql);
            while (restriccion.find()) {
                Set<String> valores = new LinkedHashSet<>();
                Matcher literal = LITERAL.matcher(restriccion.group(2));
                while (literal.find()) {
                    valores.add(literal.group(1));
                }
                // put y no putIfAbsent: si una migración posterior reemplaza la
                // restricción, la que vale es la última, igual que en la base.
                resultado.put(restriccion.group(1).toLowerCase(), valores);
            }
        }
        return resultado;
    }

    private static List<Path> archivosDeMigracion() {
        try (Stream<Path> archivos = Files.list(MIGRACIONES)) {
            List<Path> ordenados = archivos
                    .filter(p -> p.getFileName().toString().endsWith(".sql"))
                    .sorted(RestriccionesDeEnumEnMigracionesTest::porNumeroDeVersion)
                    .toList();
            assertThat(ordenados).as("no se encontró ninguna migración en %s", MIGRACIONES).isNotEmpty();
            return ordenados;
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo listar " + MIGRACIONES, e);
        }
    }

    /**
     * Ordena por el número de versión y no alfabéticamente: {@code V10} va
     * después de {@code V9}, no antes, igual que las aplica Flyway.
     */
    private static int porNumeroDeVersion(Path a, Path b) {
        return Integer.compare(version(a), version(b));
    }

    private static int version(Path archivo) {
        Matcher m = Pattern.compile("^V(\\d+)__").matcher(archivo.getFileName().toString());
        return m.find() ? Integer.parseInt(m.group(1)) : Integer.MAX_VALUE;
    }

    private static String leer(Path archivo) {
        try {
            return Files.readString(archivo, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer la migración " + archivo, e);
        }
    }

    private static Set<String> valoresDe(Class<? extends Enum<?>> tipo) {
        Set<String> valores = new LinkedHashSet<>();
        for (Enum<?> constante : tipo.getEnumConstants()) {
            valores.add(constante.name());
        }
        return valores;
    }
}
