package co.sena.sicot.repository;

import co.sena.sicot.dto.seguimiento.UltimaActividad;
import co.sena.sicot.entity.Registro;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
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

    /**
     * El registro más reciente de cada contrato, en una sola consulta.
     *
     * <p>Si dos registros comparten el mismo instante máximo salen los dos; el
     * servicio se queda con uno. Es preferible a un {@code LIMIT} por contrato,
     * que JPQL no expresa sin una consulta por contrato.
     */
    @Query("""
            SELECT new co.sena.sicot.dto.seguimiento.UltimaActividad(r.contrato.id, r.fecha, r.accion, r.descripcion)
              FROM Registro r
             WHERE r.contrato.id IN :contratoIds
               AND r.fecha = (SELECT MAX(r2.fecha) FROM Registro r2 WHERE r2.contrato.id = r.contrato.id)
            """)
    List<UltimaActividad> ultimaPorContrato(@Param("contratoIds") Collection<Long> contratoIds);
}
