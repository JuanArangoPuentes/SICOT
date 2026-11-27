package co.sena.sicot.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * Un token que dejó de valer porque alguien cerró su sesión.
 *
 * <p>Es una lista de <b>negación</b>: lo que se guarda son las excepciones, no
 * las sesiones válidas, de modo que la autenticación sigue sin estado. Ver
 * {@code V25__cierre_de_sesion_real.sql} para el porqué y para lo que no cubre.
 *
 * <p>Sin {@code @Version}: esta fila se inserta una vez y no se modifica nunca.
 * El bloqueo optimista protege de actualizaciones perdidas, y aquí no hay
 * actualizaciones.
 */
@Entity
@Table(name = "sesiones_cerradas")
public class SesionCerrada {

    /**
     * El {@code jti} del token (RFC 7519 §4.1.7). Clave primaria, y no una
     * columna más con un id autogenerado, porque la comprobación que corre en
     * <i>cada petición</i> es exactamente «¿existe esta clave?»: con la clave
     * primaria es una búsqueda por índice único y no hay forma de que se
     * duplique.
     */
    @Id
    @Column(length = 64)
    private String jti;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @CreationTimestamp
    @Column(name = "cerrada_en", nullable = false, updatable = false)
    private Instant cerradaEn;

    /** Cuándo habría caducado el token solo: pasada esta fecha la fila sobra. */
    @Column(name = "expira_en", nullable = false)
    private Instant expiraEn;

    protected SesionCerrada() {
        // Hibernate
    }

    public SesionCerrada(String jti, Usuario usuario, Instant expiraEn) {
        this.jti = jti;
        this.usuario = usuario;
        this.expiraEn = expiraEn;
    }

    public String getJti() { return jti; }

    public Usuario getUsuario() { return usuario; }

    public Instant getCerradaEn() { return cerradaEn; }

    public Instant getExpiraEn() { return expiraEn; }
}
