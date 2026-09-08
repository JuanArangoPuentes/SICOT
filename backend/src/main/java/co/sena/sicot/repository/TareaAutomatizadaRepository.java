package co.sena.sicot.repository;

import co.sena.sicot.entity.TareaAutomatizada;
import co.sena.sicot.entity.enums.EstadoTareaAutomatizada;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface TareaAutomatizadaRepository extends JpaRepository<TareaAutomatizada, Long> {

    boolean existsByClaveIdempotencia(String claveIdempotencia);

    /**
     * Candidatas a ejecutarse: pendientes cuyo momento ya llegó, más antiguas
     * primero.
     *
     * <p>Devuelve solo identificadores a propósito. Cargar las entidades aquí
     * sería traer a memoria un lote que quizá otro trabajador ya reclamó; con
     * identificadores, cada trabajador intenta la reserva y solo carga lo que
     * consiguió.
     */
    @Query("""
            SELECT t.id FROM TareaAutomatizada t
             WHERE t.estado = co.sena.sicot.entity.enums.EstadoTareaAutomatizada.PENDIENTE
               AND t.ejecutarEn <= :ahora
             ORDER BY t.ejecutarEn ASC
            """)
    List<Long> idsPendientes(@Param("ahora") Instant ahora, Pageable limite);

    /**
     * Reserva la tarea para este trabajador.
     *
     * <p>Es el control de concurrencia del módulo entero. El UPDATE lleva el
     * estado esperado en el WHERE, de modo que la comprobación y la reserva
     * ocurren en la misma sentencia atómica: si dos trabajadores intentan la
     * misma tarea, la base deja pasar exactamente a uno y el otro recibe cero
     * filas afectadas. Con un {@code SELECT} y luego un {@code UPDATE} habría
     * una ventana entre ambos, y esa ventana es un correo enviado dos veces.
     *
     * <p>Incrementa {@code intentos} en la propia reserva y no al terminar: si
     * el proceso muere a mitad de la ejecución, el intento ya está contado y la
     * tarea acabará rindiéndose en vez de reintentarse para siempre.
     *
     * @return 1 si la reservó este trabajador, 0 si se le adelantaron.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE TareaAutomatizada t
               SET t.estado = co.sena.sicot.entity.enums.EstadoTareaAutomatizada.EN_PROCESO,
                   t.intentos = t.intentos + 1
             WHERE t.id = :id
               AND t.estado = co.sena.sicot.entity.enums.EstadoTareaAutomatizada.PENDIENTE
            """)
    int reclamar(@Param("id") Long id);

    /**
     * Devuelve a PENDIENTE las tareas que quedaron reclamadas por un proceso que
     * ya no existe.
     *
     * <p>Sin esto, matar el backend mientras ejecuta una tarea la deja en
     * EN_PROCESO para siempre: nadie la reclama porque ya no está PENDIENTE, y
     * nadie la termina porque el trabajador que la tenía murió. Es la fuga
     * clásica de toda cola basada en estado, y no se manifiesta hasta el primer
     * reinicio brusco en producción.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE TareaAutomatizada t
               SET t.estado = co.sena.sicot.entity.enums.EstadoTareaAutomatizada.PENDIENTE
             WHERE t.estado = co.sena.sicot.entity.enums.EstadoTareaAutomatizada.EN_PROCESO
               AND t.fechaActualizacion < :limite
            """)
    int liberarTareasAbandonadas(@Param("limite") Instant limite);

    List<TareaAutomatizada> findByEstadoOrderByFechaCreacionDesc(EstadoTareaAutomatizada estado, Pageable limite);

    List<TareaAutomatizada> findAllByOrderByFechaCreacionDesc(Pageable limite);

    long countByEstado(EstadoTareaAutomatizada estado);
}
