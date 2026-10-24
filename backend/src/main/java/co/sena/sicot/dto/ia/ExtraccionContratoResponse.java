package co.sena.sicot.dto.ia;

import java.util.List;

/**
 * Campos que la IA propone extraer del documento cargado por Gestión.
 * Son una PROPUESTA — el usuario los revisa, corrige y confirma antes de que
 * se cree el contrato real (POST /api/contratos). Ningún campo se persiste
 * desde aquí.
 *
 * <p>Los cinco últimos salen del acto de designación (GCCON-F-032) y del acta
 * de inicio, que son los documentos con que el contrato entra de verdad al
 * proceso del supervisor (ver docs/producto/LISTA_CHEQUEO_ALEX.md).
 *
 * @param telefonoContratista no lo trae ningún documento del contrato: sale del
 *                             registro de proveedores del SECOP II
 * @param completadoDesdeSecop los campos que el documento no traía y se
 *                             tomaron de los datos abiertos del SECOP II; el
 *                             formulario los señala para que Gestión sepa de
 *                             dónde vino cada dato
 */
public record ExtraccionContratoResponse(
        String idContrato,
        String objeto,
        String proveedor,
        String nit,
        String representanteLegal,
        String valor,
        String vigenciaInicio,
        String vigenciaFin,
        String lugarEjecucion,
        String registroPresupuestal,
        String tipoContrato,
        String enlaceSecop,
        String supervisorDesignado,
        String apoyoSupervision,
        String fechaDesignacion,
        String correoContratista,
        String telefonoContratista,
        List<String> completadoDesdeSecop
) {

    public ExtraccionContratoResponse {
        completadoDesdeSecop = completadoDesdeSecop == null ? List.of() : List.copyOf(completadoDesdeSecop);
    }

    /**
     * Los once campos de siempre, con los de la designación vacíos. Es lo que
     * propone el modelo, que solo devuelve objeto y tipo.
     */
    public ExtraccionContratoResponse(String idContrato, String objeto, String proveedor, String nit,
                                      String representanteLegal, String valor, String vigenciaInicio,
                                      String vigenciaFin, String lugarEjecucion, String registroPresupuestal,
                                      String tipoContrato) {
        this(idContrato, objeto, proveedor, nit, representanteLegal, valor, vigenciaInicio, vigenciaFin,
                lugarEjecucion, registroPresupuestal, tipoContrato, null, null, null, null, null, null, List.of());
    }
}
