package co.sena.sicot.entity;

import co.sena.sicot.entity.enums.EstadoSubetapa;
import jakarta.persistence.*;

@Entity
@Table(name = "subetapas", uniqueConstraints = @UniqueConstraint(columnNames = {"etapa_id", "codigo"}))
public class Subetapa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Bloqueo optimista gestionado por Hibernate — ver {@code V12__bloqueo_optimista.sql}. */
    @Version
    @Column(name = "lock_version", nullable = false)
    private Long lockVersion;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "etapa_id", nullable = false)
    private Etapa etapa;

    @Column(nullable = false, length = 20)
    private String codigo;

    @Column(nullable = false, length = 255)
    private String nombre;

    @Column(columnDefinition = "TEXT")
    private String descripcion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoSubetapa estado = EstadoSubetapa.PENDIENTE;

    @Column(nullable = false, length = 150)
    private String responsable;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Etapa getEtapa() { return etapa; }
    public void setEtapa(Etapa etapa) { this.etapa = etapa; }

    public String getCodigo() { return codigo; }
    public void setCodigo(String codigo) { this.codigo = codigo; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }

    public EstadoSubetapa getEstado() { return estado; }
    public void setEstado(EstadoSubetapa estado) { this.estado = estado; }

    public String getResponsable() { return responsable; }
    public void setResponsable(String responsable) { this.responsable = responsable; }
}
