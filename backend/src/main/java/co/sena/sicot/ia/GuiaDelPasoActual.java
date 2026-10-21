package co.sena.sicot.ia;

import co.sena.sicot.dto.etapa.EtapaResponse;
import co.sena.sicot.dto.etapa.SubetapaResponse;
import co.sena.sicot.dto.ia.ChatResponse;
import co.sena.sicot.entity.enums.EstadoSubetapa;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Responde «¿en qué paso voy y qué hago ahora?» sin pasar por el modelo de IA.
 *
 * <h2>Por qué existe</h2>
 * Porque el sistema ya sabe la respuesta. La etapa en curso, los subpasos
 * pendientes y el porcentaje de avance están en la base; pedirle a un modelo de
 * lenguaje que los lea del prompt y los repita es pedirle lo único que no
 * garantiza —sostener hechos— para obtener algo que el código puede componer
 * exacto. Es la misma decisión que ADR-008 tomó con el resumen periódico.
 *
 * <h2>Lo que se midió el 14 de septiembre de 2026</h2>
 * A la pregunta «acabo de recibir este contrato, ¿qué tengo que hacer en el paso
 * en el que está ahora?», sobre un contrato que estaba en la <b>etapa 1</b>:
 *
 * <table border="1">
 *   <caption>Etapa que indicó cada modelo</caption>
 *   <tr><th>Modelo</th><th>Tiempo</th><th>Respondió</th></tr>
 *   <tr><td>qwen2.5:1.5b</td><td>45,4 s</td><td>etapa 4 — <b>falso</b></td></tr>
 *   <tr><td>qwen2.5:3b</td><td>90,7 s</td><td>etapa 4 — <b>falso</b></td></tr>
 *   <tr><td>qwen2.5:7b</td><td>158,4 s</td><td>etapa 1 — correcto</td></tr>
 * </table>
 *
 * <p>Los dos modelos pequeños copiaron el «paso 4» de un ejemplo de estilo que
 * el propio prompt incluye, pese a la advertencia expresa de no copiar sus
 * datos. Al recortar ese ejemplo el de 3B dejó de decir «4»… y pasó a decir
 * «2», que sigue siendo falso. No era un problema de redacción del prompt: era
 * de capacidad.
 *
 * <p><b>En supervisión de contratos, un asistente que manda al paso equivocado
 * con seguridad es peor que no tener asistente.</b> De ahí esta clase.
 *
 * <h2>Qué gana el proyecto</h2>
 * La respuesta pasa de ~150 s a microsegundos, es correcta por construcción, sus
 * pruebas pueden afirmar el texto exacto, y funciona en un equipo sin Ollama.
 * Al modelo le quedan las preguntas abiertas —«¿qué pasa si el contratista se
 * atrasa?»—, que es donde sí aporta y donde no se le está pidiendo repetir
 * cifras. «¿De dónde saco la póliza?» estaba aquí como ejemplo de pregunta
 * abierta hasta el 18-10-2026, cuando se midió que el modelo la contestaba
 * sobre el paso en curso: ahora la atiende {@link FichaDelInsumo}.
 *
 * <h2>Cuando la pregunta nombra otro paso, un sub-paso o un documento</h2>
 * Hasta el 2-10-2026 la plantilla contestaba siempre el paso en curso:
 * «¿qué me falta en el paso 4?», «¿qué debo hacer en el 4.2?» o «¿ya puedo
 * firmar el acta de inicio?» recibían, al instante y con toda seguridad, la
 * lista del paso 2. Contestar otra pregunta con aplomo es el mismo error que
 * motivó la clase. Ahora, si la pregunta nombra un paso, un sub-paso o un
 * documento formal, la respuesta es sobre ESE, compuesta igual de exacta; y
 * si trae plazo, dinero o alertas («¿cuánto me falta para que se venza?»), la
 * guía no la toma: es de {@link FichaDelContrato}.
 */
@Component
public class GuiaDelPasoActual {

