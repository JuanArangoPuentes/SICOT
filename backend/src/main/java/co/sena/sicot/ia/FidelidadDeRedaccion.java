package co.sena.sicot.ia;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Comprueba y corrige lo que el modelo redacta a partir de las notas del
 * supervisor, antes de que entre en un documento que se va a firmar.
 *
 * <p>Dos defensas, las dos deterministas porque no se puede confiar en que el
 * modelo obedezca la instrucción de no inventar (la prueba del 24-09-2026 lo
 * mostró desobedeciéndola en los cinco documentos):
 * <ol>
 *   <li><b>Nombres casi iguales se corrigen.</b> «Marta Lucía Osipina Gómez»
 *       frente a «MARTA LUCÍA OSPINA GÓMEZ» es el mismo nombre con una letra
 *       cambiada: se sustituye por el nombre exacto. Un nombre alterado en un
 *       acta es peor que no nombrarlo.</li>
 *   <li><b>Una redacción con cifras que no estaban se descarta.</b> Si el texto
 *       trae un número que no aparece en las notas ni en los datos del
 *       contrato, el modelo lo inventó; entonces se usan las notas tal como las
 *       escribió el supervisor.</li>
 * </ol>
 */
public final class FidelidadDeRedaccion {

    private static final Pattern NUMERO = Pattern.compile("\\d[\\d.,]*\\d|\\d");
    private static final Pattern PALABRA = Pattern.compile("[\\p{L}]+");

    private FidelidadDeRedaccion() {
    }

    /**
     * Sustituye cada aparición aproximada de un nombre conocido por su forma
     * exacta. Aproximada = misma cantidad de palabras y, sin tildes ni
     * mayúsculas, a una distancia de edición de 1 cada 8 letras como máximo
     * (y al menos 1). Lo idéntico no se toca.
     */
    public static String corregirNombres(String texto, List<String> nombres) {
        String resultado = texto;
        for (String nombre : nombres) {
            if (nombre == null || nombre.isBlank()) {
                continue;
            }
            String[] partesNombre = nombre.trim().split("\\s+");
            if (partesNombre.length < 2) {
                continue; // una sola palabra da demasiados falsos positivos
            }
            String objetivo = normalizar(String.join(" ", partesNombre));
            int tolerancia = Math.max(1, objetivo.length() / 8);

            List<int[]> palabras = new ArrayList<>();
            Matcher m = PALABRA.matcher(resultado);
            while (m.find()) {
                palabras.add(new int[]{m.start(), m.end()});
            }
            StringBuilder sb = new StringBuilder();
            int cursor = 0;
            int i = 0;
            while (i + partesNombre.length <= palabras.size()) {
                int ini = palabras.get(i)[0];
                int fin = palabras.get(i + partesNombre.length - 1)[1];
                String ventana = resultado.substring(ini, fin);
                String ventanaNorm = normalizar(String.join(" ", ventana.split("[^\\p{L}]+")));
                int d = distancia(ventanaNorm, objetivo);
                if (d > 0 && d <= tolerancia) {
                    sb.append(resultado, cursor, ini).append(nombre.trim());
                    cursor = fin;
                    i += partesNombre.length;
                } else {
                    i++;
                }
            }
            sb.append(resultado.substring(cursor));
            resultado = sb.toString();
        }
        return resultado;
    }

    /**
     * ¿Cada cifra del texto redactado está en las notas o en los datos del
     * contrato? Se comparan solo los dígitos, para que «120.450.000» y
     * «120450000» cuenten como la misma cifra.
     */
    public static boolean sinCifrasInventadas(String redactado, String notas, List<String> datosConocidos) {
        Set<String> conocidas = new HashSet<>(cifras(notas));
        for (String dato : datosConocidos) {
            conocidas.addAll(cifras(dato));
        }
        for (String cifra : cifras(redactado)) {
            if (!conocidas.contains(cifra)) {
                return false;
            }
        }
        return true;
    }

    private static List<String> cifras(String texto) {
        List<String> r = new ArrayList<>();
        if (texto == null) {
            return r;
        }
        Matcher m = NUMERO.matcher(texto);
        while (m.find()) {
            r.add(m.group().replaceAll("\\D", ""));
        }
        return r;
    }

    static String normalizar(String s) {
        String sinTildes = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return sinTildes.toLowerCase(Locale.ROOT).trim();
    }

    static int distancia(String a, String b) {
        int[] previa = new int[b.length() + 1];
        int[] actual = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) previa[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            actual[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int costo = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                actual[j] = Math.min(Math.min(actual[j - 1] + 1, previa[j] + 1), previa[j - 1] + costo);
            }
            int[] t = previa;
            previa = actual;
            actual = t;
        }
        return previa[b.length()];
    }
}
