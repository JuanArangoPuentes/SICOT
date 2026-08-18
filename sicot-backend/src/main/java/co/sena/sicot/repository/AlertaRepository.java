package co.sena.sicot.repository;

import co.sena.sicot.entity.Alerta;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AlertaRepository extends JpaRepository<Alerta, Long> {

    List<Alerta> findByContratoIdOrderByFechaCreacionDesc(Long contratoId);

    List<Alerta> findByContratoIsNullOrderByFechaCreacionDesc();

    List<Alerta> findAllByOrderByFechaCreacionDesc();
}
