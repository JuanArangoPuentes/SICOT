package co.sena.sicot.ia;

import co.sena.sicot.dto.documento.DocumentoResponse;
import co.sena.sicot.dto.etapa.SubetapaResponse;
import co.sena.sicot.dto.ia.ChatResponse.Accion;
import co.sena.sicot.dto.ia.ChatResponse.TipoAccion;

import java.util.Optional;

/**
 * Las acciones que el Copiloto puede ofrecer debajo de una respuesta, con su
 * etiqueta, armadas en un solo sitio para que el mismo sub-paso se ofrezca
 * igual venga de una orden, de la guía del paso o de la ficha de un documento.
 *
 * <p>Ninguna firma, marca, genera ni modifica nada: todas abren la pantalla
 * donde el supervisor decide, y la interfaz solo las ejecuta cuando él toca el
 * botón (contrato del chat, §1).
 */
final class AccionesDelCopiloto {

    private AccionesDelCopiloto() {
    }

    /**
     * Abrir un sub-paso. Si en él se firma un documento formal la acción lo
     * dice (y lleva su clave), y si es de los de la foto de la entrega, también:
     * la interfaz puede llevarlo al botón que corresponde.
     */
    static Accion abrirSubpaso(int paso, SubetapaResponse sub) {
        Optional<FichaDeDocumentoFormal.Documento> documento = FichaDeDocumentoFormal.delSubpaso(sub.codigo());
        if (documento.isPresent()) {
            FichaDeDocumentoFormal.Documento d = documento.get();
            return new Accion(TipoAccion.ABRIR_DOCUMENTO, paso, sub.codigo(), d.clave(), null,
                    "Abrir el sub-paso %s · %s".formatted(sub.codigo(), d.nombre()));
        }
        if (GuiaDelPasoActual.SUBPASOS_CON_EVIDENCIA_FOTOGRAFICA.contains(sub.codigo())) {
            return new Accion(TipoAccion.ABRIR_EVIDENCIA, paso, sub.codigo(), null, null,
                    "Abrir la carga de fotos (%s)".formatted(sub.codigo()));
        }
        return new Accion(TipoAccion.IR_A_SUBPASO, paso, sub.codigo(), null, null, "Abrir el sub-paso " + sub.codigo());
    }

    static Accion irAPaso(int paso) {
        return new Accion(TipoAccion.IR_A_PASO, paso, null, null, null, "Abrir el paso " + paso);
    }

    static Accion mostrarAlertas() {
        return new Accion(TipoAccion.MOSTRAR_ALERTAS, null, null, null, null, "Ver Alertas");
    }

    static Accion mostrarDocumentos() {
        return new Accion(TipoAccion.MOSTRAR_DOCUMENTOS, null, null, null, null, "Ver Documentos");
    }

    static Accion irAConfiguracion() {
        return new Accion(TipoAccion.IR_A_CONFIGURACION, null, null, null, null, "Abrir Configuración");
    }

    /** Solo con un documento que existe y que ya está firmado: quien llama lo comprueba. */
    static Accion descargar(FichaDeDocumentoFormal.Documento d, DocumentoResponse firmado) {
        return new Accion(TipoAccion.DESCARGAR_DOCUMENTO, null, d.subpaso(), d.clave(), firmado.id(),
                "Descargar " + d.nombre());
    }
}
