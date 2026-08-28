package co.sena.sicot.service;

import co.sena.sicot.dto.chequeo.ListaChequeoDetalle;
import co.sena.sicot.dto.chequeo.ListaChequeoResumen;
import co.sena.sicot.dto.chequeo.TipoListaChequeo;
import co.sena.sicot.exception.ResourceNotFoundException;
import co.sena.sicot.mapper.ListaChequeoMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Catálogo de solo lectura de las listas de chequeo documentales oficiales (GCCON-F-026, -F-049,
 * -F-051, -F-052, -F-053, -F-055, -F-056 y GRF-F-088).
 *
 * <p>Los datos viven en {@code src/main/resources/listas-chequeo/*.json}, transcritos de los
 * .xlsx publicados por SENA con {@code backend/tools/extraer_listas_chequeo.py}. No están en la
 * base de datos porque no son datos transaccionales: son el texto de un formato institucional,
 * cambian solo cuando SENA publica una versión nueva del formato, y esa versión nueva se integra
 * regenerando el JSON — igual que {@link GcconP010Plantilla} con las etapas del procedimiento.
 *
 * <p>El catálogo se carga una vez al arrancar y falla el arranque si un archivo está corrupto,
 * le falta un campo o repite un código: es preferible no arrancar a servir un catálogo
 * incompleto de documentos exigidos por norma.
 */
@Service
public class ListaChequeoService {

    private static final String PATRON_RECURSOS = "classpath*:listas-chequeo/*.json";

    private final Map<String, ListaChequeoDetalle> catalogo;

    public ListaChequeoService() {
        this.catalogo = cargarCatalogo();
    }

    /** Índice del catálogo, ordenado por código. {@code tipo} es opcional y filtra el resultado. */
    public List<ListaChequeoResumen> listar(TipoListaChequeo tipo) {
        return catalogo.values().stream()
                .filter(lista -> tipo == null || lista.tipo() == tipo)
                .map(ListaChequeoMapper::toResumen)
                .toList();
    }

    /** Una lista completa por su código (ej. {@code GCCON-F-053}), sin distinguir mayúsculas. */
    public ListaChequeoDetalle obtener(String codigo) {
        String normalizado = codigo == null ? "" : codigo.trim().toUpperCase(Locale.ROOT);
        ListaChequeoDetalle lista = catalogo.get(normalizado);
        if (lista == null) {
            throw new ResourceNotFoundException(
                    "La lista de chequeo " + normalizado + " no existe en el catálogo de SICOT.");
        }
        return lista;
    }

    /**
     * Lee y valida el catálogo completo. Es estático y sin dependencias de Spring para que las
     * pruebas puedan verificar los JSON reales sin levantar el contexto de la aplicación.
     */
    static Map<String, ListaChequeoDetalle> cargarCatalogo() {
        ObjectMapper mapper = JsonMapper.builder()
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();

        Resource[] recursos;
        try {
            recursos = new PathMatchingResourcePatternResolver().getResources(PATRON_RECURSOS);
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo leer el catálogo de listas de chequeo.", e);
        }
        if (recursos.length == 0) {
            throw new IllegalStateException(
                    "El catálogo de listas de chequeo está vacío: no hay archivos en listas-chequeo/.");
        }

        Map<String, ListaChequeoDetalle> porCodigo = new LinkedHashMap<>();
        for (Resource recurso : Arrays.stream(recursos)
                .sorted(Comparator.comparing(r -> String.valueOf(r.getFilename())))
                .toList()) {
            ListaChequeoDetalle lista;
            try (InputStream entrada = recurso.getInputStream()) {
                lista = mapper.readValue(entrada, ListaChequeoDetalle.class);
            } catch (IOException e) {
                throw new IllegalStateException(
                        "No se pudo leer la lista de chequeo " + recurso.getFilename() + ".", e);
            }
            if (lista.codigo() == null || lista.codigo().isBlank()) {
                throw new IllegalStateException(
                        "La lista de chequeo " + recurso.getFilename() + " no tiene código.");
            }
            if (porCodigo.put(lista.codigo(), lista) != null) {
                throw new IllegalStateException(
                        "El catálogo tiene dos listas de chequeo con el código " + lista.codigo() + ".");
            }
        }
        return Collections.unmodifiableMap(porCodigo);
    }
}
