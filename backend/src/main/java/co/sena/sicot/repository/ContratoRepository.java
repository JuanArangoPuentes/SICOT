package co.sena.sicot.repository;

import co.sena.sicot.dto.seguimiento.ConteoPorId;
import co.sena.sicot.entity.Contrato;
import co.sena.sicot.entity.enums.EstadoContrato;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ContratoRepository extends JpaRepository<Contrato, Long> {

    boolean existsByNumeroContrato(String numeroContrato);

    boolean existsByNumeroContratoAndIdNot(String numeroContrato, Long id);

    // @EntityGraph trae el supervisor en el mismo SELECT (join) en vez de una
    // consulta perezosa aparte por cada contrato — sin esto, listar N
    // contratos dispara 1+N consultas solo para poder mostrar el nombre/email
    // del supervisor en ContratoMapper.
    @Override
    @EntityGraph(attributePaths = "supervisor")
    Optional<Contrato> findById(Long id);

    /**
     * Barrido COMPLETO de contratos por estado, deliberadamente SIN tope.
     *
     * <p>Lo usa {@link co.sena.sicot.automatizacion.LectorDeContratos} para
     * evaluar las reglas de calendario sobre todos los contratos activos. Aquí
     * un tope no acotaría una pantalla: haría que el motor dejara de mirar los
     * contratos que quedaran fuera, y eso no produce ningún error visible — las
     * alertas de esos contratos sencillamente no se crearían nunca.
     *
     * <p><b>No usar desde un controlador.</b> Para listar en pantalla están los
     * métodos con {@code Pageable} de abajo.
     */
    @EntityGraph(attributePaths = "supervisor")
    List<Contrato> findByEstado(EstadoContrato estado);

    // ── Listados de pantalla, acotados ─────────────────────────────────────
    //
    // Los cuatro llevan Pageable por el mismo motivo que ya lo llevan alertas y
    // registros: `contratos` crece de forma monótona y GET /api/contratos se
    // pide en cada carga del panel de GESTIÓN. Con el horizonte de 3-4 años del
    // proyecto, "todos los contratos que han existido" deja de ser una consulta
    // razonable mucho antes de que a nadie le parezca lenta.
    //
    // Van ordenados por fecha de creación descendente y no sin orden: un tope
    // sobre un conjunto sin ordenar recorta filas arbitrarias, de modo que el
    // contrato que falta cambia entre dos peticiones idénticas. Ordenando, lo
    // que se pierde es siempre lo más antiguo, que es lo que alguien esperaría.

    @EntityGraph(attributePaths = "supervisor")
    List<Contrato> findAllByOrderByFechaCreacionDesc(Pageable limite);

    @EntityGraph(attributePaths = "supervisor")
    List<Contrato> findBySupervisorIdOrderByFechaCreacionDesc(Long supervisorId, Pageable limite);

    @EntityGraph(attributePaths = "supervisor")
    List<Contrato> findByEstadoOrderByFechaCreacionDesc(EstadoContrato estado, Pageable limite);

    @EntityGraph(attributePaths = "supervisor")
    List<Contrato> findBySupervisorIdAndEstadoOrderByFechaCreacionDesc(Long supervisorId,
                                                                      EstadoContrato estado,
                                                                      Pageable limite);

    /**
     * Contratos abiertos para el seguimiento del Administrador, sin tope.
     *
     * <p>Mismo razonamiento que {@link #findByEstado}: un tope aquí haría
     * desaparecer del seguimiento a un supervisor entero sin ningún aviso. Lo
     * que acota el tamaño es que solo entran los estados abiertos; los
     * terminados, que son los que se acumulan con los años, solo se cuentan
     * ({@link #contarFinalizadosPorSupervisor}).
     */
    @EntityGraph(attributePaths = "supervisor")
    List<Contrato> findByEstadoInOrderByFechaCreacionDesc(Collection<EstadoContrato> estados);

    @Query("""
            SELECT new co.sena.sicot.dto.seguimiento.ConteoPorId(c.supervisor.id, COUNT(c))
              FROM Contrato c
             WHERE c.supervisor IS NOT NULL
               AND c.estado = co.sena.sicot.entity.enums.EstadoContrato.FINALIZADO
             GROUP BY c.supervisor.id
            """)
    List<ConteoPorId> contarFinalizadosPorSupervisor();
}
