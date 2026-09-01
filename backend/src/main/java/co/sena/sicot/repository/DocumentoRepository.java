package co.sena.sicot.repository;

import co.sena.sicot.dto.documento.DocumentoResponse;
import co.sena.sicot.entity.Documento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
     * {@code subidoPor} y {@code firmadoPor} son opcionales, y una unión
     * implícita (escribir {@code d.subetapa.id} a secas) genera un INNER JOIN
     * que haría desaparecer del listado justamente los documentos que no
     * tienen subetapa asignada.
     */
    @Query("""
            SELECT new co.sena.sicot.dto.documento.DocumentoResponse(
                d.id,
                c.id,
                s.id,
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
                d.fechaSubida)
            FROM Documento d
            JOIN d.contrato c
            LEFT JOIN d.subetapa s
            LEFT JOIN d.subidoPor u
            LEFT JOIN d.firmadoPor f
            WHERE c.id = :contratoId
            ORDER BY d.fechaSubida DESC
            """)
    List<DocumentoResponse> listarPorContrato(@Param("contratoId") Long contratoId);
}
