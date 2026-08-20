package co.sena.sicot.entity;

import co.sena.sicot.entity.enums.EstadoFormato;
import co.sena.sicot.entity.enums.TipoDocumento;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Table(name = "formatos_documentales")
public class FormatoDocumental {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String codigo;

    @Column(nullable = false, length = 255)
    private String nombre;

    @Column(nullable = false, length = 20)
    private String version = "v1";

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_archivo", nullable = false, length = 20)
    private TipoDocumento tipoArchivo;

    @Column(name = "nombre_archivo", nullable = false, length = 255)
    private String nombreArchivo;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @JdbcTypeCode(SqlTypes.VARBINARY)
    @Column(nullable = false)
    private byte[] contenido;

    @Column(name = "tamanio_bytes", nullable = false)
    private long tamanioBytes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoFormato estado = EstadoFormato.VIGENTE;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subido_por_id")
    private Usuario subidoPor;

    @CreationTimestamp
    @Column(name = "fecha_creacion", nullable = false, updatable = false)
    private Instant fechaCreacion;

    @UpdateTimestamp
    @Column(name = "fecha_actualizacion", nullable = false)
    private Instant fechaActualizacion;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getCodigo() { return codigo; }
    public void setCodigo(String codigo) { this.codigo = codigo; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }

    public TipoDocumento getTipoArchivo() { return tipoArchivo; }
    public void setTipoArchivo(TipoDocumento tipoArchivo) { this.tipoArchivo = tipoArchivo; }

    public String getNombreArchivo() { return nombreArchivo; }
    public void setNombreArchivo(String nombreArchivo) { this.nombreArchivo = nombreArchivo; }

    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }

    public byte[] getContenido() { return contenido; }
    public void setContenido(byte[] contenido) { this.contenido = contenido; }

    public long getTamanioBytes() { return tamanioBytes; }
    public void setTamanioBytes(long tamanioBytes) { this.tamanioBytes = tamanioBytes; }

    public EstadoFormato getEstado() { return estado; }
    public void setEstado(EstadoFormato estado) { this.estado = estado; }

    public Usuario getSubidoPor() { return subidoPor; }
    public void setSubidoPor(Usuario subidoPor) { this.subidoPor = subidoPor; }

    public Instant getFechaCreacion() { return fechaCreacion; }
    public Instant getFechaActualizacion() { return fechaActualizacion; }
}