    /**
     * Frases que indican que el supervisor pregunta por su situación actual.
     *
     * <p>El encaminamiento es por palabras y no por otro modelo a propósito: si
     * decidir cuándo NO usar el modelo dependiera de un modelo, volveríamos al
     * mismo problema una capa más arriba. Una lista de frases es auditable, se
     * revisa en un <i>pull request</i> y falla de forma predecible: ante la
     * duda, no encaja y la pregunta sigue su camino normal hacia el modelo.
     *
     * <p>Se escriben una sola vez y en castellano corriente: {@link PreguntaNormalizada}
     * les quita tildes y signos igual que a la pregunta. Hasta el 1-10-2026 cada
     * frase iba dos veces, con tilde y sin ella, y la que faltaba se escapaba al
     * modelo.
     */
    private static final Set<String> SENALES = PreguntaNormalizada.normalizarTodas(
            "en qué paso", "en qué etapa", "qué paso sigue", "cuál es el siguiente paso", "qué sigue",
            "qué tengo que hacer", "qué debo hacer", "qué hago ahora", "por dónde empiezo", "en qué voy",
            "cuál es mi paso", "qué falta",
            // ── Variantes con pronombre ──────────────────────────────────
            //
            // Medido el 15 de septiembre de 2026 contra el sistema real: a la
            // pregunta «¿Qué me falta para cerrar el paso en el que estoy?»,
            // con el contrato en el paso 3, el copiloto respondió que faltaba
            // cerrar el PASO 4 y mandó al supervisor a los sub-pasos 4.1 y 4.2.
            //
            // El atajo existe justo para que esa pregunta no llegue al modelo,
            // y no la atajó por un detalle de literalidad: la lista traía
            // «qué falta», y «qué ME falta» no contiene esa subcadena. La
            // pregunta cayó en la ruta del modelo, que es la que ya se había
            // medido como poco fiable para identificar la etapa.
            //
            // Es la misma lección que motivó esta clase, aplicada a su propia
            // puerta de entrada: no basta con tener el camino determinista, hay
            // que asegurarse de que la forma en que la gente pregunta de verdad
            // entra por él. Se cubren los clíticos, no cada frase entera: «me
            // falta» atrapa «¿qué me falta?», «me falta para cerrar» y demás.
            "me falta", "me hace falta", "queda pendiente",
            "falta para cerrar", "falta para terminar", "falta por hacer",
            "me toca", "me corresponde",
            // ── Órdenes cortas ───────────────────────────────────────────
            //
            // 25-09-2026: en la prueba del APK el supervisor escribió
            // «siguiente paso» a secas. La lista traía «cuál es el siguiente
            // paso» pero no la orden sola, así que fue al modelo: esperó 120 s a
            // un precalentado y 163 s más a la respuesta, en un portátil sin
            // tarjeta gráfica. La respuesta era esta misma plantilla.
            "siguiente paso", "paso siguiente", "siguiente sub-paso", "siguiente subpaso", "próximo paso",
            // ── Lo que falta, dicho por el documento o por el paso ───────
            //
            // 1-10-2026: «¿qué documento falta?» no contenía «qué falta» (el
            // sustantivo va en medio) y se iba al modelo: minutos en CPU. Igual
            // «el paso en el que estoy», que es como la sugerencia rápida del
            // panel preguntaba por el paso actual.
            "qué necesito hacer", "qué documento falta", "qué documentos faltan", "documentos pendientes",
            "tengo pendiente", "hay pendiente", "paso en el que estoy", "paso en que estoy", "paso actual",
            "etapa actual",
            // 2-10-2026: «qué documento sigue» tenía el mismo fallo que «qué
            // documento falta» (el sustantivo en medio) y se iba al modelo.
            "qué documento sigue", "qué documentos siguen", "cuál sigue",
            // «¿Ya puedo firmar el acta de inicio?» o «¿ya puedo cerrar el
            // paso 4?»: el estado de los sub-pasos lo dice, y el modelo
            // copiaba el «le falta 4.2» del ejemplo de estilo de su prompt.
            "ya puedo firmar", "ya puedo cerrar", "ya puedo marcar", "ya puedo pasar", "ya puedo avanzar",
            "puedo cerrar el paso", "se puede cerrar", "se puede firmar");
            // «qué hago» o «continuar» como frase suelta dentro de una pregunta
            // se dejaron fuera a propósito: también aparecen en preguntas
            // abiertas («¿qué hago si no llega la póliza?») que esta plantilla
            // contestaría mal. Como pregunta COMPLETA sí son inequívocas: ver
            // PREGUNTAS_COMPLETAS.

    /**
     * Preguntas que solo se atajan si son la pregunta entera. «¿Qué necesito?»
     * a secas es el paso actual; «¿qué necesito para renovar la póliza?» es una
     * pregunta abierta que el modelo contesta mejor.
     */
    private static final Set<String> PREGUNTAS_COMPLETAS = PreguntaNormalizada.normalizarTodas(
            "qué necesito", "qué documentos necesito", "qué documento necesito", "qué hago", "qué hago aquí",
            "y ahora qué", "ahora qué hago", "siguiente");

    /**
     * Sub-pasos donde el supervisor carga fotos de la entrega. Es espejo de
     * {@code SUBETAPAS_CON_EVIDENCIA_FOTOGRAFICA} en
     * {@code frontend/src/data/contractFlow.ts}, igual que GcconP010Plantilla lo
     * es de STEPS_INITIAL: si allí cambia, aquí también, o la guía dirá «márquelo
     * como completado» en un sub-paso que pide la foto antes.
     */
    static final Set<String> SUBPASOS_CON_EVIDENCIA_FOTOGRAFICA = Set.of("3.1", "3.2");

    /**
     * Frases que solo se atajan si la pregunta nombra a la vez un paso o un
     * sub-paso: «explícame el paso 4» o «qué hay en el 3.2».
     * «Explícame eso» a secas sigue la conversación, y eso lo hace el modelo.
     */
    private static final Set<String> SENALES_CON_OBJETIVO = PreguntaNormalizada.normalizarTodas(
            "explícame", "explica", "qué hay en", "qué se hace en", "en qué consiste", "de qué se trata",
            "qué incluye", "qué tiene");

    /**
     * Más formas de pedir que le expliquen un paso o un sub-paso, que solo se
     * atajan si la pregunta es corta y nombra uno (ver {@link #pideQueLeExpliquen}).
     *
     * <p>20-10-2026: «¿y el subpaso 2.3?», escrita en el chat del contrato
     * CO1.PCCNTR.99015001 a continuación de otra respuesta, no traía ninguna
     * frase de las listas y se fue al modelo: 224 s, con el modelo recién
     * cargado. Explicar qué se hace en un paso o en un sub-paso es justo lo que
     * el motor existe para contestar al instante; al modelo le quedan las
     * preguntas de verdad específicas.
     */
    private static final Set<String> PIDE_EXPLICACION = PreguntaNormalizada.normalizarTodas(
            "explique", "qué significa", "para qué sirve", "para qué es", "háblame", "hábleme", "cuéntame",
            "cuénteme", "dígame", "detalla", "información", "ayúdame", "ayúdeme", "qué pasa en", "qué hago en",
            "qué debo", "qué se debe", "qué hay que", "qué se necesita", "qué necesito", "qué pide", "qué piden",
            "qué implica", "qué requiere", "qué verifico", "qué reviso", "qué se verifica", "qué se revisa",
            "de qué va");

