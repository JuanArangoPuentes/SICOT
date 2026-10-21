package co.sena.sicot.repository;

import co.sena.sicot.entity.ItemSolicitudPedido;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ItemSolicitudPedidoRepository extends JpaRepository<ItemSolicitudPedido, Long> {

    /**
     * La solicitud de pedido de un contrato, en el orden en que la verá el
     * contratista. Siempre se lee entera: para mostrar la tabla del paso 2.4 y
     * para armar el XLSX que se adjunta al correo.
     */
    List<ItemSolicitudPedido> findByContratoIdOrderByNumeroAsc(Long contratoId);

    /**
     * Borra la solicitud del contrato antes de volver a escribirla.
     *
     * <p>Guardar reemplaza la lista completa, porque es lo que hace el
     * supervisor: pega otra vez la tabla de la oferta económica. Borrar e
     * insertar deja una sola verdad; conciliar fila por fila obligaría a
     * decidir qué hacer con un ítem que ya no está y con uno cuyo número
     * cambió, y el resultado sería un pedido mezclado de dos versiones.
     *
     * <p>Es una sentencia propia en vez de {@code deleteAll(entidades)} para no
     * traer las descripciones completas a memoria —en el formato real pasan de
     * 900 caracteres cada una— solo para borrarlas.
     */
    @Modifying
    @Query("delete from ItemSolicitudPedido i where i.contrato.id = :contratoId")
    void borrarDelContrato(@Param("contratoId") Long contratoId);
}
