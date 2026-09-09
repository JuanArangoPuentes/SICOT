package co.sena.sicot.repository;

import co.sena.sicot.entity.Registro;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RegistroRepository extends JpaRepository<Registro, Long> {

    /**
     * Auditoría de un contrato, acotada.
     *
     * <p>El listado global ya tenía tope; este no, y es el que más crece: cada
     * avance de subetapa, cada cambio de estado y cada firma dejan una entrada.
     * Un contrato que recorre las 27 subetapas de GCCON-P-010 genera decenas de
     * filas sin contar correcciones.
     */
    List<Registro> findByContratoIdOrderByFechaDesc(Long contratoId, Pageable limite);

    // Pageable (no un List sin límite): la auditoría crece con cada acción del
    // sistema y sin tope terminaría cargando toda la tabla en memoria.
    List<Registro> findAllByOrderByFechaDesc(Pageable pageable);
}