    /**
     * Las de {@link #PIDE_EXPLICACION} tan cortas que solo cuentan como palabra
     * entera: «dime» no debe encontrarse dentro de «dimensiones».
     */
    private static final Set<String> PIDE_EXPLICACION_PALABRA = PreguntaNormalizada.normalizarTodas(
            "cómo", "quién", "qué es", "qué son", "dime", "info", "ayuda", "detalle", "detalles", "responsable");

    /**
     * Palabras que no añaden pregunta: «¿y el subpaso 2.3?», «el 2.3» o «paso
     * 4» a secas piden ese paso o ese sub-paso, y nada más. Es la misma idea que
     * {@code PALABRAS_DE_RELLENO} de {@link FichaDeDocumentoFormal} para «¿y el
     * acta de inicio?».
     */
    private static final Set<String> RELLENO = Set.of("y", "e", "el", "la", "lo", "los", "las", "al", "a", "en",
            "del", "de", "sub", "subpaso", "paso", "pasos", "etapa", "numero", "no", "entonces", "ahora", "pues",
            "bueno", "ok", "vale", "listo", "gracias", "por", "favor", "porfa", "que", "hay", "con", "sobre",
            "acerca", "ese", "este", "esa", "esta", "ver", "tal");
    private static final Pattern NUMERO_O_NUMERAL = Pattern.compile("[0-9]{1,2}|uno|dos|tres|cuatro|cinco|seis");

    /**
     * Las palabras con que se pide la explicación, ya normalizadas: no cuentan
     * como lo que la pregunta trae de más (ver {@link #palabrasDeMas}).
     * «Necesito» no está, a propósito: en «¿qué necesito para renovar la
     * póliza?» es parte del caso.
     */
    private static final Set<String> DE_LA_PETICION = Set.of("como", "quien", "es", "son", "significa", "sirve",
            "sirven", "para", "dime", "digame", "info", "informacion", "detalle", "detalles", "detalla", "detallame",
            "hablame", "hableme", "cuentame", "cuenteme", "explicame", "explica", "explicar", "explique",
            "expliqueme", "consiste", "trata", "se", "hace", "hacer", "hago", "va", "incluye", "tiene", "pasa",
            "debo", "debe", "ayuda", "ayudame", "ayudeme", "responsable", "me", "mi", "cual", "cuales");

    /**
     * Los pasos por su nombre: «explícame la inspección», «¿en qué consiste el
     * cierre?». Son las palabras con que empiezan los nombres de las seis
     * etapas del GCCON-P-010 (GcconP010Plantilla). «Acta de inicio» y
     * «certificación de cumplimiento» no van: son documentos, y su ficha
     * contesta antes.
     */
    private static final Pattern PASO_POR_NOMBRE = Pattern.compile(
            "(?<= )(designacion|arranque|inspeccion|recepcion|certificacion|cierre)(?= )");
    private static final Map<String, Integer> PASOS_POR_NOMBRE = Map.of(
            "designacion", 1, "arranque", 2, "inspeccion", 3, "recepcion", 4, "certificacion", 5, "cierre", 6);

    /**
     * Un sub-paso nombrado por lo que se hace en él: «¿cómo tomo la foto?»,
     * «explícame lo del aviso a SST».
     *
     * @param enElNombreDelSubpaso cómo se reconoce el sub-paso por su nombre
     *     normalizado, igual que en {@link FichaDelInsumo}: los contratos de
     *     antes del 15-10-2026 tienen otros sub-pasos con los mismos códigos,
     *     así que el código no basta. Si el contrato no tiene un sub-paso con
     *     ese nombre, la guía no contesta y la pregunta sigue su camino.
     */
    private record SubpasoPorNombre(Pattern enLaPregunta, String enElNombreDelSubpaso) {
    }

    private static final List<SubpasoPorNombre> SUBPASOS_POR_NOMBRE = List.of(
            porNombre("sst|ambiental|ambientales", "profesionales sst"),
            porNombre("contactar|contacto|acuerdo de entregas|acordar las entregas|acordar entregas"
                    + "|fechas de entrega", "contacto con el contratista"),
            porNombre("foto|fotos|fotografia|fotografias|fotografica|georreferenciad[a-z]*", "evidencia fotografica"),
            porNombre("bodega", "entrega en bodega"),
            porNombre("ficha tecnica", "ficha tecnica"),
            porNombre("pila|planilla|planillas|seguridad social|aportes", "aportes a seguridad social"),
            porNombre("factura|facturas|fev", "factura electronica"),
            porNombre("poliza|polizas|garantia|garantias", "vigencia de garantias"),
            porNombre("orden de pago|crp|registro presupuestal", "orden de pago"),
            porNombre("modificacion|modificaciones|adicion|adiciones|prorroga|prorrogas", "modificaciones o adiciones"),
            porNombre("objeto contractual|cumplimiento total", "cumplimiento total"),
            porNombre("sigep|archivo del expediente|archivar el expediente|cierre del expediente", "sigep"),
            // Al final, como en FichaDelInsumo: «secop» a secas no basta, el 1.1
            // también lo nombra («publicación del contrato en SECOP II»).
            porNombre("secop", "especificaciones en secop"));

