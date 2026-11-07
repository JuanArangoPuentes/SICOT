package co.sena.sicot.dto.etapa;

import co.sena.sicot.entity.enums.EstadoSubetapa;

/**
 * @param documentosRequeridos lo que el supervisor debe cargar antes de cerrar
 *                             el paso, o {@code null}; hoy solo
 *                             {@code ACTA_FIRMADA_Y_ENVIO}
 */
public record SubetapaResponse(
        Long id,
        String codigo,
        String nombre,
        String descripcion,
        EstadoSubetapa estado,
        String responsable,
        String documentosRequeridos
) {

    public SubetapaResponse(Long id, String codigo, String nombre, String descripcion, EstadoSubetapa estado,
                            String responsable) {
        this(id, codigo, nombre, descripcion, estado, responsable, null);
    }
}
