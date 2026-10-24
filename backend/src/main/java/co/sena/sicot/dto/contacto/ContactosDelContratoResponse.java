package co.sena.sicot.dto.contacto;

import co.sena.sicot.entity.enums.RolDeContacto;

import java.util.List;

/**
 * Los profesionales de SST y ambiental del contrato (paso 2.2 del proceso real).
 *
 * @param contactos primero los de SST; vacía si el supervisor no los ha
 *                  registrado
 */
public record ContactosDelContratoResponse(List<Contacto> contactos) {

    /** @param nombre puede faltar: a veces el estudio previo solo trae el correo del área */
    public record Contacto(RolDeContacto rol, String nombre, String correo) {
    }
}
