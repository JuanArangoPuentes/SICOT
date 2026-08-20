package co.sena.sicot.entity;

import co.sena.sicot.entity.enums.EstadoDocumento;
import co.sena.sicot.entity.enums.TipoDocumento;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

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

    @Column(name = "content_type", length = 100)
    private String contentType;

    @JdbcTypeCode(SqlTypes.VARBINARY)
    private byte[] contenido;

    @Column(name = "tamanio_bytes")
    private Long tamanioBytes;

    // true = el Copiloto IA generó este documento; el usuario solo lo firma.
    @Column(name = "generado_por_ia", nullable = false)
    private boolean generadoPorIa = false;

    // Referencia a FirmaElectronica.firmaId de quien firmó (no es FK: la
    // firma pertenece a la cuenta, se copia aquí como evidencia histórica).
    @Column(name = "firma_id", length = 50)
    private String firmaId;

    @Column(name = "fecha_firma")
    private Instant fechaFirma;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subido_por_id")
    private Usuario subidoPor;

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

    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }

    public byte[] getContenido() { return contenido; }
    public void setContenido(byte[] contenido) { this.contenido = contenido; }

    public Long getTamanioBytes() { return tamanioBytes; }
    public void setTamanioBytes(Long tamanioBytes) { this.tamanioBytes = tamanioBytes; }

    public boolean isGeneradoPorIa() { return generadoPorIa; }
    public void setGeneradoPorIa(boolean generadoPorIa) { this.generadoPorIa = generadoPorIa; }

    public String getFirmaId() { return firmaId; }
    public void setFirmaId(String firmaId) { this.firmaId = firmaId; }

    public Instant getFechaFirma() { return fechaFirma; }
    public void setFechaFirma(Instant fechaFirma) { this.fechaFirma = fechaFirma; }

    public Usuario getSubidoPor() { return subidoPor; }
    public void setSubidoPor(Usuario subidoPor) { this.subidoPor = subidoPor; }

    public Instant getFechaSubida() { return fechaSubida; }
    public Instant getFechaActualizacion() { return fechaActualizacion; }
}
