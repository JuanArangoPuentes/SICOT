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

    /**
     * La hora que declaró el equipo cuando el cambio se hizo sin red y llegó
     * después por la cola de ADR-015; nula en todo lo que se hizo con conexión.
     * {@code fecha} sigue siendo cuándo lo recibió el servidor. Ver
     * {@code HechoSinConexionInterceptor} y {@code V24__hora_declarada_sin_conexion.sql}.
     */
    @Column(name = "hecho_sin_conexion_en", updatable = false)
    private Instant hechoSinConexionEn;

    /**
     * Qué herramienta automática declaró haber hecho esto, cuando
     * {@link #origen} es {@code AGENTE}. Nula en lo que hace una persona y en
     * lo que hace el motor. Ver {@code V26__quien_actuo_fue_un_agente.sql}.
     */
    @Column(length = 60)
    private String agente;

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

    public String getAgente() { return agente; }
    public void setAgente(String agente) { this.agente = agente; }

    public Instant getHechoSinConexionEn() { return hechoSinConexionEn; }
    public void setHechoSinConexionEn(Instant hechoSinConexionEn) { this.hechoSinConexionEn = hechoSinConexionEn; }
}
