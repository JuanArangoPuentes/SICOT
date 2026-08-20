package co.sena.sicot.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Table(name = "firmas_electronicas")
public class FirmaElectronica {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Column(name = "firma_id", nullable = false, length = 50)
    private String firmaId;

    @Column(nullable = false)
    private boolean activa = true;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asignado_por_id")
    private Usuario asignadoPor;

    @CreationTimestamp
    @Column(name = "fecha_asignacion", nullable = false, updatable = false)
    private Instant fechaAsignacion;

    @UpdateTimestamp
    @Column(name = "fecha_actualizacion", nullable = false)
    private Instant fechaActualizacion;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Usuario getUsuario() { return usuario; }
    public void setUsuario(Usuario usuario) { this.usuario = usuario; }

    public String getFirmaId() { return firmaId; }
    public void setFirmaId(String firmaId) { this.firmaId = firmaId; }

    public boolean isActiva() { return activa; }
    public void setActiva(boolean activa) { this.activa = activa; }

    public Usuario getAsignadoPor() { return asignadoPor; }
    public void setAsignadoPor(Usuario asignadoPor) { this.asignadoPor = asignadoPor; }

    public Instant getFechaAsignacion() { return fechaAsignacion; }
    public Instant getFechaActualizacion() { return fechaActualizacion; }
}
