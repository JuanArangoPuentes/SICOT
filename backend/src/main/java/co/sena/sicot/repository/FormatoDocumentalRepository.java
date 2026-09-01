package co.sena.sicot.repository;

import co.sena.sicot.dto.formato.FormatoDocumentalResponse;
import co.sena.sicot.entity.FormatoDocumental;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface FormatoDocumentalRepository extends JpaRepository<FormatoDocumental, Long> {

    Optional<FormatoDocumental> findByCodigoIgnoreCase(String codigo);

    /**
     * Catálogo de formatos SIN el contenido binario de los archivos.
     *
     * <p>Mismo problema y misma solución que
     * {@code DocumentoRepository.listarPorContrato}: la columna
     * {@code contenido} de esta tabla también es {@code BYTEA}, y devolver
     * entidades traía a memoria cada plantilla institucional completa solo para
     * pintar una lista de códigos y nombres. Aquí el riesgo es incluso más
     * directo, porque el catálogo se lista entero de una vez (no está acotado
     * por contrato).
     */
    @Query("""
            SELECT new co.sena.sicot.dto.formato.FormatoDocumentalResponse(
                f.id,
                f.codigo,
                f.nombre,
                f.version,
                f.tipoArchivo,
                f.nombreArchivo,
                f.tamanioBytes,
                f.estado,
                u.nombre,
                f.fechaActualizacion)
            FROM FormatoDocumental f
            LEFT JOIN f.subidoPor u
            ORDER BY f.codigo ASC
            """)
    List<FormatoDocumentalResponse> listarCatalogo();
}