    private static SubpasoPorNombre porNombre(String palabras, String enElNombreDelSubpaso) {
        return new SubpasoPorNombre(Pattern.compile("(?<= )(?:" + palabras + ")(?= )"), enElNombreDelSubpaso);
    }

    /** Lo que la guía ya dice en cada respuesta: «¿… y cómo lo registro en SICOT?» no queda sin contestar. */
    private static final Set<String> COMO_SE_REGISTRA = PreguntaNormalizada.normalizarTodas(
            "cómo lo registro", "cómo se registra", "cómo lo marco", "cómo lo hago", "qué botón");

    /**
     * Palabras de plazo, dinero o alertas. «¿Cuánto me falta para que se venza?»
     * trae «me falta», pero no pregunta por los sub-pasos: es de
     * {@link FichaDelContrato}. Ante la duda la guía no la toma.
     */
    private static final Set<String> OTRO_TEMA = Set.of("dia", "dias", "plazo", "fecha", "fechas", "pago",
            "pagos", "pagar", "valor", "semana", "semanas", "mes", "meses", "tiempo", "millones", "pesos",
            "atrasado", "atrasada", "atraso", "alerta", "alertas");
    // «vence», «venció», «vencimiento», pero también «venza»: el subjuntivo cambia la c por z.
    private static final Pattern VENCIMIENTO = Pattern.compile("(?<= )ven[cz]");

    /**
     * «¿En qué paso se firma…?», «¿en qué etapa va el…?»: la pregunta es por
     * dónde va un objeto, no por dónde va el supervisor. «¿En qué paso se
     * encuentra el contrato?» sí es la suya.
     */
    private static final Pattern PREGUNTA_POR_UN_OBJETO = Pattern.compile(
            "(?<= )en que (?:paso|etapa|sub paso|subpaso) "
                    + "(?:se (?!encuentra |encuentran |va |esta )[a-z]+ "
                    + "|(?:va|van|esta|estan|queda) (?:el|la|lo|los|las) (?!contrato |proceso |tramite ))");

    /** «¿Por qué no puedo firmar?» pide una causa, no la lista de pendientes. */
    private static final Pattern POR_QUE = Pattern.compile("^ (?:por que|porque) ");

    /**
     * El número de un sub-paso: «2.3» queda «2 3» al normalizar. Solo
     * sub-pasos posibles del GCCON-P-010 (paso 1 a 6).
     */
    static final Pattern SUBPASO = Pattern.compile("(?<= )([1-6]) ([1-9][0-9]?)(?= )");
    static final Pattern PASO = Pattern.compile(
            "(?<= )(?:paso|etapa)(?: numero| no)? ([1-6]|uno|dos|tres|cuatro|cinco|seis)(?= )");
    private static final Pattern DESPUES_DEL = Pattern.compile(
            "(?<= )despues (?:del|de la) (?:paso |etapa )?([1-6])(?= )");
    private static final Map<String, Integer> NUMEROS = Map.of(
            "uno", 1, "dos", 2, "tres", 3, "cuatro", 4, "cinco", 5, "seis", 6);

    /**
     * ¿Es una pregunta que se puede contestar sin modelo?
     *
     * <p>Deliberadamente conservador. Una pregunta condicional («¿qué tengo que
     * hacer si el contratista se atrasa?») trae la frase, pero lo que pide es
     * consejo sobre un caso, no el estado del contrato: esa la atiende el
     * modelo. Lo mismo una de plazo o de dinero, que es de la ficha del
     * contrato, o una que pregunta por dónde va otra cosa.
     */
    public boolean puedeResponder(String pregunta) {
        PreguntaNormalizada p = PreguntaNormalizada.de(pregunta);
        if (p.estaVacia()) {
            return false;
        }
        // Una pregunta larga casi siempre trae contexto o matices que esta
        // plantilla no cubre; se deja pasar al modelo en vez de contestar de
        // más. El umbral es generoso: "acabo de recibir este contrato, ¿qué
        // tengo que hacer exactamente en el paso en el que está ahora?" cabe.
        if (p.largo() > 200 || p.tienePalabra("si")) {
            return false;
        }
        if (OTRO_TEMA.stream().anyMatch(p::tienePalabra) || p.coincide(VENCIMIENTO)
                || p.coincide(PREGUNTA_POR_UN_OBJETO) || p.coincide(POR_QUE)) {
            return false;
        }
        // El Oficio de Pago no es del supervisor ni tiene sub-paso: la guía no
        // tiene nada exacto que decir de él.
        if (FichaDeDocumentoFormal.nombrados(p).stream().anyMatch(d -> d.subpaso() == null && d.seCargaEn() == null)) {
            return false;
        }
        // Con un documento no: «explícame el acta de inicio» pide qué es, y eso
        // lo dice la ficha del documento.
        boolean nombraUnPaso = p.coincide(SUBPASO) || p.coincide(PASO) || p.coincide(DESPUES_DEL);
        return p.contieneAlguna(SENALES) || p.esExactamenteAlguna(PREGUNTAS_COMPLETAS)
                || (nombraUnPaso && p.contieneAlguna(SENALES_CON_OBJETIVO))
                || pideQueLeExpliquen(pregunta, p, nombraUnPaso);
    }

