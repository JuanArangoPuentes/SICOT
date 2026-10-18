package co.sena.sicot.ia;

import co.sena.sicot.dto.etapa.EtapaResponse;
import co.sena.sicot.dto.etapa.SubetapaResponse;
import co.sena.sicot.dto.ia.ChatResponse;
import co.sena.sicot.entity.enums.EstadoSubetapa;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Responde sin modelo «¿cómo verifico la factura?», «¿dónde reviso las
 * pólizas?» o «¿de dónde saco la planilla PILA?».
 *
 * <h2>Por qué existe</h2>
 * Hasta el 18-10-2026 estas preguntas iban al modelo a propósito (ver
 * {@link GuiaDelPasoActual}): parecían abiertas. Medidas en vivo ese día con
 * qwen2.5:7b contra el contrato CO1.PCCNTR.10057820, que estaba en el 2.1:
 * <ul>
 *   <li>«¿Dónde reviso que las pólizas estén vigentes?» → contestó sobre el
 *       2.1 y el 2.4, el paso en curso, y no sobre las pólizas.</li>
 *   <li>«¿Cómo verifico la planilla PILA…?» → «envíe la planilla a la Unidad de
 *       Gestión Contractual para revisión», un trámite que no está en el
 *       proceso.</li>
 *   <li>«¿Qué debo revisar al recibir la factura electrónica?» → «envíe la
 *       factura a SICOT», donde no se carga nada.</li>
 * </ul>
 * Y lo que esas preguntas piden sí lo sabe SICOT: dónde se consigue el insumo
 * (lo mismo que dice el prompt en «Dónde se consiguen los insumos»), qué
 * sub-paso es, en qué estado está en este contrato y cómo se registra. Es la
 * misma decisión que la guía: lo que el sistema sabe se compone exacto.
 *
 * <h2>Qué sigue yendo al modelo</h2>
 * Un caso con problema —«¿qué hago si la póliza está vencida?», «la factura no
 * coincide con el contrato»— pide consejo, y eso no está en ninguna tabla.
 * Ante la duda, la pregunta no encaja y sigue su camino; lo que escriba el
 * modelo pasa después por {@link CorrectorDeCargas}.
 */
final class FichaDelInsumo {

    /**
     * Un insumo que el supervisor verifica fuera de SICOT.
     *
     * @param enElNombreDelSubpaso cómo se reconoce su sub-paso por el nombre
     *     normalizado: los contratos de antes del 15-10-2026 tienen otros
     *     sub-pasos con los mismos códigos, así que el código no basta.
     * @param sinDetalleEnSicot si SICOT no dice qué revisar en detalle: en el
     *     SECOP sí lo dice la guía del sub-paso (dónde mirar en el expediente).
     */
    private record Insumo(Pattern enLaPregunta, String dondeSeConsigue, String enElNombreDelSubpaso,
                          boolean sinDetalleEnSicot) {
    }

    // Las frases de «dónde» dicen lo mismo que el prompt (CopilotoChatService,
    // «Dónde se consiguen los insumos»): si una cambia, hay que cambiar la otra,
    // o el atajo y el modelo darán dos respuestas distintas.
    private static final List<Insumo> INSUMOS = List.of(
            new Insumo(Pattern.compile("\\b(factura|facturas|fev)\\b"),
                    "La factura electrónica se verifica en el portal de la DIAN.", "factura electronica", true),
            new Insumo(Pattern.compile("\\b(pila|planilla|planillas|seguridad social)\\b"),
                    "La planilla PILA la entrega el contratista, o se consulta en el operador de seguridad social.",
                    "aportes a seguridad social", true),
            new Insumo(Pattern.compile("\\b(poliza|polizas|garantia|garantias)\\b"),
                    "Las pólizas se consultan con la aseguradora o en el SECOP II.", "vigencia de garantias", true),
            // Va al final: «¿dónde reviso las pólizas en el SECOP?» es de pólizas.
            // La frase sale de la descripción del sub-paso en el proceso real (V20).
            // «secop» a secas no basta para el sub-paso: el 1.1 se llama
            // «Suscripción y publicación del contrato en SECOP II» y va antes
            // (visto en vivo el 18-10-2026, la respuesta caía en el 1.1).
            new Insumo(Pattern.compile("\\bsecop\\b"),
                    "Las cantidades, las especificaciones y los valores del contrato se verifican en el SECOP II, "
                            + "entrando al proceso con su enlace.", "especificaciones en secop", false));

