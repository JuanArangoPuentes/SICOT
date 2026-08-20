package co.sena.sicot.dto.ia;

/**
 * Campos que la IA propone extraer del documento cargado por Gestión.
 * Son una PROPUESTA — el usuario los revisa, corrige y confirma antes de que
 * se cree el contrato real (POST /api/contratos). Ningún campo se persiste
 * desde aquí.
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
        String tipoContrato
) {
}