    /**
     * «¿Y el subpaso 2.3?», «¿cómo hago el 2.4?», «explícame la inspección»,
     * «¿cómo tomo la foto?»: preguntas cortas que nombran un paso o un sub-paso
     * —por su número o por lo que se hace en él— y piden que se lo expliquen,
     * o solo lo nombran.
     *
     * <p>Lo que distingue «¿cómo tomo la foto?» de «¿cómo le digo al
     * contratista que las fotos salieron mal?» es cuánto más trae la pregunta
     * aparte de la referencia y de la petición: la segunda cuenta un caso, y
     * ese es del modelo. Por número se admiten dos palabras más («¿qué
     * necesito para el 2.3?»); por nombre, una sola, porque el nombre es menos
     * preciso: «¿qué necesito para renovar la póliza?» no pregunta por el 5.1.
     *
     * <p>Más corta que el resto de la guía (100 caracteres y no 200), por lo
     * mismo. Lo que {@link FichaDelInsumo} reconoce («¿cómo verifico la
     * factura?») se le deja a ella, que dice además dónde se consigue.
     */
    private static boolean pideQueLeExpliquen(String pregunta, PreguntaNormalizada p, boolean nombraUnPasoPorNumero) {
        boolean porNombre = p.coincide(PASO_POR_NOMBRE) || subpasoPorNombre(p).isPresent();
        if (!(nombraUnPasoPorNumero || porNombre) || p.largo() > 100 || FichaDelInsumo.reconoce(pregunta)) {
            return false;
        }
        int deMas = palabrasDeMas(p);
        if (deMas == 0) {
            return true;
        }
        boolean pide = p.contieneAlguna(SENALES_CON_OBJETIVO) || p.contieneAlguna(PIDE_EXPLICACION)
                || p.contieneAlgunaFrase(PIDE_EXPLICACION_PALABRA);
        return pide && deMas <= (nombraUnPasoPorNumero ? 2 : 1);
    }

    /**
     * Las palabras de la pregunta que no son la referencia al paso o al
     * sub-paso, ni relleno, ni la forma de pedir la explicación. «¿Y el
     * subpaso 2.3?» y «¿qué es el CRP?» no tienen ninguna; «¿cómo tomo la
     * foto?», una («tomo»).
     */
    private static int palabrasDeMas(PreguntaNormalizada p) {
        String resto = p.sin(PASO_POR_NOMBRE);
        for (SubpasoPorNombre s : SUBPASOS_POR_NOMBRE) {
            resto = s.enLaPregunta().matcher(resto).replaceAll(" ");
        }
        int deMas = 0;
        for (String palabra : resto.strip().split(" +")) {
            if (!palabra.isEmpty() && !RELLENO.contains(palabra) && !DE_LA_PETICION.contains(palabra)
                    && !NUMERO_O_NUMERAL.matcher(palabra).matches()) {
                deMas++;
            }
        }
        return deMas;
    }

    private static Optional<SubpasoPorNombre> subpasoPorNombre(PreguntaNormalizada p) {
        return SUBPASOS_POR_NOMBRE.stream().filter(s -> p.coincide(s.enLaPregunta())).findFirst();
    }

    /**
     * ¿Este trozo de una pregunta compuesta ya lo contesta la guía? Ver
     * {@link PreguntaNormalizada#otrasPreguntas}.
     */
    boolean cubre(String trozo) {
        return puedeResponder(trozo) || PreguntaNormalizada.de(trozo).contieneAlguna(COMO_SE_REGISTRA);
    }

    /**
     * Compone la respuesta a partir del estado real de las etapas, sobre el
     * paso en curso o sobre el paso, sub-paso o documento que nombre la
     * pregunta.
     *
     * @return la respuesta, con el botón para abrir el sub-paso del que habla,
     *         o {@link Optional#empty()} si no hay etapas sobre las que decir
     *         nada — en cuyo caso quien llama debe seguir su camino normal en
     *         vez de inventar una respuesta.
     */
    public Optional<ChatResponse> responder(String pregunta, List<EtapaResponse> etapas) {
        if (etapas == null || etapas.isEmpty()) {
            return Optional.empty();
        }
        PreguntaNormalizada p = PreguntaNormalizada.de(pregunta);
        Optional<MatchResult> sub = p.buscar(SUBPASO);
        if (sub.isPresent()) {
            return Optional.of(sobreElSubpaso(sub.get().group(1) + "." + sub.get().group(2), etapas));
        }
        Optional<String> delDocumento = subpasoDelDocumento(p, etapas);
        if (delDocumento.isPresent()) {
            return Optional.of(sobreElSubpaso(delDocumento.get(), etapas));
        }
        // Los nombres solo cuentan si la pregunta entró por ellos: en «¿qué me
        // falta? ya tomé las fotos» la pregunta es la de siempre, no el 3.2.
        boolean porNombre = !p.contieneAlguna(SENALES) && !p.esExactamenteAlguna(PREGUNTAS_COMPLETAS);
        Optional<SubpasoPorNombre> nombrado = porNombre ? subpasoPorNombre(p) : Optional.empty();
        if (nombrado.isPresent()) {
            for (EtapaResponse etapa : etapas) {
                for (SubetapaResponse s : etapa.subEtapas() == null ? List.<SubetapaResponse>of() : etapa.subEtapas()) {
                    if (PreguntaNormalizada.normalizar(s.nombre()).contains(nombrado.get().enElNombreDelSubpaso())) {
                        return Optional.of(sobreElSubpaso(s.codigo(), etapas));
                    }
                }
            }
            // Un contrato sin ese sub-paso (los de antes del 15-10-2026): lo
            // que nombra la pregunta no está aquí, y contestar el paso en curso
            // sería contestar otra cosa.
            if (!p.coincide(PASO) && !p.coincide(DESPUES_DEL) && !p.coincide(PASO_POR_NOMBRE)) {
                return Optional.empty();
            }
        }
        Optional<EtapaResponse> enCurso = enCurso(etapas);
        OptionalInt paso = pasoNombrado(p, porNombre);
        if (paso.isPresent() && (enCurso.isEmpty() || enCurso.get().numero() != paso.getAsInt())) {
            return Optional.of(sobreOtroPaso(paso.getAsInt(), etapas, enCurso));
        }
        return delPasoActual(etapas, enCurso);
    }

