package co.sena.sicot.repository;

import co.sena.sicot.automatizacion.EstadoDeEtapas;
import co.sena.sicot.entity.Etapa;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface EtapaRepository extends JpaRepository<Etapa, Long> {

    // Trae las subetapas en el mismo SELECT: EtapaService.listarPorContrato
    // siempre las recorre para armar el DTO (y se llama en cada mensaje del
    // chat del Copiloto), así que sin esto son 1+6 consultas cada vez.
    @EntityGraph(attributePaths = "subEtapas")
    List<Etapa> findByContratoIdOrderByNumeroAsc(Long contratoId);

    /**
     * Etapa en curso y total de etapas, por contrato, en una sola consulta.
     *
     * <p>La usa el cálculo de cronograma, que corre para todos los contratos
     * activos en la pasada diaria y en cada carga del panel del supervisor.
     * Resolverlo navegando {@code contrato → etapas} sería una consulta por
     * contrato para leer dos números.
     *
     * <p>El {@code MIN(CASE …)} devuelve el número de la primera etapa no
     * cerrada, o {@code null} si todas lo están — que es exactamente la
     * distinción que necesita {@link co.sena.sicot.service.Cronograma}: un
     * contrato con el flujo terminado no tiene etapa que estimar.
     */
    @Query("""
            SELECT new co.sena.sicot.automatizacion.EstadoDeEtapas(
                       e.contrato.id,
                       MIN(CASE WHEN e.estado <> co.sena.sicot.entity.enums.EstadoEtapa.COMPLETADA
                                THEN e.numero ELSE NULL END),
                       COUNT(e))
              FROM Etapa e
             WHERE e.contrato.id IN :contratoIds
             GROUP BY e.contrato.id
            """)
    List<EstadoDeEtapas> estadoPorContrato(@Param("contratoIds") Collection<Long> contratoIds);
}
