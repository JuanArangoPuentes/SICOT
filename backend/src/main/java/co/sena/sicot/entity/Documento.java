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

    /** Bloqueo optimista gestionado por Hibernate — ver {@code V12__bloqueo_optimista.sql}. */
    @Version
    @Column(name = "lock_version", nullable = false)
    private Long lockVersion;

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

    /**
     * Bytes del archivo, dentro de la fila (columna {@code BYTEA}).
     *
     * <p><b>Nunca lo cargue con una consulta de listado.</b> JPA carga los
     * atributos básicos de forma ansiosa, así que cualquier consulta que
     * devuelva entidades {@code Documento} trae también todos los bytes de
     * todos los archivos. Con el tope de 20 MB por archivo y un heap de
     * contenedor de ~750 MB, basta una cuarentena de documentos en un contrato
     * para provocar un {@code OutOfMemoryError} — y eso listando una pantalla
     * que ni siquiera muestra el contenido.
     *
     * <p>{@code @Basic(fetch = LAZY)} NO resuelve esto: sobre un atributo
     * básico, Hibernate lo ignora en silencio salvo que el proyecto active la
     * mejora de bytecode (bytecode enhancement), y una anotación que no hace
     * nada es peor que ninguna porque induce a confiar. La solución real está
     * en {@link co.sena.sicot.repository.DocumentoRepository}: los listados
     * usan una proyección JPQL que no menciona esta columna, así que los bytes
     * no salen de PostgreSQL. La entidad completa solo se carga en la descarga
     * y en la firma, que son operaciones de un documento a la vez.
     */
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

    /**
     * SHA-256 (hexadecimal, 64 caracteres) del contenido en el momento exacto
     * de la firma — ver {@code V13__huella_de_integridad_en_la_firma.sql}. Es
     * lo que permite detectar que los bytes cambiaron después de firmados.
     * {@code null} en documentos sin firmar y en los que se firmaron antes de
     * que existiera esta columna.
     */
    @Column(name = "firma_hash_sha256", length = 64)
    private String firmaHashSha256;

    /**
     * Quién firmó, como clave foránea y no como texto dentro de un registro de
     * auditoría. Distinto de {@link #subidoPor}: quien carga un documento no
     * es necesariamente quien lo firma.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "firmado_por_id")
    private Usuario firmadoPor;

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

    public String getFirmaHashSha256() { return firmaHashSha256; }
    public void setFirmaHashSha256(String firmaHashSha256) { this.firmaHashSha256 = firmaHashSha256; }

    public Usuario getFirmadoPor() { return firmadoPor; }
    public void setFirmadoPor(Usuario firmadoPor) { this.firmadoPor = firmadoPor; }

    public Usuario getSubidoPor() { return subidoPor; }
    public void setSubidoPor(Usuario subidoPor) { this.subidoPor = subidoPor; }

    public Instant getFechaSubida() { return fechaSubida; }
    public Instant getFechaActualizacion() { return fechaActualizacion; }
}
