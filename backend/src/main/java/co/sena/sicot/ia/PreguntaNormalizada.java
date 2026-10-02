package co.sena.sicot.ia;

import java.text.Normalizer;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
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
        String sinTildes = MARCAS_DIACRITICAS.matcher(Normalizer.normalize(texto, Normalizer.Form.NFD))
                .replaceAll("");
        return NO_ALFANUMERICO.matcher(sinTildes.toLowerCase(Locale.ROOT)).replaceAll(" ").strip();
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

    /** La pregunta sin lo que reconoce el patrón: sirve para ver qué más preguntó. */
    String sin(Pattern patron) {
        return patron.matcher(texto).replaceAll(" ");
    }

    @Override
    public String toString() {
        return texto.strip();
    }
}
