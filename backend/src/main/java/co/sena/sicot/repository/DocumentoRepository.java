package co.sena.sicot.repository;

import co.sena.sicot.dto.documento.DocumentoResponse;
import co.sena.sicot.dto.seguimiento.DocumentoResumen;
import co.sena.sicot.entity.Documento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface DocumentoRepository extends JpaRepository<Documento, Long> {

    /**
     * Listado de los documentos de un contrato, SIN traer el contenido binario.
     *
     * <p>Antes esto era un método derivado que devolvía entidades
     * {@code Documento}. JPA carga los atributos básicos de forma ansiosa, así
     * que aquella consulta traía a memoria los bytes íntegros de todos los
     * archivos del contrato solo para construir un DTO que los descarta. Con
     * 20 MB de tope por archivo, una veintena de documentos bastaba para
     * agotar el heap del contenedor y tumbar el backend entero — no solo esta
     * pantalla.
     *
     * <p>La proyección nombra una a una las columnas que el cliente necesita.
     * {@code contenido} no aparece, así que PostgreSQL nunca lo lee ni lo
     * manda por la red. Es la única forma fiable de conseguirlo: sobre un
     * atributo básico, {@code @Basic(fetch = LAZY)} lo ignora Hibernate salvo
     * que se active la mejora de bytecode.
     *
     * <p>Los {@code LEFT JOIN} son obligatorios y no cosméticos: {@code subetapa},
     * {@code formato}, {@code subidoPor} y {@code firmadoPor} son opcionales, y
     * una unión implícita (escribir {@code d.subetapa.id} a secas) genera un
     * INNER JOIN que haría desaparecer del listado justamente los documentos
     * que no tienen subetapa —o formato institucional— asignado.
     */
    @Query("""
            SELECT new co.sena.sicot.dto.documento.DocumentoResponse(
                d.id,
                c.id,
                s.id,
                fmt.id,
                fmt.codigo,
                fmt.nombre,
                d.nombre,
                d.tipo,
                d.rutaArchivo,
                d.estado,
                d.tamanioBytes,
                d.generadoPorIa,
                d.firmaId,
                d.fechaFirma,
                d.firmaHashSha256,
                f.nombre,
                u.nombre,
                d.fechaSubida,
                d.capturaFecha,
                d.capturaLatitud,
                d.capturaLongitud)
            FROM Documento d
            JOIN d.contrato c
            LEFT JOIN d.subetapa s
            LEFT JOIN d.formato fmt
            LEFT JOIN d.subidoPor u
            LEFT JOIN d.firmadoPor f
            WHERE c.id = :contratoId
            ORDER BY d.fechaSubida DESC
            """)
    List<DocumentoResponse> listarPorContrato(@Param("contratoId") Long contratoId);

    /**
     * ¿Hay ya un documento firmado de este formato en la subetapa? Lo usa la
     * generación para no crear un segundo «Acta de Inicio» después de firmada
     * la primera: el borrador sobrante quedaba como tarea pendiente en la
     * bandeja del supervisor para siempre (prueba integral del 24-09-2026).
     */
    boolean existsByContratoIdAndSubetapaIdAndNombreStartingWithAndFirmaIdIsNotNull(
            Long contratoId, Long subetapaId, String prefijoNombre);

    /** El borrador sin firmar más reciente de un formato en una subetapa. */
    java.util.Optional<Documento> findFirstByContratoIdAndSubetapaIdAndNombreStartingWithAndFirmaIdIsNullOrderByFechaSubidaDesc(
            Long contratoId, Long subetapaId, String prefijoNombre);

    /**
     * ¿Hay otro documento firmado con el mismo nombre en la subetapa? Lo usa
     * la firma para no dejar dos actas firmadas del mismo paso.
     */
    boolean existsByContratoIdAndSubetapaIdAndNombreAndFirmaIdIsNotNullAndIdNot(
            Long contratoId, Long subetapaId, String nombre, Long id);

    /**
     * Resumen de los documentos de varios contratos, sin contenido binario.
     *
     * <p>Mismo cuidado que {@link #listarPorContrato}: una proyección que no
     * nombra {@code contenido}, y {@code LEFT JOIN} a la subetapa porque un
     * documento cargado sin subetapa también es del expediente y tiene que
     * contarse.
     */
    @Query("""
            SELECT new co.sena.sicot.dto.seguimiento.DocumentoResumen(
                d.id,
                c.id,
                s.codigo,
                d.nombre,
                d.estado,
                d.generadoPorIa,
                CASE WHEN d.firmaId IS NOT NULL THEN true ELSE false END,
                d.fechaSubida,
                d.fechaFirma)
            FROM Documento d
            JOIN d.contrato c
            LEFT JOIN d.subetapa s
            WHERE c.id IN :contratoIds
            ORDER BY d.fechaSubida DESC
            """)
    List<DocumentoResumen> resumenPorContratos(@Param("contratoIds") Collection<Long> contratoIds);
}
