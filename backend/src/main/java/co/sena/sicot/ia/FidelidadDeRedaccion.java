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
 *   <li><b>Una redacción que pierde una cifra o cambia una palabra también se
 *       descarta</b> (desde el 29-09-2026, cuando «5 camas» salió como «las
 *       cunas»).</li>
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
        // «treinta pupitres» por «30 pupitres» no inventa nada: es la misma
        // cifra en letras, y descartarla dejaba fuera redacciones fieles.
        for (String cifra : cifras(notas)) {
            palabrasConocidas.addAll(enLetras(cifra));
        }
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

    /**
     * Un número de sub-paso del procedimiento («3.1», «4.2»). La revisión del
     * paso le pide al supervisor contar qué hizo «en cada uno de estos
     * puntos», así que sus notas suelen empezar cada frase con uno; la
     * redacción formal los quita con razón, y no son una cifra que se pierda.
     */
    private static final Pattern SUB_PASO = Pattern.compile("[1-6]\\.[1-9]");

    /** El número de un punto de una lista: «1. revisé…», «2) …». */
    private static final Pattern ENUMERADOR = Pattern.compile("(?:^|[\\s;:,])(\\d{1,2})[.)](?=\\s)");

    /**
     * Un ordinal abreviado: «1ra», «2do», «3er», «4.º». El sufijo va pegado y
     * el número no puede ser la cola de otro: «71204 va» no es un «4.º».
     */
    private static final Pattern ORDINAL = Pattern.compile(
            "(?<![\\d.,])(\\d{1,2})(?:ro|ra|do|da|er|to|ta|vo|va|no|na|mo|ma|\\.?[°ºª])(?![\\p{L}\\d])",
            Pattern.CASE_INSENSITIVE);

    /** Una fecha en cifras: «15/09/2026», «29-09-2026». */
    private static final Pattern FECHA = Pattern.compile("\\b(\\d{1,2})[/-](\\d{1,2})[/-](\\d{4})\\b");

    private static final String[] ORDINALES = {"", "primer", "segund", "tercer", "cuart", "quint", "sext",
            "septim", "octav", "noven", "decim"};

    private static final String[] MESES = {"", "enero", "febrero", "marzo", "abril", "mayo", "junio", "julio",
            "agosto", "septiembre", "octubre", "noviembre", "diciembre"};

    /**
     * ¿Cada cifra de las notas sigue en la redacción? El 29-09-2026 el
     * supervisor escribió «la entrega de 5 camas» y el modelo redactó «la
     * recepción de las cunas»: sin la cantidad, el acta ya no dice lo que el
     * supervisor verificó. Una cantidad o una fecha que se pierde es tan grave
     * como una que se inventa.
     *
     * <p>Una cifra se da por conservada si la redacción la trae igual o escrita
     * en letras («treinta» por «30»). No se exige lo que la redacción quita con
     * razón, porque exigirlo descartaba redacciones fieles (revisión del
     * 29-09-2026, con el modelo real: 5 de 20):
     * <ul>
     *   <li>los números de sub-paso y de los puntos de una lista;</li>
     *   <li>los datos del contrato, que el prompt le pide no repetir;</li>
     *   <li>una fecha pasada a forma larga («15 de septiembre de 2026»), si
     *       siguen el día, el mes y el año;</li>
     *   <li>un ordinal escrito en letras («segundo piso» por «2do piso»).</li>
     * </ul>
     */
    public static boolean conservaLasCifras(String redactado, String notas, List<String> datosConocidos) {
        if (notas == null || notas.isBlank()) {
            return true;
        }
        String textoRedactado = normalizar(redactado);
        Set<String> enRedaccion = new HashSet<>(cifras(redactado));
        Set<String> palabrasRedaccion = new HashSet<>(List.of(textoRedactado.split("[^a-z]+")));
        Set<String> delContrato = new HashSet<>();
        for (String dato : datosConocidos) {
            delContrato.addAll(cifras(dato));
        }
        // Lo que se revisa aparte (fechas y ordinales) o no se exige
        // (enumeradores) se tapa, para que su número no se cuente dos veces.
        StringBuilder resto = new StringBuilder(notas);
        Matcher fecha = FECHA.matcher(notas);
        while (fecha.find()) {
            int dia = Integer.parseInt(fecha.group(1));
            int mes = Integer.parseInt(fecha.group(2));
            String anio = fecha.group(3);
            boolean igual = redactado != null && (redactado.contains(fecha.group())
                    || redactado.contains(fecha.group().replace('-', '/'))
                    || redactado.contains(fecha.group().replace('/', '-')));
            boolean larga = mes >= 1 && mes <= 12 && enRedaccion.contains(String.valueOf(dia))
                    && enRedaccion.contains(anio) && palabrasRedaccion.contains(MESES[mes]);
            if (!igual && !larga) {
                return false;
            }
            tapar(resto, fecha.start(), fecha.end());
        }
        Matcher ordinal = ORDINAL.matcher(resto.toString());
        while (ordinal.find()) {
            int n = Integer.parseInt(ordinal.group(1));
            boolean enLetras = n >= 1 && n <= 10 && textoRedactado.contains(ORDINALES[n]);
            if (!enLetras && !enRedaccion.contains(String.valueOf(n))) {
                return false;
            }
            tapar(resto, ordinal.start(), ordinal.end());
        }
        Matcher enumerador = ENUMERADOR.matcher(resto.toString());
        while (enumerador.find()) {
            tapar(resto, enumerador.start(1), enumerador.end(1));
        }
        Matcher m = NUMERO.matcher(resto.toString());
        while (m.find()) {
            String cifra = m.group();
            String canonica = valorCanonico(cifra);
            if (SUB_PASO.matcher(cifra).matches() || delContrato.contains(canonica) || enRedaccion.contains(canonica)
                    || enLetrasEnLaRedaccion(canonica, palabrasRedaccion)) {
                continue;
            }
            return false;
        }
        return true;
    }

    private static void tapar(StringBuilder texto, int desde, int hasta) {
        for (int i = desde; i < hasta; i++) {
            texto.setCharAt(i, ' ');
        }
    }

    /** ¿Está el entero escrito en letras en la redacción? («30» → «treinta»). */
    private static boolean enLetrasEnLaRedaccion(String canonica, Set<String> palabrasRedaccion) {
        List<String> palabras = enLetras(canonica);
        return !palabras.isEmpty() && palabrasRedaccion.containsAll(palabras);
    }

    /** Las palabras de un entero de 2 en adelante («4500000» → cuatro, millones, quinientos, mil). */
    private static List<String> enLetras(String canonica) {
        try {
            long n = Long.parseLong(canonica);
            if (n < 2 || n > 999_999_999_999L) {
                return List.of();
            }
            List<String> r = new ArrayList<>();
            for (String p : normalizar(NumeroEnLetras.entero(n)).split("[^a-z]+")) {
                if (!p.isEmpty() && !p.equals("y") && !p.equals("un") && !p.equals("uno") && !p.equals("de")) {
                    r.add(p);
                }
            }
            return r;
        } catch (NumberFormatException e) {
            return List.of();
        }
    }

    /**
     * Palabras que el modelo introduce al pasar a registro formal y que
     * quedan a una o dos letras de otras de las notas: «en buen estado» por
     * «llegó bien», «para» cerca de «pero», «ha sido» cerca de «sede». No son
     * alteraciones, y contarlas descartaba redacciones fieles.
     */
    private static final Set<String> DEL_REGISTRO_FORMAL = Set.of("buen", "buena", "bien", "para", "pero", "sido",
            "sera", "esta", "este", "esto", "estos", "estas", "todo", "toda", "todos", "todas", "tuvo", "otra",
            "otro", "otros", "otras", "cada", "cual", "como", "cuando", "donde", "desde", "hasta", "sobre", "entre",
            "segun", "ante", "bajo", "mismo", "misma", "dicho", "dicha", "tanto", "tambien", "ademas", "aqui",
            "alli", "siendo", "hace", "hizo", "haber", "habia", "tener", "tiene", "tenia", "mas",
            "menos", "segunda", "segundo", "primera", "primero", "tercera", "tercero");

    /** Terminaciones de flexión: si solo cambia esto, es la redacción conjugando o concordando. */
    private static final Set<String> FLEXIONES = Set.of("", "a", "o", "e", "i", "as", "os", "es", "is", "an", "en",
            "on", "ar", "er", "ir", "ado", "ada", "ados", "adas", "ido", "ida", "idos", "idas", "aba", "aban", "ia",
            "ian", "ron", "ban", "aron", "ieron", "mos", "amos", "emos", "imos", "ando", "iendo", "ste", "aste",
            "iste", "n", "s", "r", "ra", "ro");

    /**
     * ¿Cambió el modelo una palabra de las notas por otra casi igual? Es el
     * otro error del 29-09-2026: «camas» se volvió «cunas». Cuenta como
     * alterada una palabra de la redacción que no está en las notas ni en los
     * datos del contrato, con la misma longitud e inicial que una palabra de
     * las notas que YA NO está en la redacción, y a dos letras o menos de ella.
     *
     * <p>No cuenta, porque es justo el trabajo de la redacción:
     * <ul>
     *   <li>cambiar la flexión: «entregó» por «entrega», «recibo» por «recibí»
     *       (solo si la raíz común tiene al menos tres letras: «cama» y «caja»
     *       comparten dos y sí es un cambio de objeto);</li>
     *   <li>corregir la ortografía: «rebisé» por «revisé», «resibí» por
     *       «recibí» (misma pronunciación);</li>
     *   <li>palabras del registro formal («buen», «para», «sido»).</li>
     * </ul>
     * Si la regla se equivoca, van las notas tal cual, que es lo seguro.
     */
    public static boolean sinPalabrasCambiadas(String redactado, String notas, List<String> datosConocidos) {
        Set<String> deNotas = palabrasLargas(notas);
        Set<String> enRedaccion = palabrasLargas(redactado);
        Set<String> conocidas = new HashSet<>(deNotas);
        for (String dato : datosConocidos) {
            conocidas.addAll(palabrasLargas(dato));
        }
        for (String w : enRedaccion) {
            if (conocidas.contains(w) || DEL_REGISTRO_FORMAL.contains(w)) {
                continue;
            }
            if (invierteElSentido(w, deNotas, enRedaccion)) {
                return false;
            }
            for (String v : deNotas) {
                if (enRedaccion.contains(v) || v.length() != w.length() || v.charAt(0) != w.charAt(0)
                        || distancia(v, w) > 2 || claveFonetica(v).equals(claveFonetica(w))
                        // Sobre la forma fonética: «rebisé» por «revisó» corrige
                        // la ortografía y conjuga a la vez.
                        || esFlexion(claveFonetica(v), claveFonetica(w))) {
                    continue;
                }
                return false;
            }
        }
        return true;
    }

    /**
     * Raíces de afirmaciones que comprometen al supervisor: plazos,
     * cumplimiento, calidad, conformidad, sanciones. En la prueba en vivo del
     * 29-09-2026, «entregó la 2da parte de los bienes» salió como «entregando
     * la segunda parte de los bienes en el término establecido»: el Informe
     * Final afirmaba que la entrega fue a tiempo sin que el supervisor lo
     * dijera. No tiene cifras ni palabras parecidas, así que nada lo veía.
     */
    private static final List<String> AFIRMACIONES = List.of("termino", "plazo", "tiempo", "oportun", "cabalidad",
            "satisfac", "conform", "cumpl", "especificac", "garantia", "multa", "sancion", "retras", "demora",
            "atras", "puntual");

    /**
     * ¿Afirma la redacción algo de plazos, cumplimiento, calidad o sanciones
     * que las notas no dicen? Si las notas usan la misma raíz («cumple» y
     * «cumplió», «retraso» y «retrasos»), no es una afirmación nueva.
     */
    public static boolean sinAfirmacionesAgregadas(String redactado, String notas) {
        Set<String> deNotas = palabrasLargas(notas);
        for (String w : palabrasLargas(redactado)) {
            for (String raiz : AFIRMACIONES) {
                if (w.startsWith(raiz) && deNotas.stream().noneMatch(v -> v.startsWith(raiz))) {
                    return false;
                }
            }
        }
        return true;
    }

    /** Prefijos que niegan: «incumplió», «imposible», «desorden», «disconforme». */
    private static final List<String> NEGACIONES = List.of("des", "dis", "in", "im");

    /**
     * ¿Dice la redacción lo contrario que las notas? «El contratista cumplió»
     * redactado como «incumplió» es una palabra más larga, así que la regla de
     * palabras parecidas no la veía (revisión del 29-09-2026). Se mira en los
     * dos sentidos: que la redacción niegue una palabra de las notas, o que
     * quite la negación que las notas tenían.
     */
    private static boolean invierteElSentido(String w, Set<String> deNotas, Set<String> enRedaccion) {
        for (String prefijo : NEGACIONES) {
            if (w.startsWith(prefijo) && w.length() - prefijo.length() >= 4) {
                String sinPrefijo = w.substring(prefijo.length());
                for (String v : deNotas) {
                    if (mismaPalabra(v, sinPrefijo)) {
                        return true;
                    }
                }
            }
            for (String v : deNotas) {
                if (v.startsWith(prefijo) && v.length() - prefijo.length() >= 4 && !enRedaccion.contains(v)
                        && mismaPalabra(v.substring(prefijo.length()), w)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean mismaPalabra(String a, String b) {
        return a.equals(b) || esFlexion(claveFonetica(a), claveFonetica(b));
    }

    private static boolean esFlexion(String v, String w) {
        int comun = 0;
        while (comun < Math.min(v.length(), w.length()) && v.charAt(comun) == w.charAt(comun)) {
            comun++;
        }
        return comun >= 3 && FLEXIONES.contains(v.substring(comun)) && FLEXIONES.contains(w.substring(comun));
    }

    /** Cómo suena la palabra en español: b=v, s=z=c(e,i), j=g(e,i), y=ll, sin h, mb=nb. */
    static String claveFonetica(String palabra) {
        String p = palabra.replace("h", "").replace("ll", "y").replace("qu", "k")
                .replace("ce", "se").replace("ci", "si").replace("ge", "je").replace("gi", "ji")
                .replace('z', 's').replace('v', 'b').replace("nb", "mb").replace("np", "mp");
        return p.replace('c', 'k');
    }

    /**
     * ¿Trae la redacción texto en otra escritura? El modelo de SICOT (qwen)
     * mezcló párrafos en chino en dos de veinte redacciones de la revisión del
     * 29-09-2026 («Se procedió al退货四箱…»). Si no llevan cifras, las demás
     * comprobaciones no los ven.
     */
    public static boolean enOtraEscritura(String redactado) {
        if (redactado == null) {
            return false;
        }
        return redactado.codePoints().anyMatch(cp -> {
            Character.UnicodeScript s = Character.UnicodeScript.of(cp);
            return s != Character.UnicodeScript.LATIN && s != Character.UnicodeScript.COMMON
                    && s != Character.UnicodeScript.INHERITED;
        });
    }

    private static Set<String> palabrasLargas(String texto) {
        Set<String> r = new HashSet<>();
        if (texto == null) {
            return r;
        }
        for (String p : normalizar(texto).split("[^a-z]+")) {
            if (p.length() >= 4) {
                r.add(p);
            }
        }
        return r;
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
