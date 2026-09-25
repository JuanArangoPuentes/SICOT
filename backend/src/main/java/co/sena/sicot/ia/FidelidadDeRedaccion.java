package co.sena.sicot.ia;

import java.math.BigDecimal;
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
 *       trae un número —en cifras o en letras— que no aparece en las notas ni
 *       en los datos del contrato, el modelo lo inventó; entonces se usan las
 *       notas tal como las escribió el supervisor.</li>
 * </ol>
 */
public final class FidelidadDeRedaccion {

    /**
     * Un número tal como se escribe en Colombia: «120.450.000», «2,5», «71204».
     * La primera alternativa reconoce los miles con punto antes que un decimal.
     */
    private static final Pattern NUMERO = Pattern.compile("\\d{1,3}(?:\\.\\d{3})+(?:,\\d+)?|\\d+(?:[.,]\\d+)?");
    private static final Pattern PALABRA = Pattern.compile("[\\p{L}]+");
    private static final Pattern MILES = Pattern.compile("\\d{1,3}(?:\\.\\d{3})+(?:,\\d+)?");

    /**
     * Números escritos en letras. «un», «una» y «uno» quedan fuera: son sobre
     * todo artículos, y contarlos haría descartar casi cualquier redacción.
     */
    private static final Set<String> NUMEROS_EN_LETRAS = Set.of(
            "dos", "tres", "cuatro", "cinco", "seis", "siete", "ocho", "nueve", "diez", "once", "doce", "trece",
            "catorce", "quince", "dieciseis", "diecisiete", "dieciocho", "diecinueve", "veinte", "treinta",
            "cuarenta", "cincuenta", "sesenta", "setenta", "ochenta", "noventa", "cien", "ciento", "doscientos",
            "trescientos", "cuatrocientos", "quinientos", "seiscientos", "setecientos", "ochocientos",
            "novecientos", "mil", "millon", "millones", "billon", "billones");

    private FidelidadDeRedaccion() {
    }

    /**
     * Sustituye cada aparición aproximada de un nombre conocido por su forma
     * exacta.
     *
     * <p>Aproximada quiere decir: la misma cantidad de palabras, las mismas
     * iniciales en cada palabra y, sin tildes ni mayúsculas, una distancia de
     * edición de 1 cada 8 letras como máximo. Solo para nombres de tres
     * palabras o más, y nunca sobre un texto que ya es, exactamente, alguno de
     * los nombres conocidos. Con nombres de dos palabras la corrección cambiaba
     * a otras personas («Andrea Ospina» por el supervisor «Andrés Ospina») o
     * palabras comunes («una mesa» por «Ana Mesa»), y con dos nombres parecidos
     * la segunda pasada «corregía» el que ya estaba bien (revisión del
     * 24-09-2026). Un nombre de dos palabras mal escrito es un riesgo menor que
     * atribuirle a alguien lo que hizo otra persona.
     */
    public static String corregirNombres(String texto, List<String> nombres) {
        Set<String> exactos = new HashSet<>();
        for (String n : nombres) {
            if (n != null && !n.isBlank()) {
                exactos.add(normalizar(String.join(" ", n.trim().split("\\s+"))));
            }
        }
        String resultado = texto;
        for (String nombre : nombres) {
            if (nombre == null || nombre.isBlank()) {
                continue;
            }
            String[] partesNombre = nombre.trim().split("\\s+");
            if (partesNombre.length < 3) {
                continue;
            }
            String objetivo = normalizar(String.join(" ", partesNombre));
            String[] palabrasObjetivo = objetivo.split(" ");
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
                String[] ventana = new String[partesNombre.length];
                for (int k = 0; k < partesNombre.length; k++) {
                    int[] w = palabras.get(i + k);
                    ventana[k] = normalizar(resultado.substring(w[0], w[1]));
                }
                String ventanaNorm = String.join(" ", ventana);
                if (!exactos.contains(ventanaNorm) && mismasIniciales(ventana, palabrasObjetivo)) {
                    int d = distancia(ventanaNorm, objetivo);
                    if (d > 0 && d <= tolerancia) {
                        sb.append(resultado, cursor, ini).append(nombre.trim());
                        cursor = fin;
                        i += partesNombre.length;
                        continue;
                    }
                }
                i++;
            }
            sb.append(resultado.substring(cursor));
            resultado = sb.toString();
        }
        return resultado;
    }

    private static boolean mismasIniciales(String[] a, String[] b) {
        if (a.length != b.length) {
            return false;
        }
        for (int k = 0; k < a.length; k++) {
            if (a[k].isEmpty() || b[k].isEmpty() || a[k].charAt(0) != b[k].charAt(0)) {
                return false;
            }
        }
        return true;
    }

    /**
     * ¿Cada número del texto redactado está en las notas o en los datos del
     * contrato? En cifras se comparan por su valor, así que «120.450.000» y
     * «120450000» son el mismo número pero «2,5» y «25» no. En letras, cada
     * palabra numérica («veinticinco», «millones») tiene que estar en las
     * notas: el modelo convirtió el valor del contrato en letras equivocadas
     * el 24-09-2026, y un número inventado en letras pasaba por no tener
     * dígitos.
     */
    public static boolean sinCifrasInventadas(String redactado, String notas, List<String> datosConocidos) {
        Set<String> conocidas = new HashSet<>(cifras(notas));
        Set<String> palabrasConocidas = new HashSet<>(palabrasNumericas(notas));
        for (String dato : datosConocidos) {
            conocidas.addAll(cifras(dato));
            palabrasConocidas.addAll(palabrasNumericas(dato));
        }
        for (String cifra : cifras(redactado)) {
            if (!conocidas.contains(cifra)) {
                return false;
            }
        }
        for (String palabra : palabrasNumericas(redactado)) {
            if (!palabrasConocidas.contains(palabra)) {
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
            r.add(valorCanonico(m.group()));
        }
        return r;
    }

    /** «120.450.000» → «120450000»; «2,5» → «2.5»; «07» → «7». */
    static String valorCanonico(String numero) {
        String n = MILES.matcher(numero).matches()
                ? numero.replace(".", "").replace(',', '.')
                : numero.replace(',', '.');
        try {
            return new BigDecimal(n).stripTrailingZeros().toPlainString();
        } catch (NumberFormatException e) {
            return n;
        }
    }

    private static List<String> palabrasNumericas(String texto) {
        List<String> r = new ArrayList<>();
        if (texto == null) {
            return r;
        }
        for (String p : normalizar(texto).split("[^a-z]+")) {
            if (NUMEROS_EN_LETRAS.contains(p) || (p.startsWith("veinti") && p.length() > 6)) {
                r.add(p);
            }
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
