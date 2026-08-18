package co.sena.sicot.entity;

import co.sena.sicot.entity.enums.EstadoContrato;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "contratos")
public class Contrato {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "numero_contrato", nullable = false, unique = true, length = 50)
    private String numeroContrato;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String objeto;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal valor;

    @Column(name = "fecha_inicio")
    private LocalDate fechaInicio;

    @Column(name = "fecha_fin")
    private LocalDate fechaFin;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoContrato estado = EstadoContrato.BORRADOR;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supervisor_id")
    private Usuario supervisor;

    @Column(name = "tipo_contrato", length = 100)
    private String tipoContrato;

    @Column(length = 255)
    private String contratista;

    @Column(name = "contratista_nit", length = 30)
    private String contratistaNit;

    @Column(name = "representante_legal", length = 255)
    private String representanteLegal;

    @Column(name = "lugar_ejecucion", length = 255)
    private String lugarEjecucion;

    @Column(name = "numero_registro_presupuestal", length = 50)
    private String numeroRegistroPresupuestal;

    @Column(name = "fecha_registro_presupuestal")
    private LocalDate fechaRegistroPresupuestal;

    @Column(name = "centro_costo", length = 100)
    private String centroCosto;

    @CreationTimestamp
    @Column(name = "fecha_creacion", nullable = false, updatable = false)
    private Instant fechaCreacion;

    @UpdateTimestamp
    @Column(name = "fecha_actualizacion", nullable = false)
    private Instant fechaActualizacion;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getNumeroContrato() { return numeroContrato; }
    public void setNumeroContrato(String numeroContrato) { this.numeroContrato = numeroContrato; }

    public String getObjeto() { return objeto; }
    public void setObjeto(String objeto) { this.objeto = objeto; }

    public BigDecimal getValor() { return valor; }
    public void setValor(BigDecimal valor) { this.valor = valor; }

    public LocalDate getFechaInicio() { return fechaInicio; }
    public void setFechaInicio(LocalDate fechaInicio) { this.fechaInicio = fechaInicio; }

    public LocalDate getFechaFin() { return fechaFin; }
    public void setFechaFin(LocalDate fechaFin) { this.fechaFin = fechaFin; }

    public EstadoContrato getEstado() { return estado; }
    public void setEstado(EstadoContrato estado) { this.estado = estado; }

    public Usuario getSupervisor() { return supervisor; }
    public void setSupervisor(Usuario supervisor) { this.supervisor = supervisor; }

    public String getTipoContrato() { return tipoContrato; }
    public void setTipoContrato(String tipoContrato) { this.tipoContrato = tipoContrato; }

    public String getContratista() { return contratista; }
    public void setContratista(String contratista) { this.contratista = contratista; }

    public String getContratistaNit() { return contratistaNit; }
    public void setContratistaNit(String contratistaNit) { this.contratistaNit = contratistaNit; }

    public String getRepresentanteLegal() { return representanteLegal; }
    public void setRepresentanteLegal(String representanteLegal) { this.representanteLegal = representanteLegal; }

    public String getLugarEjecucion() { return lugarEjecucion; }
    public void setLugarEjecucion(String lugarEjecucion) { this.lugarEjecucion = lugarEjecucion; }

    public String getNumeroRegistroPresupuestal() { return numeroRegistroPresupuestal; }
    public void setNumeroRegistroPresupuestal(String numeroRegistroPresupuestal) { this.numeroRegistroPresupuestal = numeroRegistroPresupuestal; }

    public LocalDate getFechaRegistroPresupuestal() { return fechaRegistroPresupuestal; }
    public void setFechaRegistroPresupuestal(LocalDate fechaRegistroPresupuestal) { this.fechaRegistroPresupuestal = fechaRegistroPresupuestal; }

    public String getCentroCosto() { return centroCosto; }
    public void setCentroCosto(String centroCosto) { this.centroCosto = centroCosto; }

    public Instant getFechaCreacion() { return fechaCreacion; }
    public Instant getFechaActualizacion() { return fechaActualizacion; }
}
