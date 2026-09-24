package co.sena.sicot.dto.seguimiento;

import java.util.List;

/**
 * Un supervisor y los contratos que tiene abiertos.
 *
 * @param firmaVigente         si tiene una firma electrónica activa. Sin ella no
 *                             puede firmar ningún documento del proceso, así que
 *                             el Administrador lo ve aquí antes de que el
 *                             supervisor se tope con el error.
 * @param contratosFinalizados solo el número: el detalle de lo terminado no
 *                             hace falta para saber cómo va hoy, y con los años
 *                             sería la mayor parte de la respuesta.
 */
public record SupervisorSeguimiento(
        Long id,
        String nombre,
        String email,
        boolean activo,
        boolean firmaVigente,
        long contratosFinalizados,
        List<ContratoSeguimiento> contratos
) {
}
