package co.sena.sicot.repository;

import co.sena.sicot.entity.Contrato;
import co.sena.sicot.entity.enums.EstadoContrato;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

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

    @Override
    @EntityGraph(attributePaths = "supervisor")
    List<Contrato> findAll();

    @EntityGraph(attributePaths = "supervisor")
    List<Contrato> findBySupervisorId(Long supervisorId);

    @EntityGraph(attributePaths = "supervisor")
    List<Contrato> findByEstado(EstadoContrato estado);

    @EntityGraph(attributePaths = "supervisor")
    List<Contrato> findBySupervisorIdAndEstado(Long supervisorId, EstadoContrato estado);
}
