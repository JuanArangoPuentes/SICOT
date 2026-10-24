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

import java.time.LocalDate;

/**
 * Una fecha de entrega que el supervisor acordó con el contratista en el paso
 * 2.4 del proceso real.
 *
 * <p>Sale del paso 5 de la lista de chequeo de Alex Zapata: «se establecen
 * fechas de entrega o la prestación del servicio, las fechas se definen de
 * común acuerdo con el contratista respetando los tiempos de inicio y fin del
 * contrato». Ver {@code V22__acuerdo_de_entregas_y_contactos.sql}.
 *
 * <p>Sin {@code @Version}, por la misma razón que {@link ItemSolicitudPedido}:
 * el acuerdo se guarda entero y de una vez, así que no hay dos ediciones de una
 * misma entrega que puedan pisarse.
 */
@Entity
@Table(name = "entregas_acordadas")
public class EntregaAcordada {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "contrato_id", nullable = false)
    private Contrato contrato;

    @Column(nullable = false)
    private Integer numero;

    @Column(nullable = false)
    private LocalDate fecha;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String descripcion;

    protected EntregaAcordada() {
    }

    public EntregaAcordada(Contrato contrato, Integer numero, LocalDate fecha, String descripcion) {
        this.contrato = contrato;
        this.numero = numero;
        this.fecha = fecha;
        this.descripcion = descripcion;
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

    public LocalDate getFecha() {
        return fecha;
    }

    public String getDescripcion() {
        return descripcion;
    }
}
