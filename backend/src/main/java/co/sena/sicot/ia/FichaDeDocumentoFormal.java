package co.sena.sicot.ia;

import co.sena.sicot.dto.etapa.EtapaResponse;
import co.sena.sicot.dto.etapa.SubetapaResponse;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Responde «¿qué es el GCCON-F-031, quién lo firma y en qué sub-paso se
 * genera?» sin pasar por el modelo de IA.
 *
 * <h2>Por qué existe</h2>
 * Cuatro de las cinco sugerencias rápidas del panel del Copiloto preguntan
 * esto mismo por un documento distinto, y la respuesta no cambia nunca: el
 * código, el sub-paso y quién firma son datos fijos del procedimiento
 * GCCON-P-010, los mismos que el prompt le pasaba al modelo en
 * {@code CopilotoChatService.CONOCIMIENTO_PROCESO} para que los repitiera.
 * Hasta el 1-10-2026 cada pulsación era una inferencia completa en un portátil
 * sin tarjeta gráfica —hasta ~158 s la primera del contrato, decenas de
 * segundos las siguientes— para que un modelo copiara cinco líneas de su propio
 * prompt — con el riesgo, medido en ADR-006, de que un modelo pequeño copie la
 * línea equivocada. Aquí se compone exacta y al instante, que es la doctrina de
 * ADR-006 y ADR-008: lo que el código ya sabe no se le pide al modelo.
 *
 * <h2>De dónde salen los datos</h2>
 * El código y el nombre de cada documento se leen de
 * {@link PlantillaDocumentoIA#CATALOGO}, el mismo catálogo con el que
 * {@code RedactorDeDocumentos} arma el PDF: si cambia allí, cambia aquí. El
 * sub-paso y la descripción son los de {@code FORMAL_DOCS} en
 * {@code frontend/src/data/contractFlow.ts}; el estado del sub-paso, el del
 * contrato real.
 *
 * <h2>Cuándo NO responde</h2>
 * Igual que {@link GuiaDelPasoActual}, es deliberadamente conservadora: solo
 * contesta si la pregunta nombra un documento conocido <b>y</b> pregunta qué
 * es, quién lo firma, dónde se genera o su código —o lo nombra a secas—. «¿Ya
 * puedo firmar el Acta de Inicio?» la contesta la guía con el estado de ese
 * sub-paso, y «¿qué hago si el contratista no la firma?» sigue hacia el
 * modelo.
 *
 * <p>«El acta» o «el informe» a secas no dicen cuál: la ficha da las de los
 * dos candidatos en vez de elegir uno.
 */
@Component
public class FichaDeDocumentoFormal {

    /**
     * Un documento sobre el que se puede contestar sin modelo.
     *
     * @param clave       su clave en {@link PlantillaDocumentoIA#CATALOGO}, la
     *                    que viaja en las acciones del Copiloto, o
     *                    {@code null} si SICOT no lo arma.
     * @param subpaso     sub-paso del GCCON-P-010 donde SICOT lo arma, o
     *                    {@code null} si SICOT no lo arma.
     * @param descripcion la línea que dice qué es o qué contiene, o {@code null}.
     * @param aclaracion  lo que hay que decir siempre para que no se confunda
     *                    con otro documento, o {@code null}.
     */
    record Documento(String clave, String codigo, String nombre, String subpaso, String descripcion, String firma,
                     String aclaracion, Pattern reconocer) {
    }

    private static final String FIRMA_DEL_SUPERVISOR =
            "usted, como supervisor, con la firma electrónica que el Administrador le asignó a su cuenta.";

    static final List<Documento> DOCUMENTOS = List.of(
            delCatalogo("ACTA_INICIO", "2.7",
                    "Qué contiene: fecha de inicio, duración, alcance y responsabilidades del supervisor.",
                    FIRMA_DEL_SUPERVISOR + " El formato lleva además la firma del representante legal del contratista.",
                    null,
                    "gccon ?f ?018", "acta de inicio"),
            delCatalogo("INFORME_SUPERVISION", "3.4",
                    "Qué contiene: control de la ejecución, inspección física, novedades y avances del contrato.",
                    FIRMA_DEL_SUPERVISOR, null,
                    "gccon ?f ?031", "informe de supervision"),
            delCatalogo("ACTA_RECIBO", "4.3",
                    "Qué contiene: la recepción formal de los bienes, con su cantidad, calidad y especificaciones "
                            + "técnicas.",
                    FIRMA_DEL_SUPERVISOR, null,
                    "gil ?f ?010", "acta de recibo", "recibo a satisfaccion"),
            delCatalogo("CERTIFICACION_CUMPLIMIENTO", "5.3",
                    "Qué es: la certificación del supervisor que respalda el trámite de pago.",
                    FIRMA_DEL_SUPERVISOR,
                    "Código: no tiene un código de formato oficial confirmado; «ESUCON» es como la llaman en el "
                            + "Centro. Si necesita el código, confírmelo con la Unidad de Gestión Contractual.",
                    "esucon", "certificacion de cumplimiento", "certificado de cumplimiento"),
            delCatalogo("INFORME_FINAL", "6.3",
                    "Qué contiene: el cumplimiento de las obligaciones y del objeto del contrato, y su estado "
                            + "financiero al cierre.",
                    FIRMA_DEL_SUPERVISOR,
                    "Ojo: no es el Acta de Liquidación.",
                    "gccon ?f ?030", "informe final"),
            // No es del supervisor y SICOT no lo arma, pero se le confunde con
            // los suyos (ver la nota de PlantillaDocumentoIA): por eso tiene
            // ficha, para decir de quién es.
            new Documento(null, "GRF-F-089", "Oficio de Pago", null, null,
                    "el Ordenador del gasto (Subdirector), no el supervisor.",
                    "En SICOT: no es uno de los documentos que usted firma, y SICOT no lo arma. Lo que usted "
                            + "firma para el pago es la Certificación de cumplimiento, en el sub-paso 5.3.",
                    reconocedor("grf ?f ?089", "oficio de pago")));

    /**
     * Frases que preguntan por el documento en sí. Se escriben en castellano
     * corriente: {@link PreguntaNormalizada} les quita tildes y signos igual
     * que a la pregunta.
     */
    // Se buscan con borde de palabra a los dos lados (contieneAlgunaFrase): con
    // el borde solo a la izquierda, «qué es» casaba con «qué escribo» y «qué
    // está», y la ficha contestaba preguntas abiertas sobre la redacción.
    private static final Set<String> PREGUNTAS_POR_EL_DOCUMENTO = PreguntaNormalizada.normalizarTodas(
            "qué es", "qué son", "qué significa", "para qué sirve", "qué contiene", "qué lleva", "qué tiene",
            "explícame", "explica",
            "quién firma", "quién lo firma", "quién la firma", "quiénes firman", "quién debe firmar",
            "en qué sub-paso", "en qué subpaso", "en qué paso", "en qué etapa", "en qué estado", "qué estado",
            "cuándo se genera", "cuándo se firma", "cuándo se hace",
            "dónde se genera", "dónde se firma", "dónde encuentro", "dónde lo encuentro", "dónde la encuentro",
            "de dónde saco", "dónde consigo", "cómo se genera", "cómo lo genero", "cómo la genero", "cómo se hace",
            "cómo se firma", "cómo lo firmo", "cómo la firmo",
            "qué código", "cuál es el código", "tiene código", "lleva código", "código oficial", "código de formato",
            "es lo mismo", "diferencia", "diferencias");

    /**
     * «El acta» o «el informe» a secas. Con el nombre abreviado la ficha no
     * reconocía ningún documento, y «¿en qué paso se firma el informe?» acababa
     * en la guía del paso actual, que contestaba el paso en el que va el
     * supervisor: otra pregunta, con toda seguridad. Ahora se dan las fichas de
     * los dos candidatos. «Acta de liquidación» y demás nombres con «de» no
     * entran: no son documentos de SICOT.
     */
    private static final Pattern ACTA_SIN_CALIFICAR = Pattern.compile("(?<= )actas?(?! de | del )(?![a-z0-9])");
    private static final Pattern INFORME_SIN_CALIFICAR = Pattern.compile("(?<= )informes?(?! de | del )(?![a-z0-9])");

    /** Palabras que no añaden pregunta: «¿Y el acta de inicio?» nombra el documento a secas. */
    private static final Set<String> PALABRAS_DE_RELLENO = Set.of(
            "y", "e", "o", "el", "la", "lo", "los", "las", "un", "una", "de", "del", "al", "sobre",
            "formato", "documento", "que", "hay", "con");

    /**
     * Las fichas de los documentos que nombra la pregunta, si es una pregunta
     * que se puede contestar sin modelo; vacía si no.
     */
    public List<Documento> reconocer(String pregunta) {
        PreguntaNormalizada p = PreguntaNormalizada.de(pregunta);
        // Mismo umbral que GuiaDelPasoActual: lo largo trae matices que una
        // ficha no cubre. «Si» porque una pregunta condicional —«¿qué pasa si
        // no llega?»— nunca es una pregunta por la ficha.
        if (p.estaVacia() || p.largo() > 200 || p.tienePalabra("si")) {
            return List.of();
        }
        List<Documento> nombrados = nombrados(p);
        boolean ambiguos = nombrados.isEmpty();
        if (ambiguos) {
            nombrados = candidatosDeUnNombreAbreviado(p);
            if (nombrados.isEmpty()) {
                return List.of();
            }
        }
        if (p.contieneAlgunaFrase(PREGUNTAS_POR_EL_DOCUMENTO) || (!ambiguos && soloLosNombra(p, nombrados))) {
            return nombrados;
        }
        return List.of();
    }

    public boolean puedeResponder(String pregunta) {
        return !reconocer(pregunta).isEmpty();
    }

    /**
     * ¿Es este trozo de una pregunta compuesta de los que contesta la ficha?
     * Ver {@link PreguntaNormalizada#otrasPreguntas}.
     */
    boolean cubre(String trozo) {
        PreguntaNormalizada p = PreguntaNormalizada.de(trozo);
        // «¿… y quién lo firma?» sigue hablando del mismo documento; «¿… y
        // dónde consigo la póliza?» trae una frase de la ficha pero pregunta
        // por otra cosa, y esa no se puede dar por contestada.
        return !nombrados(p).isEmpty() || (p.contieneAlgunaFrase(PREGUNTAS_POR_EL_DOCUMENTO)
                && OTRAS_COSAS.stream().noneMatch(p::tienePalabra));
    }

    /** Lo que, nombrado en otro trozo de la pregunta, no es el documento de la ficha. */
    private static final Set<String> OTRAS_COSAS = Set.of("poliza", "polizas", "garantia", "garantias", "factura",
            "facturas", "rut", "pila", "planilla", "cdp", "certificado", "camara", "contrato", "valor", "pago",
            "pagos", "fecha", "plazo");

    /** Los documentos que la pregunta nombra con su nombre o su código completo. */
    static List<Documento> nombrados(PreguntaNormalizada p) {
        return DOCUMENTOS.stream().filter(d -> p.coincide(d.reconocer())).toList();
    }

    /** Los documentos a los que puede referirse «el acta» o «el informe» dicho a secas. */
    static List<Documento> candidatosDeUnNombreAbreviado(PreguntaNormalizada p) {
        if (p.coincide(ACTA_SIN_CALIFICAR)) {
            return List.of(DOCUMENTOS.get(0), DOCUMENTOS.get(2));
        }
        if (p.coincide(INFORME_SIN_CALIFICAR)) {
            return List.of(DOCUMENTOS.get(1), DOCUMENTOS.get(4));
        }
        return List.of();
    }

    /**
     * Compone la respuesta para la pregunta, con el estado real de los
     * sub-pasos en este contrato.
     *
     * @return vacío si la pregunta no es para esta clase — quien llama sigue
     *         su camino normal.
     */
    public Optional<String> responder(String pregunta, List<EtapaResponse> etapas) {
        List<Documento> documentos = reconocer(pregunta);
        if (documentos.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(componer(documentos, etapas));
    }

    /** Las fichas de estos documentos, con el estado real de sus sub-pasos en el contrato. */
    String componer(List<Documento> documentos, List<EtapaResponse> etapas) {
        String fichas = documentos.stream()
                .map(d -> ficha(d, etapas == null ? List.of() : etapas))
                .collect(Collectors.joining("\n\n"));
        return fichas + "\n\nSi necesita más detalle, pregúnteme.";
    }

    /** La ficha del documento que SICOT arma en este sub-paso, si arma alguno. */
    static Optional<Documento> delSubpaso(String codigoSubpaso) {
        return DOCUMENTOS.stream().filter(d -> d.subpaso() != null && d.subpaso().equals(codigoSubpaso)).findFirst();
    }

    private static String ficha(Documento d, List<EtapaResponse> etapas) {
        StringBuilder sb = new StringBuilder(encabezado(d)).append('\n');
        if (d.descripcion() != null) {
            sb.append('\n').append(d.descripcion());
        }
        if (d.subpaso() == null) {
            sb.append("\nQuién firma: ").append(d.firma());
            if (d.aclaracion() != null) {
                sb.append('\n').append(d.aclaracion());
            }
            return sb.toString();
        }
        if (d.aclaracion() != null) {
            sb.append('\n').append(d.aclaracion());
        }
        Optional<EtapaResponse> etapa = etapaDelSubpaso(etapas, d.subpaso());
        sb.append("\nDónde se genera: en el sub-paso ").append(d.subpaso());
        etapa.ifPresent(e -> sb.append(" (Paso %d: %s)".formatted(e.numero(), e.nombre())));
        sb.append('.');
        sb.append("\nQuién firma: ").append(d.firma());
        // El flujo de la interfaz, dicho igual que en la guía y en el prompt
        // (FlujoDeFirma). Hasta el 2-10-2026 decía que lo que SICOT no sabe
        // «queda como dato pendiente» —la interfaz se lo pide antes— y que él
        // «lo revisa antes de firmarlo» sin decir dónde.
        sb.append("\nCómo se hace en SICOT: ").append(FlujoDeFirma.COMPLETO);
        etapa.flatMap(e -> e.subEtapas().stream().filter(s -> d.subpaso().equals(s.codigo())).findFirst())
                .filter(s -> s.estado() != null)
                .ifPresent(s -> sb.append("\nEn este contrato, el sub-paso %s está %s."
                        .formatted(s.codigo(), estado(s))));
        return sb.toString();
    }

    private static String encabezado(Documento d) {
        if (d.codigo() != null) {
            return "%s (%s).".formatted(d.nombre(), d.codigo());
        }
        // La Certificación: sin código oficial, se la nombra como la conoce el Centro.
        return "%s («ESUCON»).".formatted(d.nombre());
    }

    private static String estado(SubetapaResponse s) {
        return switch (s.estado()) {
            case PENDIENTE -> "pendiente";
            case EN_CURSO -> "en curso";
            case COMPLETADA -> "completado";
        };
    }

    private static Optional<EtapaResponse> etapaDelSubpaso(List<EtapaResponse> etapas, String codigo) {
        return etapas.stream()
                .filter(e -> e.subEtapas() != null
                        && e.subEtapas().stream().anyMatch(s -> codigo.equals(s.codigo())))
                .findFirst();
    }

    /** ¿La pregunta no dice nada más que el nombre del documento? «GCCON-F-031», «¿y el acta de inicio?». */
    private static boolean soloLosNombra(PreguntaNormalizada p, List<Documento> nombrados) {
        String resto = p.toString();
        for (Documento d : nombrados) {
            resto = PreguntaNormalizada.de(resto).sin(d.reconocer());
        }
        return Arrays.stream(resto.strip().split(" +"))
                .filter(palabra -> !palabra.isEmpty())
                .allMatch(PALABRAS_DE_RELLENO::contains);
    }

    private static Documento delCatalogo(String clave, String subpaso, String descripcion, String firma,
                                         String aclaracion, String... formas) {
        PlantillaDocumentoIA plantilla = PlantillaDocumentoIA.CATALOGO.get(clave);
        // «PENDIENTE_DE_DEFINIR» es una marca interna, no un código: no se le dice al supervisor.
        String codigo = plantilla.codigo().startsWith("PENDIENTE") ? null : plantilla.codigo();
        return new Documento(clave, codigo, plantilla.nombre(), subpaso, descripcion, firma, aclaracion,
                reconocedor(formas));
    }

    /**
     * Las formas de nombrar un documento, sobre la pregunta ya normalizada.
     * Los códigos admiten los separadores que la gente usa: «GCCON-F-031»,
     * «GCCON F031» y «gcconf031» quedan, normalizados, como «gccon f 031»,
     * «gccon f031» y «gcconf031», y los tres encajan en {@code gccon ?f ?031}.
     * Se exige el prefijo completo a propósito: «F-010» a secas podría ser
     * otro formato del SENA, y contestar con la ficha equivocada es peor que
     * dejarle la pregunta al modelo.
     */
    private static Pattern reconocedor(String... formas) {
        return Pattern.compile("(?<= )(?:" + String.join("|", formas) + ")(?![a-z0-9])");
    }
}
