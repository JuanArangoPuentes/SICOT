package co.sena.sicot.ia;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * La pregunta del supervisor reducida a lo que importa para encaminarla: sin
 * mayúsculas, sin tildes y sin signos.
 *
 * <h2>Por qué hace falta</h2>
 * Los atajos sin modelo ({@link GuiaDelPasoActual}, {@link FichaDeDocumentoFormal})
 * reconocen la pregunta por frases. Hasta el 1-10-2026 se comparaban solo en
 * minúsculas, y eso dejaba fuera formas reales de escribir lo mismo:
 * <ul>
 *   <li>una tilde de más o de menos obligaba a duplicar cada frase en la lista
 *       («en que paso» y «en qué paso»), y la que se olvidaba iba al modelo;</li>
 *   <li>un teclado que manda la tilde como carácter aparte (Unicode NFD) no
 *       coincidía con ninguna de las dos;</li>
 *   <li>«GCCON-F-031», «GCCON F031» y «gccon-f-031» eran tres textos
 *       distintos;</li>
 *   <li>«¿qué falta?» coincidía dentro de «porque faltan», porque se buscaba la
 *       subcadena sin mirar dónde empezaba la palabra.</li>
 * </ul>
 * Cada pregunta que se escapa del atajo cuesta minutos de CPU en un portátil
 * sin tarjeta gráfica, y en el caso del paso actual, una respuesta que los
 * modelos pequeños dan mal (ADR-006). Normalizar las dos partes —la pregunta y
 * las frases— con la misma función cierra esas grietas de una vez.
 */
final class PreguntaNormalizada {

    private static final Pattern MARCAS_DIACRITICAS = Pattern.compile("\\p{M}+");
    private static final Pattern NO_ALFANUMERICO = Pattern.compile("[^a-z0-9]+");

    /**
     * Cómo se escriben en el teléfono las palabras que importan para encaminar
     * la pregunta. Se sustituyen solo como palabra entera y después de quitar
     * signos, así que la «q» de «gccon» o de «que» no se toca.
     *
     * <p>2-10-2026: «en q paso voy», «q sigue» o «k me falta» no coincidían con
     * ninguna frase y se iban al modelo, hasta ~158 s en CPU para una respuesta
     * que los modelos pequeños dan mal (ADR-006). Escritas con todas las letras
     * se contestaban al instante. La lista es corta y fija a propósito: cada
     * entrada se puede leer y discutir en una revisión.
     */
    private static final Map<String, String> ABREVIATURAS = Map.ofEntries(
            Map.entry("q", "que"), Map.entry("k", "que"), Map.entry("ke", "que"), Map.entry("qe", "que"),
            Map.entry("xq", "porque"), Map.entry("pq", "porque"), Map.entry("xk", "porque"),
            Map.entry("porq", "porque"),
            Map.entry("pa", "para"),
            Map.entry("tb", "tambien"), Map.entry("tmb", "tambien"),
            Map.entry("dnd", "donde"),
            Map.entry("cdo", "cuando"), Map.entry("qdo", "cuando"));