    /** La respuesta sobre el paso en curso, como texto. */
    public Optional<String> responder(List<EtapaResponse> etapas) {
        if (etapas == null || etapas.isEmpty()) {
            return Optional.empty();
        }
        return delPasoActual(etapas, enCurso(etapas)).map(ChatResponse::respuesta);
    }

    private Optional<ChatResponse> delPasoActual(List<EtapaResponse> etapas, Optional<EtapaResponse> enCurso) {
        if (enCurso.isEmpty()) {
            return Optional.of(ChatResponse.delSistema("""
                    Ya están completados los %d pasos del contrato. No queda ningún sub-paso pendiente.

                    Si necesita revisar algo de un paso ya cerrado, dígame cuál y se lo consulto."""
                    .formatted(etapas.size())));
        }

        EtapaResponse etapa = enCurso.get();
        List<SubetapaResponse> pendientes = pendientes(etapa);

        StringBuilder sb = new StringBuilder();
        sb.append("Está en el paso %d: %s".formatted(etapa.numero(), etapa.nombre()));
        if (etapa.porcentaje() > 0) {
            sb.append(" (%d%% completado)".formatted(etapa.porcentaje()));
        }
        sb.append(".\n\nLo que le falta aquí:\n");
        listar(sb, pendientes);

        SubetapaResponse siguiente = pendientes.get(0);
        sb.append("\n\nEmpiece por %s: %s.".formatted(siguiente.codigo(), siguiente.nombre()));
        detalle(sb, siguiente);
        sb.append("\n\n").append(comoSeRegistra(siguiente));
        sb.append("\n\nSi necesita detalle de alguno de estos sub-pasos —qué documento sirve de soporte, "
                + "de dónde sale un insumo— pregúnteme por él y se lo explico.");
        return Optional.of(ChatResponse.delSistema(sb.toString(),
                AccionesDelCopiloto.abrirSubpaso(etapa.numero(), siguiente)));
    }

    /** «¿Qué me falta en el paso 4?» con el contrato en el 2: los pendientes del 4, no los del 2. */
    private ChatResponse sobreOtroPaso(int numero, List<EtapaResponse> etapas, Optional<EtapaResponse> enCurso) {
        Optional<EtapaResponse> encontrada = etapas.stream().filter(e -> e.numero() == numero).findFirst();
        if (encontrada.isEmpty()) {
            return ChatResponse.delSistema("Este contrato no tiene registrado el paso %d.".formatted(numero));
        }
        EtapaResponse etapa = encontrada.get();
        List<SubetapaResponse> pendientes = pendientes(etapa);
        StringBuilder sb = new StringBuilder();
        ChatResponse.Accion accion;
        if (pendientes.isEmpty()) {
            sb.append("El paso %d: %s ya está completo: no le queda ningún sub-paso pendiente."
                    .formatted(etapa.numero(), etapa.nombre()));
            // Lo que tuvo, para quien pregunta en qué consistía («explícame la
            // designación»).
            if (etapa.subEtapas() != null && !etapa.subEtapas().isEmpty()) {
                sb.append("\n\nSus sub-pasos:\n");
                listar(sb, etapa.subEtapas());
            }
            accion = AccionesDelCopiloto.irAPaso(etapa.numero());
        } else {
            sb.append("Paso %d: %s".formatted(etapa.numero(), etapa.nombre()));
            if (etapa.porcentaje() > 0) {
                sb.append(" (%d%% completado)".formatted(etapa.porcentaje()));
            }
            sb.append(".\n\nLo que le falta ahí:\n");
            listar(sb, pendientes);
            SubetapaResponse primero = pendientes.get(0);
            sb.append("\n\nEl primero es %s: %s.".formatted(primero.codigo(), primero.nombre()));
            detalle(sb, primero);
            sb.append("\n\n").append(comoSeRegistra(primero));
            accion = AccionesDelCopiloto.abrirSubpaso(etapa.numero(), primero);
        }
        enCurso.ifPresent(e -> sb.append("\n\nUsted va en el paso %d: %s.".formatted(e.numero(), e.nombre())));
        return ChatResponse.delSistema(sb.toString(), accion);
    }

    /**
     * «¿Qué debo hacer en el 4.2?» o «¿ya puedo firmar el acta de inicio?»: el
     * estado de ese sub-paso, lo que queda antes de él en su paso y qué botón
     * pulsar. No se dice «todavía no puede»: SICOT no obliga a seguir el
     * orden, y afirmarlo sería inventar una regla.
     */
    private ChatResponse sobreElSubpaso(String codigo, List<EtapaResponse> etapas) {
        for (EtapaResponse etapa : etapas) {
            List<SubetapaResponse> subs = etapa.subEtapas() == null ? List.of() : etapa.subEtapas();
            for (int i = 0; i < subs.size(); i++) {
                SubetapaResponse sub = subs.get(i);
                if (codigo.equals(sub.codigo())) {
                    return sobreElSubpaso(etapa, sub, subs.subList(0, i), enCurso(etapas));
                }
            }
        }
        return ChatResponse.delSistema("Este contrato no tiene un sub-paso %s.".formatted(codigo));
    }

