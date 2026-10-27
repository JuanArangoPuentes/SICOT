package co.sena.sicot.repository;

import co.sena.sicot.entity.EntregaAcordada;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface EntregaAcordadaRepository extends JpaRepository<EntregaAcordada, Long> {

    /** El acuerdo de un contrato, en orden de entrega. */
    List<EntregaAcordada> findByContratoIdOrderByNumeroAsc(Long contratoId);

    /**
     * Las entregas de varios contratos de una vez, para el motor de
     * automatizaciones: una sola consulta para todos los contratos vigentes en
     * vez de una por contrato. Ver {@code LectorDeContratos}.
     */
    @Query("select e from EntregaAcordada e where e.contrato.id in :contratoIds order by e.contrato.id, e.numero")
    List<EntregaAcordada> deLosContratos(@Param("contratoIds") Collection<Long> contratoIds);

    /** Para la compuerta del paso 2.4: no se cierra sin al menos una fecha acordada. */
    long countByContratoId(Long contratoId);

    /**
     * Borra el acuerdo antes de volver a escribirlo. Guardar reemplaza la lista
     * completa, como la solicitud de pedido: ver
     * {@link ItemSolicitudPedidoRepository#borrarDelContrato}.
     */
    @Modifying
    @Query("delete from EntregaAcordada e where e.contrato.id = :contratoId")
    void borrarDelContrato(@Param("contratoId") Long contratoId);
}
