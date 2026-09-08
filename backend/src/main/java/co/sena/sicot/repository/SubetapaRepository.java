package co.sena.sicot.repository;

import co.sena.sicot.automatizacion.ConteoDeSubetapas;
import co.sena.sicot.entity.Subetapa;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SubetapaRepository extends JpaRepository<Subetapa, Long> {

    List<Subetapa> findByEtapaIdOrderByCodigoAsc(Long etapaId);

    List<Subetapa> findByEtapaContratoIdOrderByCodigoAsc(Long contratoId);

    /**
     * Busca una subetapa exigiendo que pertenezca al contrato indicado.
     *
     * Es la forma correcta de resolver una subetapa recibida desde una petición:
     * el vínculo con el contrato queda dentro de la consulta, así que no se
     * puede olvidar comprobarlo. Antes cada servicio lo verificaba por su
     * cuenta después de un {@code findById}, y las dos copias divergieron: la
     * ruta de generación de documentos con IA se quedó sin la comprobación, y
     * permitía adjuntar un documento de un contrato a la subetapa de otro.
     */
    Optional<Subetapa> findByIdAndEtapaContratoId(Long id, Long contratoId);

    /**
     * Totales y completadas por contrato, en una sola consulta agrupada.
     *
     * <p>Lo usa la evaluación diaria de reglas de calendario. Recorrer
     * {@code contrato → etapas → subetapas} desde Java daría el mismo número
     * disparando una consulta por contrato y otra por etapa: con las 6 etapas
     * de GCCON-P-010, unos cincuenta contratos activos ya son más de trescientas
     * consultas para un dato que la base resuelve en una.
     *
     * <p>El {@code SUM(CASE …)} en vez de un segundo {@code COUNT} con filtro
     * mantiene ambos números en la misma pasada, y garantiza que
     * {@code completadas} nunca pueda quedar desalineada de {@code totales} por
     * haberse leído en un instante distinto.
     */
    @Query("""
            SELECT new co.sena.sicot.automatizacion.ConteoDeSubetapas(
                       s.etapa.contrato.id,
                       COUNT(s),
                       SUM(CASE WHEN s.estado = co.sena.sicot.entity.enums.EstadoSubetapa.COMPLETADA
                                THEN 1L ELSE 0L END))
              FROM Subetapa s
             WHERE s.etapa.contrato.id IN :contratoIds
             GROUP BY s.etapa.contrato.id
            """)
    List<ConteoDeSubetapas> contarPorContrato(@Param("contratoIds") Collection<Long> contratoIds);
}
