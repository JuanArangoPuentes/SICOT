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
     * Lo que le pasó a un contrato después de una entrada dada.
     *
     * <p>Es la consulta que sostiene el sondeo de novedades: la hace un equipo
     * cada pocos segundos mientras tiene el contrato abierto, para enterarse de
     * lo que hizo otro. Lo que cuesta, dicho con exactitud: entra por el
     * índice {@code idx_registros_contrato_fecha (contrato_id, fecha DESC)}
     * de V11 y recorre las entradas de ESE contrato —unos cientos en uno con
     * el proceso completo—, filtrando por {@code id}. No es una búsqueda por
     * clave primaria, como decía antes este comentario, pero tampoco toca las
     * de ningún otro contrato. Si un contrato llegara a decenas de miles de
     * entradas, un índice {@code (contrato_id, id)} la volvería una sola
     * búsqueda.
     *
     * <p>Se filtra por identificador y no por {@code fecha} a propósito: ver
     * {@link co.sena.sicot.dto.registro.NovedadesResponse}. Lleva
     * {@code Pageable} por lo mismo que las otras dos: un equipo que estuvo
     * horas sin preguntar no puede hacer que la respuesta traiga la auditoría
     * entera del contrato.
     */
    List<Registro> findByContratoIdAndIdGreaterThanOrderByIdDesc(Long contratoId, Long desdeId, Pageable limite);

    /**
     * El identificador de la última entrada de un contrato, o {@code null} si
     * no tiene ninguna.
     *
     * <p>Es con lo que arranca el sondeo: la primera pregunta no trae
     * novedades —la pantalla acaba de cargar el estado entero— sino la marca
     * desde la que empezar a mirar.
     */
    @Query("SELECT MAX(r.id) FROM Registro r WHERE r.contrato.id = :contratoId")
    Long ultimoIdDelContrato(@Param("contratoId") Long contratoId);

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
