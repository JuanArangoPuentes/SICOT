package co.sena.sicot.repository;

import co.sena.sicot.entity.Registro;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RegistroRepository extends JpaRepository<Registro, Long> {

    List<Registro> findByContratoIdOrderByFechaDesc(Long contratoId);

    List<Registro> findAllByOrderByFechaDesc();
}
