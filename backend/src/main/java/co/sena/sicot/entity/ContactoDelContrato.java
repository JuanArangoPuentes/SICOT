package co.sena.sicot.entity;

import co.sena.sicot.entity.enums.RolDeContacto;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Un profesional de SST o ambiental al que el supervisor le avisa del contrato.
 *
 * <p>El paso 3 de la lista de chequeo de Alex Zapata pide identificarlos en el
 * estudio previo y escribirles que el contrato inició, «invitándolos a las
 * entregas». Guardarlos con el contrato es lo que permite que los dos correos
 * que les manda el supervisor —el de inicio (2.2) y el de las fechas acordadas
 * (2.4)— salgan ya con destinatarios.
 */
@Entity
@Table(name = "contactos_del_contrato")
public class ContactoDelContrato {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "contrato_id", nullable = false)
    private Contrato contrato;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RolDeContacto rol;

    @Column(length = 255)
    private String nombre;

    @Column(nullable = false, length = 255)
    private String correo;

    protected ContactoDelContrato() {
    }

    public ContactoDelContrato(Contrato contrato, RolDeContacto rol, String nombre, String correo) {
        this.contrato = contrato;
        this.rol = rol;
        this.nombre = nombre;
        this.correo = correo;
    }

    public Long getId() {
        return id;
    }

    public Contrato getContrato() {
        return contrato;
    }

    public RolDeContacto getRol() {
        return rol;
    }

    public String getNombre() {
        return nombre;
    }

    public String getCorreo() {
        return correo;
    }
}
