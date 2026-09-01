package co.sena.sicot.entity;

import co.sena.sicot.entity.enums.EstadoEtapa;
import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "etapas", uniqueConstraints = @UniqueConstraint(columnNames = {"contrato_id", "numero"}))
public class Etapa {

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

    @Column(nullable = false, length = 150)
    private String nombre;

    @Column(nullable = false)
    private int numero;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoEtapa estado = EstadoEtapa.PENDIENTE;

    @Column(nullable = false)
    private int porcentaje = 0;

    @OneToMany(mappedBy = "etapa", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("codigo ASC")
    private List<Subetapa> subEtapas = new ArrayList<>();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Contrato getContrato() { return contrato; }
    public void setContrato(Contrato contrato) { this.contrato = contrato; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public int getNumero() { return numero; }
    public void setNumero(int numero) { this.numero = numero; }

    public EstadoEtapa getEstado() { return estado; }
    public void setEstado(EstadoEtapa estado) { this.estado = estado; }

    public int getPorcentaje() { return porcentaje; }
    public void setPorcentaje(int porcentaje) { this.porcentaje = porcentaje; }

    public List<Subetapa> getSubEtapas() { return subEtapas; }
    public void setSubEtapas(List<Subetapa> subEtapas) { this.subEtapas = subEtapas; }
}
