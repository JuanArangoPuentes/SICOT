package co.sena.sicot.entity;

import co.sena.sicot.entity.enums.OrigenRegistro;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "registros")
public class Registro {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contrato_id")
    private Contrato contrato;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    @Column(nullable = false, length = 100)
    private String accion;

    @Column(columnDefinition = "TEXT")
    private String descripcion;

    /**
     * Quién produjo la entrada. Existe porque {@code usuario} nulo es ambiguo:
     * podría ser el sistema o un dato perdido. Ver ADR-008 y
     * {@code V15__motor_de_automatizaciones.sql}.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private OrigenRegistro origen = OrigenRegistro.USUARIO;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant fecha;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Contrato getContrato() { return contrato; }
    public void setContrato(Contrato contrato) { this.contrato = contrato; }

    public Usuario getUsuario() { return usuario; }
    public void setUsuario(Usuario usuario) { this.usuario = usuario; }

    public String getAccion() { return accion; }
    public void setAccion(String accion) { this.accion = accion; }

    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }

    public OrigenRegistro getOrigen() { return origen; }
    public void setOrigen(OrigenRegistro origen) { this.origen = origen; }

    public Instant getFecha() { return fecha; }
}