    private ChatResponse sobreElSubpaso(EtapaResponse etapa, SubetapaResponse sub, List<SubetapaResponse> anteriores,
                                        Optional<EtapaResponse> enCurso) {
        ChatResponse.Accion accion = AccionesDelCopiloto.abrirSubpaso(etapa.numero(), sub);
        StringBuilder sb = new StringBuilder("Sub-paso %s: %s, del paso %d: %s."
                .formatted(sub.codigo(), sub.nombre(), etapa.numero(), etapa.nombre()));
        if (sub.estado() == EstadoSubetapa.COMPLETADA) {
            // Con lo que se hace en él: «explícame el 2.1» con el 2.1 cerrado
            // pide la explicación igual, y hasta el 20-10-2026 solo recibía
            // «Ya está completado».
            sb.append(" Ya está completado.");
            detalle(sb, sub);
            return ChatResponse.delSistema(sb.toString(), accion);
        }
        sb.append(sub.estado() == EstadoSubetapa.EN_CURSO ? " Está en curso." : " Está pendiente.");
        detalle(sb, sub);
        List<SubetapaResponse> pendientesAntes = anteriores.stream()
                .filter(s -> s.estado() != EstadoSubetapa.COMPLETADA)
                .toList();
        if (pendientesAntes.isEmpty()) {
            sb.append("\n\nNo le queda nada pendiente antes de él en ese paso.");
        } else {
            sb.append(pendientesAntes.size() == 1
                    ? "\n\nAntes de él, en ese mismo paso, sigue pendiente:\n"
                    : "\n\nAntes de él, en ese mismo paso, siguen pendientes:\n");
            listar(sb, pendientesAntes);
        }
        sb.append("\n\n").append(comoSeRegistra(sub));
        enCurso.filter(e -> e.numero() < etapa.numero())
                .ifPresent(e -> sb.append("\n\nUsted va en el paso %d: %s.".formatted(e.numero(), e.nombre())));
        return ChatResponse.delSistema(sb.toString(), accion);
    }

    /**
     * El sub-paso del documento que nombra la pregunta. «El acta» o «el
     * informe» a secas se toman por el primero de los dos que no esté
     * completado: es el que le queda por delante, y la respuesta dice su
     * nombre, así que no hay confusión posible.
     */
    private static Optional<String> subpasoDelDocumento(PreguntaNormalizada p, List<EtapaResponse> etapas) {
        List<FichaDeDocumentoFormal.Documento> nombrados = FichaDeDocumentoFormal.nombrados(p);
        Optional<String> nombrado = nombrados.stream()
                .map(d -> d.subpaso() != null ? d.subpaso()
                        : FichaDeDocumentoFormal.dondeSeCarga(d, etapas).map(u -> u.sub().codigo()).orElse(null))
                .filter(Objects::nonNull)
                .findFirst();
        if (nombrado.isPresent()) {
            return nombrado;
        }
        List<FichaDeDocumentoFormal.Documento> candidatos = FichaDeDocumentoFormal.candidatosDeUnNombreAbreviado(p);
        if (candidatos.isEmpty()) {
            return Optional.empty();
        }
        return candidatos.stream()
                .map(d -> d.subpaso() != null ? d.subpaso()
                        : FichaDeDocumentoFormal.dondeSeCarga(d, etapas).map(u -> u.sub().codigo()).orElse(null))
                .filter(Objects::nonNull)
                .filter(codigo -> etapas.stream()
                        .flatMap(e -> e.subEtapas() == null ? Stream.empty() : e.subEtapas().stream())
                        .noneMatch(s -> codigo.equals(s.codigo()) && s.estado() == EstadoSubetapa.COMPLETADA))
                .findFirst()
                .or(() -> Optional.ofNullable(candidatos.get(candidatos.size() - 1).subpaso()));
    }

    private static OptionalInt pasoNombrado(PreguntaNormalizada p, boolean porNombre) {
        Optional<MatchResult> despues = p.buscar(DESPUES_DEL);
        if (despues.isPresent()) {
            int anterior = Integer.parseInt(despues.get().group(1));
            return anterior < 6 ? OptionalInt.of(anterior + 1) : OptionalInt.empty();
        }
        // PASO solo deja pasar 1-6 o su nombre, así que parseInt no falla.
        Optional<Integer> porNumero = p.buscar(PASO)
                .map(m -> NUMEROS.containsKey(m.group(1)) ? NUMEROS.get(m.group(1)) : Integer.parseInt(m.group(1)));
        if (porNumero.isPresent()) {
            return OptionalInt.of(porNumero.get());
        }
        return (porNombre ? p.buscar(PASO_POR_NOMBRE) : Optional.<MatchResult>empty())
                .map(m -> OptionalInt.of(PASOS_POR_NOMBRE.get(m.group(1))))
                .orElse(OptionalInt.empty());
    }

    private static Optional<EtapaResponse> enCurso(List<EtapaResponse> etapas) {
        return etapas.stream().filter(GuiaDelPasoActual::tieneSubpasosPendientes).findFirst();
    }

