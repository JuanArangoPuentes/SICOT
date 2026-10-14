package co.sena.sicot.documentacion;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Comprueba que {@code docs/api/INVENTARIO_ENDPOINTS.md} tenga una fila por cada
 * endpoint de los controladores.
 *
 * <p><b>Qué falla sin esta prueba.</b> Ese inventario existe para lo que Swagger
 * no muestra: <b>dónde</b> se comprueba el acceso de cada ruta, que en SICOT unas
 * veces es un {@code @PreAuthorize} y otras una llamada a
 * {@code SecurityUtils.verificarAccesoAlContrato} dentro del servicio. Quien
 * revisa si una ruta deja ver contratos ajenos lo hace con esta tabla, no con el
 * código de los quince controladores. Hasta ahora la tabla se verificaba a mano
 * y el propio documento lo decía: se quedó con 44 endpoints cuando ya había 47, y
 * entre las ausencias estaba {@code GET /api/contratos/&#123;id&#125;/cronograma}, que
 * no lleva {@code @PreAuthorize} y depende de que el servicio verifique el
 * acceso. Una ruta que no aparece en la tabla es una ruta que nadie revisa.
 *
 * <p><b>Por qué cuenta por controlador y no un total.</b> Un total que no cuadra
 * dice que falta algo; el recuento por clase dice <b>qué</b> falta, que es lo que
 * necesita quien tiene que arreglar el documento. La prueba lee los archivos del
 * proyecto, así que no necesita contexto de Spring, base de datos ni Docker.
 *
 * <p><b>Lo que esta prueba no puede comprobar</b> es si la columna «Control de
 * acceso real» dice la verdad: eso sigue siendo lectura humana. Solo garantiza
 * que ninguna ruta se quede fuera de la revisión.
 */
class InventarioDeEndpointsTest {

    private static final Path CONTROLADORES = Path.of("src/main/java/co/sena/sicot/controller");

    /** El inventario vive en la raíz del repositorio, un nivel por encima del módulo. */
    private static final Path INVENTARIO = Path.of("../docs/api/INVENTARIO_ENDPOINTS.md");

    /**
     * Una anotación de ruta al principio de su línea, como se escriben las
     * anotaciones. Anclarla al margen evita contar las mismas palabras cuando
     * aparecen citadas en un Javadoc.
     */
    private static final Pattern ANOTACION_DE_RUTA =
            Pattern.compile("^\\s*@(Get|Post|Put|Patch|Delete)Mapping", Pattern.MULTILINE);

    /**
     * Una fila de la tabla: {@code | 12 | NombreController | GET | ... }. El
     * número de la primera columna es lo que distingue una fila de endpoint de
     * la cabecera o de cualquier otra tabla del documento.
     */
    private static final Pattern FILA_DEL_INVENTARIO =
            Pattern.compile("^\\|\\s*\\d+\\s*\\|\\s*([A-Za-z]+Controller)\\s*\\|", Pattern.MULTILINE);

    @Test
    void cadaEndpointTieneSuFilaEnElInventario() {
        Map<String, Integer> enElCodigo = endpointsPorControlador();
        Map<String, Integer> enElDocumento = filasPorControlador();

        assertThat(enElCodigo)
                .as("""
                        docs/api/INVENTARIO_ENDPOINTS.md tiene que llevar una fila por endpoint. \
                        Si añadió o quitó una ruta, actualice la tabla (y la cuenta de su cabecera) \
                        antes de dar la tarea por terminada.""")
                .isEqualTo(enElDocumento);
    }

    /** Cuántas anotaciones de ruta tiene cada clase de controlador. */
    private Map<String, Integer> endpointsPorControlador() {
        Map<String, Integer> conteo = new TreeMap<>();
        try (Stream<Path> archivos = Files.list(CONTROLADORES)) {
            for (Path archivo : archivos.filter(p -> p.toString().endsWith("Controller.java")).toList()) {
                String nombre = archivo.getFileName().toString().replace(".java", "");
                String fuente = Files.readString(archivo, StandardCharsets.UTF_8);
                int rutas = 0;
                Matcher m = ANOTACION_DE_RUTA.matcher(fuente);
                while (m.find()) {
                    rutas++;
                }
                if (rutas > 0) {
                    conteo.put(nombre, rutas);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudieron leer los controladores en " + CONTROLADORES, e);
        }
        assertThat(conteo)
                .as("No se encontró ningún controlador en %s: la prueba estaría pasando en vacío", CONTROLADORES)
                .isNotEmpty();
        return conteo;
    }

    /** Cuántas filas del inventario nombran a cada controlador. */
    private Map<String, Integer> filasPorControlador() {
        String documento;
        try {
            documento = Files.readString(INVENTARIO, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer el inventario en " + INVENTARIO, e);
        }
        Map<String, Integer> conteo = new TreeMap<>();
        Matcher m = FILA_DEL_INVENTARIO.matcher(documento);
        while (m.find()) {
            conteo.merge(m.group(1), 1, Integer::sum);
        }
        return conteo;
    }

    /**
     * La cabecera anuncia cuántos controladores y endpoints hay; si se queda
     * vieja, quien abra el documento desconfía de la tabla entera.
     */
    @Test
    void laCabeceraAnunciaLosNumerosReales() {
        String documento;
        try {
            documento = Files.readString(INVENTARIO, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer el inventario en " + INVENTARIO, e);
        }
        Map<String, Integer> enElCodigo = endpointsPorControlador();
        int controladores = enElCodigo.size();
        int endpoints = enElCodigo.values().stream().mapToInt(Integer::intValue).sum();

        List<String> cabecera = documento.lines().limit(5).toList();
        assertThat(cabecera)
                .as("La cabecera de %s tiene que anunciar «%d controladores, %d endpoints»",
                        INVENTARIO, controladores, endpoints)
                .anyMatch(l -> l.contains(controladores + " controladores")
                        && l.contains(endpoints + " endpoints"));
    }
}
