package co.sena.sicot.entity;

import co.sena.sicot.entity.enums.EstadoDocumento;
import co.sena.sicot.entity.enums.TipoDocumento;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Table(name = "documentos")
public class Documento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "contrato_id", nullable = false)
    private Contrato contrato;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subetapa_id")
    private Subetapa subetapa;

    @Column(nullable = false, length = 255)
    private String nombre;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoDocumento tipo;

    @Column(name = "ruta_archivo", length = 500)
    private String rutaArchivo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoDocumento estado = EstadoDocumento.PENDIENTE;

    @CreationTimestamp
    @Column(name = "fecha_subida", nullable = false, updatable = false)
    private Instant fechaSubida;

    @UpdateTimestamp
    @Column(name = "fecha_actualizacion", nullable = false)
    private Instant fechaActualizacion;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Contrato getContrato() { return contrato; }
    public void setContrato(Contrato contrato) { this.contrato = contrato; }

    public Subetapa getSubetapa() { return subetapa; }
    public void setSubetapa(Subetapa subetapa) { this.subetapa = subetapa; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public TipoDocumento getTipo() { return tipo; }
    public void setTipo(TipoDocumento tipo) { this.tipo = tipo; }

    public String getRutaArchivo() { return rutaArchivo; }
    public void setRutaArchivo(String rutaArchivo) { this.rutaArchivo = rutaArchivo; }

    public EstadoDocumento getEstado() { return estado; }
    public void setEstado(EstadoDocumento estado) { this.estado = estado; }

    public Instant getFechaSubida() { return fechaSubida; }
    public Instant getFechaActualizacion() { return fechaActualizacion; }
}
