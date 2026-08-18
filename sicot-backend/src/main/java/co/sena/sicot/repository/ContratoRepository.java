package co.sena.sicot.repository;

import co.sena.sicot.entity.Contrato;
import co.sena.sicot.entity.enums.EstadoContrato;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ContratoRepository extends JpaRepository<Contrato, Long> {

    boolean existsByNumeroContrato(String numeroContrato);

    boolean existsByNumeroContratoAndIdNot(String numeroContrato, Long id);

    List<Contrato> findBySupervisorId(Long supervisorId);

    List<Contrato> findByEstado(EstadoContrato estado);

    List<Contrato> findBySupervisorIdAndEstado(Long supervisorId, EstadoContrato estado);
}