    /**
     * El «sí» que afirma, que no es el «si» condicional: con tilde, o «si» al
     * principio seguido de una coma, de un punto o de «y» («si, y ahora qué»).
     *
     * <p>Al quitar las tildes los dos quedaban como «si», y los atajos, que
     * descartan las condicionales, mandaban «sí, ¿y ahora qué?» al modelo. Se
     * quita antes de normalizar porque es lo único que distingue a los dos: la
     * tilde y la puntuación. «Si» sin tilde en mitad de la frase sigue contando
     * como condicional: ante la duda, la pregunta va al modelo.
     */
    private static final Pattern SI_AFIRMATIVO = Pattern.compile(
            "(?<![\\p{L}\\p{N}])sí(?![\\p{L}\\p{N}])"
                    + "|^[\\s¡¿]*si(?=\\s*[,.!;:]|\\s*$|\\s+y(?![\\p{L}\\p{N}]))",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    /**
     * Dónde empieza otra pregunta dentro del mismo mensaje: tras un signo de
     * interrogación, o en un «y» seguido de una palabra interrogativa
     * («¿qué es el acta de inicio y cuánto vale el contrato?»).
     */
    private static final Pattern SEPARADOR_DE_PREGUNTAS = Pattern.compile(
            "\\?|\\s+y\\s+(?=¿?\\s*(?:qué|que|cuál|cual|cuánt[oa]s?|cuant[oa]s?|cuándo|cuando|quién|quien|dónde|donde|"
                    + "cómo|como|por qué|por que|q|k)(?![\\p{L}\\p{N}]))",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    /** Cómo empieza una pregunta, sobre el texto ya normalizado. */
    private static final Pattern EMPIEZA_PREGUNTANDO = Pattern.compile(
            "^(?:y )?(?:(?:en|a|de|para|con|hasta|desde|por) )?"
                    + "(?:que|cual|cuales|cuanto|cuanta|cuantos|cuantas|cuando|quien|quienes|donde|como)(?: |$)");

    /** La pregunta normalizada, con un espacio a cada lado para buscar palabras enteras. */
    private final String texto;

    private PreguntaNormalizada(String texto) {
        this.texto = " " + texto + " ";
    }

    static PreguntaNormalizada de(String pregunta) {
        return new PreguntaNormalizada(normalizar(pregunta));
    }

    /**
     * «¿Qué es el GCCON-F-031?» → «que es el gccon f 031».
     *
     * <p>La «ñ» queda como «n»: es lo mismo a los dos lados de la comparación,
     * así que no confunde nada.
     */
    static String normalizar(String texto) {
        if (texto == null) {
            return "";
        }
        // NFC primero: un teclado que manda la tilde aparte escribe «si» más un
        // acento combinante, y SI_AFIRMATIVO busca la «í» compuesta.
        String sinAfirmacion = SI_AFIRMATIVO.matcher(Normalizer.normalize(texto, Normalizer.Form.NFC))
                .replaceAll(" ");
        String sinTildes = MARCAS_DIACRITICAS.matcher(Normalizer.normalize(sinAfirmacion, Normalizer.Form.NFD))
                .replaceAll("");
        String[] palabras = NO_ALFANUMERICO.matcher(sinTildes.toLowerCase(Locale.ROOT)).replaceAll(" ").strip()
                .split(" ");
        for (int i = 0; i < palabras.length; i++) {
            palabras[i] = ABREVIATURAS.getOrDefault(palabras[i], palabras[i]);
        }
        return String.join(" ", palabras);
    }

    /**
     * Las preguntas que trae el mensaje además de la primera, tal como las
     * escribió el supervisor: los trozos que empiezan con una palabra
     * interrogativa tras un «?» o tras un «y».
     *
     * <p>Sirve a los atajos para no contestar media pregunta en silencio: si
     * uno de estos trozos no es de los que el atajo contesta, hay que decirlo.
     * Los trozos que no empiezan preguntando («¿Tiene código oficial?», «Deme
     * el paso a paso») no se cuentan: suelen precisar la primera pregunta, no
     * abrir otra.
     */
    static List<String> otrasPreguntas(String pregunta) {
        if (pregunta == null) {
            return List.of();
        }
        String[] trozos = SEPARADOR_DE_PREGUNTAS.split(Normalizer.normalize(pregunta, Normalizer.Form.NFC));
        List<String> otras = new ArrayList<>();
        for (int i = 1; i < trozos.length; i++) {
            String limpio = trozos[i].replaceAll("^[\\s¿¡,;:.]+|[\\s,;:.!]+$", "");
            if (EMPIEZA_PREGUNTANDO.matcher(normalizar(limpio)).find()) {
                otras.add(limpio);
            }
        }
        return otras;
    }

    /** Las frases normalizadas igual que las preguntas, para escribirlas en castellano corriente. */
    static Set<String> normalizarTodas(String... frases) {
        LinkedHashSet<String> normalizadas = new LinkedHashSet<>();
        for (String frase : frases) {
            normalizadas.add(normalizar(frase));
        }
        return Collections.unmodifiableSet(normalizadas);
    }

    boolean estaVacia() {
        return texto.isBlank();
    }

    /** Largo de la pregunta ya normalizada, sin los espacios de los bordes. */
    int largo() {
        return texto.length() - 2;
    }

    /**
     * ¿Aparece la frase empezando en una palabra?
     *
     * <p>Solo se exige el borde de la izquierda, a propósito: así «qué me falta»
     * reconoce también «qué me faltan» y «me toca» reconoce «me tocaba», pero
     * «qué falta» ya no se encuentra dentro de «porque faltan».
     */
    boolean contiene(String fraseNormalizada) {
        return texto.contains(" " + fraseNormalizada);
    }

    boolean contieneAlguna(Set<String> frasesNormalizadas) {
        return frasesNormalizadas.stream().anyMatch(this::contiene);
    }

    /**
     * ¿Aparece la frase entera, con borde de palabra a los dos lados?
     *
     * <p>Para las frases cortas que no deben encontrarse dentro de otra
     * palabra: con {@link #contiene}, «qué es» casaba con «qué escribo» y
     * «qué está», y la ficha fija de un documento contestaba preguntas
     * abiertas sobre su redacción.
     */
    boolean contieneFrase(String fraseNormalizada) {
        return texto.contains(" " + fraseNormalizada + " ");
    }

    boolean contieneAlgunaFrase(Set<String> frasesNormalizadas) {
        return frasesNormalizadas.stream().anyMatch(this::contieneFrase);
    }

    /** ¿La pregunta entera es una de estas frases, y nada más? */
    boolean esExactamenteAlguna(Set<String> frasesNormalizadas) {
        return frasesNormalizadas.contains(texto.strip());
    }

    /** ¿Aparece esta palabra suelta, entera por los dos lados? */
    boolean tienePalabra(String palabra) {
        return texto.contains(" " + palabra + " ");
    }

    /** Para patrones que necesitan más que una frase fija (los códigos de formato). */
    boolean coincide(Pattern patron) {
        return patron.matcher(texto).find();
    }

    /** Lo que encontró el patrón, para leer sus grupos (el número de un paso o de un sub-paso). */
    Optional<MatchResult> buscar(Pattern patron) {
        Matcher m = patron.matcher(texto);
        return m.find() ? Optional.of(m.toMatchResult()) : Optional.empty();
    }

    /** La pregunta sin lo que reconoce el patrón: sirve para ver qué más preguntó. */
    String sin(Pattern patron) {
        return patron.matcher(texto).replaceAll(" ");
    }

    @Override
    public String toString() {
        return texto.strip();
    }
}
