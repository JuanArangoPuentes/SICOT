package co.sena.sicot.repository;

import co.sena.sicot.entity.Alerta;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AlertaRepository extends JpaRepository<Alerta, Long> {

    List<Alerta> findByContratoIdOrderByFechaCreacionDesc(Long contratoId);

    List<Alerta> findByContratoIsNullOrderByFechaCreacionDesc();

    // Pageable (no un List sin límite): las alertas se acumulan con el uso
    // normal del sistema y sin tope terminarían cargando toda la tabla.
    List<Alerta> findAllByOrderByFechaCreacionDesc(Pageable pageable);
}
