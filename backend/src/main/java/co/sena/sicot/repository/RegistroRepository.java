package co.sena.sicot.repository;

import co.sena.sicot.entity.Registro;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RegistroRepository extends JpaRepository<Registro, Long> {

    List<Registro> findByContratoIdOrderByFechaDesc(Long contratoId);

    // Pageable (no un List sin límite): la auditoría crece con cada acción del
    // sistema y sin tope terminaría cargando toda la tabla en memoria.
    List<Registro> findAllByOrderByFechaDesc(Pageable pageable);
}