    private static List<SubetapaResponse> pendientes(EtapaResponse etapa) {
        return etapa.subEtapas() == null ? List.of() : etapa.subEtapas().stream()
                .filter(s -> s.estado() != EstadoSubetapa.COMPLETADA)
                .toList();
    }

    private static void listar(StringBuilder sb, List<SubetapaResponse> subs) {
        for (SubetapaResponse s : subs) {
            sb.append("\n%s %s".formatted(s.codigo(), s.nombre()));
            if (s.estado() == EstadoSubetapa.EN_CURSO) {
                sb.append("  ← en curso");
            }
        }
    }

    /**
     * La descripción y el responsable salen de la plantilla GCCON-P-010: es lo
     * que el supervisor necesita para saber qué hacer, sin modelo.
     */
    private static void detalle(StringBuilder sb, SubetapaResponse sub) {
        if (sub.descripcion() != null && !sub.descripcion().isBlank()) {
            sb.append(" ").append(sub.descripcion().strip());
        }
        if (sub.responsable() != null && !sub.responsable().isBlank()) {
            sb.append(" Responsable: %s.".formatted(sub.responsable().strip()));
        }
    }

    /**
     * Qué botón pulsar en SICOT para ese sub-paso. Es la misma regla que
     * {@code guiaDelSubPaso} en {@code frontend/src/data/guiaSubPaso.ts}: si la
     * guía del tutorial y esta dijeran cosas distintas del mismo sub-paso, el
     * supervisor no sabría a cuál creer. El flujo de la firma es el de
     * {@link FlujoDeFirma}, el mismo que cuentan la ficha y el prompt.
     */
    static String comoSeRegistra(SubetapaResponse sub) {
        Optional<FichaDeDocumentoFormal.Documento> documento = FichaDeDocumentoFormal.delSubpaso(sub.codigo());
        if (documento.isPresent()) {
            FichaDeDocumentoFormal.Documento d = documento.get();
            return "En SICOT: cuando tenga lo necesario, pulse «Firmar documento». SICOT arma «%s»%s con los datos "
                    .formatted(d.nombre(), d.codigo() == null ? "" : " (" + d.codigo() + ")")
                    + "exactos del contrato: " + FlujoDeFirma.CORTO;
        }
        // El paso del acta y las herramientas de 2.2 a 2.4 del proceso real,
        // con el texto de guiaSubPaso.ts. Las herramientas se reconocen por el
        // nombre, igual que HERRAMIENTA_POR_PASO: los contratos de antes del
        // 15-10-2026 tienen otros sub-pasos con esos mismos códigos.
        if (FichaDeDocumentoFormal.ACTA_FIRMADA_Y_ENVIO.equals(sub.documentosRequeridos())) {
            return "En SICOT: cuando haya firmado el acta y la haya devuelto respondiendo el correo de Contratación, "
                    + "cargue en este sub-paso el acta firmada (PDF) y la evidencia del envío (el pantallazo o el PDF "
                    + "del correo que respondió). Cuando las dos aparezcan como cargadas, márquelo como completado: "
                    + "SICOT no lo deja cerrar sin ellas.";
        }
        String nombre = sub.nombre() == null ? "" : sub.nombre().strip();
        if (nombre.equals("Verificación de especificaciones en SECOP II")) {
            return "En SICOT: debajo de este sub-paso verá lo que el SECOP II publica del contrato y lo que no "
                    + "coincide con lo registrado en SICOT. Las cantidades y las especificaciones no vienen en esos "
                    + "datos: abra el proceso en el SECOP II con el enlace del sub-paso y revíselas en la oferta "
                    + "económica y el estudio previo del expediente; cuando esté todo revisado, márquelo como "
                    + "completado.";
        }
        if (nombre.equals("Aviso a los profesionales SST y ambiental")) {
            return "En SICOT: abra el borrador del correo, agregue como destinatarios a los profesionales de SST y "
                    + "ambiental —sus correos están en el estudio previo, que se abre entrando al proceso en el SECOP "
                    + "II— y envíelo desde su correo. Después, márquelo como completado.";
        }
        if (nombre.equals("Contacto con el contratista y acuerdo de entregas")) {
            return "En SICOT: si es un suministro, cargue abajo la solicitud de pedido —se pega de la oferta "
                    + "económica— y descargue el Excel; después abra el borrador del correo al contratista, "
                    + "adjúntelo y envíelo desde su correo. Cuando hayan acordado las fechas de entrega, márquelo "
                    + "como completado.";
        }
        if (SUBPASOS_CON_EVIDENCIA_FOTOGRAFICA.contains(sub.codigo())) {
            return "En SICOT: tome la foto con «Tomar foto de la entrega» o elija una con «Elegir una foto», "
                    + "pulse «Cargar evidencia» y, cuando aparezca como cargada, marque el sub-paso como completado.";
        }
        String responsable = sub.responsable() == null ? "" : sub.responsable().strip();
        if (!responsable.isEmpty() && !responsable.toLowerCase(Locale.ROOT).contains("supervisor")) {
            return "En SICOT: este sub-paso lo realiza %s; márquelo como completado cuando le confirmen que está hecho."
                    .formatted(responsable);
        }
        return "En SICOT: cuando lo haya hecho, márquelo como completado.";
    }

    private static boolean tieneSubpasosPendientes(EtapaResponse etapa) {
        return etapa.subEtapas() != null && etapa.subEtapas().stream()
                .anyMatch(s -> s.estado() != EstadoSubetapa.COMPLETADA);
    }
}