    /** Lo que pregunta cómo o dónde, sin traer un caso concreto. */
    private static final Pattern COMO_O_DONDE = Pattern.compile(
            "\\b(como|donde|que reviso|que debo revisar|que tengo que revisar|que verifico|que debo verificar"
                    + "|que hago con|reviso|verifico|consigo|saco|obtengo|subo|cargo|adjunto)\\b");

    /** Un caso con problema o una condición: consejo, no tabla. Va al modelo. */
    private static final Pattern UN_CASO = Pattern.compile(
            "\\b(si|no|vencid\\w*|vence\\w*|venz\\w*|venci\\w*|falta\\w*|error\\w*|distint\\w*|diferente\\w*"
                    + "|rechaz\\w*|mal|incorrect\\w*|pasa|ocurre|coincide\\w*|por que|porque)\\b");

    /** «… y qué hago en SICOT»: lo contesta la línea de cómo se registra. */
    private static final Set<String> EN_SICOT = PreguntaNormalizada.normalizarTodas(
            "en sicot", "cómo lo registro", "cómo se registra", "cómo lo marco", "qué botón");

    private FichaDelInsumo() {
    }

    /**
     * @return la respuesta sobre el insumo que nombra la pregunta, con el botón
     *     para abrir su sub-paso; o vacío si la pregunta no es de esta tabla o
     *     el contrato no tiene ese sub-paso, para que siga su camino.
     */
    static Optional<ChatResponse> responder(String pregunta, List<EtapaResponse> etapas) {
        Optional<Insumo> insumo = reconocer(pregunta);
        if (insumo.isEmpty() || etapas == null) {
            return Optional.empty();
        }
        for (EtapaResponse etapa : etapas) {
            List<SubetapaResponse> subs = etapa.subEtapas() == null ? List.of() : etapa.subEtapas();
            for (SubetapaResponse sub : subs) {
                if (PreguntaNormalizada.normalizar(sub.nombre()).contains(insumo.get().enElNombreDelSubpaso())) {
                    return Optional.of(ChatResponse.delSistema(componer(insumo.get(), etapa, sub),
                            AccionesDelCopiloto.abrirSubpaso(etapa.numero(), sub)));
                }
            }
        }
        return Optional.empty();
    }

    /** ¿Este trozo de una pregunta compuesta ya lo contesta la ficha? */
    static boolean cubre(String trozo) {
        return reconocer(trozo).isPresent() || PreguntaNormalizada.de(trozo).contieneAlguna(EN_SICOT);
    }

    private static Optional<Insumo> reconocer(String pregunta) {
        PreguntaNormalizada p = PreguntaNormalizada.de(pregunta);
        if (p.estaVacia() || p.largo() > 200 || !p.coincide(COMO_O_DONDE) || p.coincide(UN_CASO)) {
            return Optional.empty();
        }
        return INSUMOS.stream().filter(i -> p.coincide(i.enLaPregunta())).findFirst();
    }

    private static String componer(Insumo insumo, EtapaResponse etapa, SubetapaResponse sub) {
        String estado = sub.estado() == EstadoSubetapa.COMPLETADA ? "ya está completado"
                : sub.estado() == EstadoSubetapa.EN_CURSO ? "está en curso" : "está pendiente";
        return insumo.dondeSeConsigue()
                + "\n\nEs el sub-paso %s: %s, del paso %d: %s, que en este contrato %s."
                        .formatted(sub.codigo(), sub.nombre(), etapa.numero(), etapa.nombre(), estado)
                + "\n\n" + (CorrectorDeCargas.recibeArchivos(sub) ? "" : "En ese sub-paso SICOT no recibe ningún archivo. ")
                + GuiaDelPasoActual.comoSeRegistra(sub)
                + (insumo.sinDetalleEnSicot()
                        ? "\n\nQué revisar en detalle no lo trae SICOT: si tiene dudas, confírmelo con la Unidad de "
                                + "Gestión Contractual."
                        : "");
    }
}
