package co.sena.sicot.dto.chequeo;

/** Qué clase de trámite verifica una lista de chequeo del catálogo. */
public enum TipoListaChequeo {

    /** Lista de una modalidad de selección (contratación directa, licitación pública, etc.). */
    MODALIDAD_SELECCION,

    /** Lista de un trámite contractual puntual (terminación anticipada y liquidación). */
    TRAMITE_CONTRACTUAL,

    /** Lista del proceso de Gestión de Recursos Financieros para registrar la obligación y pagar. */
    TRAMITE_PAGO
}
