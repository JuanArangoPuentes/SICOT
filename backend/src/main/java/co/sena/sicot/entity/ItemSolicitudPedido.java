package co.sena.sicot.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;

/**
 * Un bien de la solicitud de pedido que el supervisor le manda al contratista
 * en el paso 2.4 del proceso real.
 *
 * <p>Reproduce una fila del formato que el supervisor usa hoy
 * («Solicitud_Maybe.xlsx», el pedido del Lote 4 que Alex Zapata adjuntó a su
 * lista de chequeo el 20-10-2026): número de ítem, descripción completa,
 * cantidad y valor unitario con IVA. El valor total de la fila no se guarda,
 * se calcula: ver {@code V21__solicitud_de_pedido_al_contratista.sql}.
 *
 * <p>Sin {@code @Version}: la solicitud se guarda completa y de una vez
 * ({@code SolicitudDePedidoService.guardar} reemplaza la lista del contrato),
 * así que no hay dos ediciones de un mismo ítem que puedan pisarse. El bloqueo
 * optimista que importa es el del contrato.
 */
@Entity
@Table(name = "items_solicitud_pedido")
public class ItemSolicitudPedido {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "contrato_id", nullable = false)
    private Contrato contrato;

    @Column(nullable = false)
    private Integer numero;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String descripcion;

    @Column(nullable = false)
    private Integer cantidad;

    @Column(name = "valor_unitario", nullable = false, precision = 18, scale = 2)
    private BigDecimal valorUnitario;

    protected ItemSolicitudPedido() {
    }

    public ItemSolicitudPedido(Contrato contrato, Integer numero, String descripcion,
                               Integer cantidad, BigDecimal valorUnitario) {
        this.contrato = contrato;
        this.numero = numero;
        this.descripcion = descripcion;
        this.cantidad = cantidad;
        this.valorUnitario = valorUnitario;
    }

    /**
     * Cantidad × valor unitario, la columna «VALOR TOTAL CON IVA» del formato.
     * Se calcula siempre, nunca se lee de la base.
     */
    public BigDecimal valorTotal() {
        return valorUnitario.multiply(BigDecimal.valueOf(cantidad));
    }

    public Long getId() {
        return id;
    }

    public Contrato getContrato() {
        return contrato;
    }

    public Integer getNumero() {
        return numero;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public Integer getCantidad() {
        return cantidad;
    }

    public BigDecimal getValorUnitario() {
        return valorUnitario;
    }
}
