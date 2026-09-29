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
 * <p>Todas las defensas son deterministas porque no se puede confiar en que el
 * modelo obedezca la instrucción de no inventar (la prueba del 24-09-2026 lo
 * mostró desobedeciéndola en los cinco documentos). Cada excepción que acepta
 * una redacción fiel es exacta, no por piezas: una excepción por piezas dejaba
 * pasar cifras cambiadas (revisión del 29-09-2026).
 * <ol>
 *   <li><b>Nombres casi iguales se corrigen.</b> «Marta Lucía Osipina Gómez»
 *       frente a «MARTA LUCÍA OSPINA GÓMEZ» es el mismo nombre con una letra
 *       cambiada: se sustituye por el nombre exacto. Un nombre alterado en un
 *       acta es peor que no nombrarlo.</li>
 *   <li><b>Una redacción con cifras que no estaban se descarta.</b> Si el texto
 *       trae un número —en cifras o en letras— que no aparece en las notas ni
 *       en los datos del contrato, el modelo lo inventó; entonces se usan las
 *       notas tal como las escribió el supervisor.</li>
 *   <li><b>Una redacción que pierde una cifra, cambia una palabra, dice lo
 *       contrario, agrega afirmaciones de cumplimiento o mezcla otra escritura
 *       también se descarta</b> (desde el 29-09-2026, cuando «5 camas» salió
 *       como «las cunas»).</li>
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
     * «120450000» son el mismo número pero «2,5» y «25» no. En letras también
     * se comparan por valor: «treinta pupitres» por «30 pupitres» es la misma
     * cifra, pero «dos de ellas» cuando las notas decían «algunas» es un
     * número inventado, aunque las notas traigan «2026» en una fecha. El
     * modelo convirtió el valor del contrato en letras equivocadas el
     * 24-09-2026, y un número inventado en letras pasaba por no tener dígitos.
     */
    public static boolean sinCifrasInventadas(String redactado, String notas, List<String> datosConocidos) {
        Set<String> conocidas = new HashSet<>(cifras(notas));
        conocidas.addAll(valoresEnLetras(notas));
        for (String dato : datosConocidos) {
            conocidas.addAll(cifras(dato));
            conocidas.addAll(valoresEnLetras(dato));
        }
        for (String cifra : cifras(redactado)) {
            if (!conocidas.contains(cifra)) {
                return false;
            }
        }
        for (String valor : valoresEnLetras(redactado)) {
            if (!conocidas.contains(valor)) {
                return false;
            }
        }
        return true;
    }

    // ── Cifras que se pierden ───────────────────────────────────────────────

    /**
     * Un número de sub-paso del procedimiento («3.1», «4.2»). La revisión del
     * paso le pide al supervisor contar qué hizo «en cada uno de estos
     * puntos», así que sus notas suelen empezar cada frase con uno; la
     * redacción formal los quita con razón, y no son una cifra que se pierda.
     */
    private static final Pattern SUB_PASO = Pattern.compile("[1-6]\\.[1-9]");

    /** Candidato a número de un punto de lista: «1. revisé…», «2) …». */
    private static final Pattern ENUMERADOR = Pattern.compile("(?<![\\p{L}\\d.,:])(\\d{1,2})[.)](?=\\s)");

    /**
     * Un ordinal abreviado, con la palabra que lo sigue: «1ra entrega», «2do
     * piso», «4.º nivel». El sufijo va pegado y el número no puede ser la cola
     * de otro: «71204 va» no es un «4.º».
     */
    private static final Pattern ORDINAL = Pattern.compile(
            "(?<![\\d.,])((\\d{1,2})(?:ro|ra|do|da|er|to|ta|vo|va|no|na|mo|ma|\\.?[°ºª]))(?![\\p{L}\\d])"
                    + "(?:\\s+(\\p{L}+))?",
            Pattern.CASE_INSENSITIVE);

    /** Una fecha en cifras: «15/09/2026», «29-09-2026». */
    private static final Pattern FECHA = Pattern.compile("(?<!\\d)(\\d{1,2})[/-](\\d{1,2})[/-](\\d{4})(?!\\d)");

    private static final String[] ORDINALES = {"", "primer(?:o|a)?", "segund(?:o|a)", "tercer(?:o|a)?",
            "cuart(?:o|a)", "quint(?:o|a)", "sext(?:o|a)", "septim(?:o|a)", "octav(?:o|a)", "noven(?:o|a)",
            "decim(?:o|a)"};

    private static final String[] MESES = {"", "enero", "febrero", "marzo", "abril", "mayo", "junio", "julio",
            "agosto", "septiembre", "octubre", "noviembre", "diciembre"};

    /**
     * Los datos del contrato que la redacción puede omitir son identificadores
     * completos (número del contrato, NIT, valor), de cinco cifras o más. Los
     * fragmentos de un código —el «1» de «CO1», el dígito de verificación del
     * NIT— no: eximirlos dejaba perder «7 computadores» sin que se notara
     * (revisión del 29-09-2026).
     */
    private static final int DIGITOS_DE_UN_IDENTIFICADOR = 5;

    /**
     * ¿Cada cifra de las notas sigue en la redacción? El 29-09-2026 el
     * supervisor escribió «la entrega de 5 camas» y el modelo redactó «la
     * recepción de las cunas»: sin la cantidad, el acta ya no dice lo que el
     * supervisor verificó. Una cantidad o una fecha que se pierde es tan grave
     * como una que se inventa.
     *
     * <p>Una cifra se da por conservada si la redacción trae el mismo valor, en
     * cifras o en letras («treinta y uno» por «31»; «treinta» no). No se exige
     * lo que la redacción quita con razón, pero cada excepción es exacta,
     * porque una excepción por piezas dejaba pasar cifras cambiadas:
     * <ul>
     *   <li>los números de sub-paso, y los de una lista solo si van 1, 2, 3… y
     *       la lista empieza al principio de las notas o de un renglón («mesas
     *       recibidas: 12.» es una cantidad, no un punto de lista);</li>
     *   <li>los identificadores del contrato, que el prompt pide no repetir;</li>
     *   <li>una fecha pasada a forma larga, si aparece entera y seguida («15 de
     *       septiembre de 2026»), no por piezas repartidas en el texto;</li>
     *   <li>un ordinal en letras junto a la misma palabra («segundo piso» por
     *       «2do piso»).</li>
     * </ul>
     */
    public static boolean conservaLasCifras(String redactado, String notas, List<String> datosConocidos) {
        if (notas == null || notas.isBlank()) {
            return true;
        }
        String textoRedactado = redactado == null ? "" : normalizar(redactado);
        List<String> fichasRedaccion = fichas(redactado);
        Set<String> enRedaccion = new HashSet<>(cifras(redactado));
        enRedaccion.addAll(valoresEnLetras(redactado));
        Set<String> identificadores = new HashSet<>();
        for (String dato : datosConocidos) {
            for (String c : cifras(dato)) {
                if (c.replace(".", "").length() >= DIGITOS_DE_UN_IDENTIFICADOR) {
                    identificadores.add(c);
                }
            }
        }
        // Lo que se revisa aparte (fechas, ordinales) o no se exige
        // (enumeradores, un identificador del contrato escrito entero, como
        // «71204-2026») se tapa, para que su número no se cuente dos veces.
        StringBuilder resto = new StringBuilder(notas);
        String notasMinusculas = notas.toLowerCase(Locale.ROOT);
        for (String dato : datosConocidos) {
            if (dato == null || dato.strip().length() < DIGITOS_DE_UN_IDENTIFICADOR || !dato.matches(".*\\d.*")) {
                continue;
            }
            String buscado = dato.strip().toLowerCase(Locale.ROOT);
            for (int k = notasMinusculas.indexOf(buscado); k >= 0; k = notasMinusculas.indexOf(buscado, k + 1)) {
                tapar(resto, k, k + buscado.length());
            }
        }
        if (!mismoOrdenDeFechas(notas, textoRedactado) || !mismoOrdenDeOrdinales(notas, fichasRedaccion)) {
            return false;
        }
        Matcher fecha = FECHA.matcher(notas);
        while (fecha.find()) {
            int dia = Integer.parseInt(fecha.group(1));
            int mes = Integer.parseInt(fecha.group(2));
            String anio = fecha.group(3);
            if (!fechaEnLaRedaccion(textoRedactado, dia, mes, anio)) {
                return false;
            }
            tapar(resto, fecha.start(), fecha.end());
        }
        Matcher ordinal = ORDINAL.matcher(resto.toString());
        while (ordinal.find()) {
            int n = Integer.parseInt(ordinal.group(2));
            String escrito = normalizar(ordinal.group(1));
            String siguiente = ordinal.group(3) == null ? null : normalizar(ordinal.group(3));
            if (!ordinalEnLaRedaccion(fichasRedaccion, textoRedactado, n, escrito, siguiente)) {
                return false;
            }
            tapar(resto, ordinal.start(1), ordinal.end(1));
        }
        for (int[] e : enumeradores(resto.toString())) {
            tapar(resto, e[0], e[1]);
        }
        Matcher m = NUMERO.matcher(resto.toString());
        while (m.find()) {
            String cifra = m.group();
            String canonica = valorCanonico(cifra);
            if (SUB_PASO.matcher(cifra).matches() || identificadores.contains(canonica)
                    || enRedaccion.contains(canonica)) {
                continue;
            }
            return false;
        }
        return true;
    }

    /**
     * Con dos fechas o más, la redacción las tiene que dar en el mismo orden:
     * «visita el 15/09, entrega el 30/09» redactado como «la visita fue el 30
     * y la entrega el 15» tiene las dos fechas, pero cambiadas de papel.
     */
    private static boolean mismoOrdenDeFechas(String notas, String textoRedactado) {
        List<int[]> enNotas = new ArrayList<>();
        Matcher m = FECHA.matcher(notas);
        while (m.find()) {
            enNotas.add(new int[]{Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)),
                    Integer.parseInt(m.group(3))});
        }
        if (enNotas.size() < 2) {
            return true;
        }
        List<Integer> posiciones = new ArrayList<>();
        for (int[] f : enNotas) {
            posiciones.add(posicionDeLaFecha(textoRedactado, f[0], f[1], String.valueOf(f[2])));
        }
        for (int i = 1; i < posiciones.size(); i++) {
            if (posiciones.get(i) >= 0 && posiciones.get(i - 1) >= 0 && posiciones.get(i) < posiciones.get(i - 1)) {
                return false;
            }
        }
        return true;
    }

    /** Con dos ordinales o más, en el mismo orden que en las notas («2do… 1er» no es «primer… segundo»). */
    private static boolean mismoOrdenDeOrdinales(String notas, List<String> fichasRedaccion) {
        List<Integer> enNotas = new ArrayList<>();
        Matcher m = ORDINAL.matcher(notas);
        while (m.find()) {
            enNotas.add(Integer.parseInt(m.group(2)));
        }
        if (enNotas.size() < 2) {
            return true;
        }
        List<Integer> enTexto = new ArrayList<>();
        for (String f : fichasRedaccion) {
            Matcher d = Pattern.compile("(\\d{1,2})(?:ro|ra|do|da|er|to|ta|vo|va|no|na|mo|ma|[°ºª])").matcher(f);
            if (d.matches()) {
                enTexto.add(Integer.parseInt(d.group(1)));
                continue;
            }
            for (int n = 1; n < ORDINALES.length; n++) {
                if (Pattern.compile(ORDINALES[n]).matcher(f).matches()) {
                    enTexto.add(n);
                    break;
                }
            }
        }
        List<Integer> comunes = enTexto.stream().filter(enNotas::contains).toList();
        List<Integer> esperados = enNotas.stream().filter(enTexto::contains).toList();
        return comunes.equals(esperados);
    }

    /** Dónde empieza la fecha en la redacción, en cifras o en forma larga; -1 si no está. */
    private static int posicionDeLaFecha(String textoRedactado, int dia, int mes, String anio) {
        String d = "0?" + dia;
        Matcher corta = Pattern.compile("(?<!\\d)" + d + "[/-]0?" + mes + "[/-]" + anio + "(?!\\d)")
                .matcher(textoRedactado);
        if (corta.find()) {
            return corta.start();
        }
        if (mes < 1 || mes > 12) {
            return -1;
        }
        String diaLargo = dia == 1 ? "(?:0?1|primero)" : d;
        Matcher larga = Pattern.compile("(?<![\\p{L}\\d])" + diaLargo + "\\s+de\\s+" + MESES[mes]
                + "\\s+(?:de|del)\\s+" + anio + "(?!\\d)").matcher(textoRedactado);
        return larga.find() ? larga.start() : -1;
    }

    /** La fecha en cifras (con / o -, con o sin cero delante) o en forma larga, entera y seguida. */
    private static boolean fechaEnLaRedaccion(String textoRedactado, int dia, int mes, String anio) {
        return posicionDeLaFecha(textoRedactado, dia, mes, anio) >= 0;
    }

    /** El ordinal igual que en las notas, o en letras junto a la misma palabra («segundo piso»). */
    private static boolean ordinalEnLaRedaccion(List<String> fichasRedaccion, String textoRedactado, int n,
                                                String escrito, String siguiente) {
        if (Pattern.compile("(?<![\\p{L}\\d])" + Pattern.quote(escrito) + "(?![\\p{L}\\d])")
                .matcher(textoRedactado).find()) {
            return true;
        }
        if (n < 1 || n > 10) {
            return false;
        }
        Pattern enLetras = Pattern.compile(ORDINALES[n]);
        for (int i = 0; i < fichasRedaccion.size(); i++) {
            if (enLetras.matcher(fichasRedaccion.get(i)).matches()) {
                if (siguiente == null) {
                    return true;
                }
                if (i + 1 < fichasRedaccion.size() && mismaPalabra(siguiente, fichasRedaccion.get(i + 1))) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Los números de una lista numerada: solo si son 1, 2, 3… seguidos y la
     * lista empieza al principio de las notas o de un renglón.
     */
    private static List<int[]> enumeradores(String notas) {
        List<int[]> candidatos = new ArrayList<>();
        List<Integer> valores = new ArrayList<>();
        Matcher m = ENUMERADOR.matcher(notas);
        while (m.find()) {
            candidatos.add(new int[]{m.start(1), m.end(1)});
            valores.add(Integer.parseInt(m.group(1)));
        }
        if (candidatos.size() < 2) {
            return List.of();
        }
        for (int i = 0; i < valores.size(); i++) {
            if (valores.get(i) != i + 1) {
                return List.of();
            }
        }
        String antes = notas.substring(0, candidatos.getFirst()[0]);
        int salto = Math.max(antes.lastIndexOf('\n'), antes.lastIndexOf(';'));
        if (!antes.substring(salto + 1).isBlank()) {
            return List.of();
        }
        return candidatos;
    }

    private static void tapar(StringBuilder texto, int desde, int hasta) {
        for (int i = desde; i < Math.min(hasta, texto.length()); i++) {
            texto.setCharAt(i, ' ');
        }
    }

    // ── Números escritos en letras ──────────────────────────────────────────

    private static final java.util.Map<String, Long> VALOR_DE_LA_PALABRA = valoresDeLasPalabras();

    private static java.util.Map<String, Long> valoresDeLasPalabras() {
        java.util.Map<String, Long> v = new java.util.HashMap<>();
        String[] unidades = {"", "uno", "dos", "tres", "cuatro", "cinco", "seis", "siete", "ocho", "nueve", "diez",
                "once", "doce", "trece", "catorce", "quince", "dieciseis", "diecisiete", "dieciocho", "diecinueve",
                "veinte", "veintiuno", "veintidos", "veintitres", "veinticuatro", "veinticinco", "veintiseis",
                "veintisiete", "veintiocho", "veintinueve"};
        for (int i = 1; i < unidades.length; i++) {
            v.put(unidades[i], (long) i);
        }
        v.put("un", 1L);
        v.put("una", 1L);
        v.put("veintiun", 21L);
        v.put("veintiuna", 21L);
        String[] decenas = {"treinta", "cuarenta", "cincuenta", "sesenta", "setenta", "ochenta", "noventa"};
        for (int i = 0; i < decenas.length; i++) {
            v.put(decenas[i], (long) (30 + 10 * i));
        }
        v.put("cien", 100L);
        v.put("ciento", 100L);
        String[] centenas = {"doscient", "trescient", "cuatrocient", "quinient", "seiscient", "setecient",
                "ochocient", "novecient"};
        long[] valores = {200, 300, 400, 500, 600, 700, 800, 900};
        for (int i = 0; i < centenas.length; i++) {
            v.put(centenas[i] + "os", valores[i]);
            v.put(centenas[i] + "as", valores[i]);
        }
        return v;
    }

    private static boolean esPalabraNumerica(String p) {
        return VALOR_DE_LA_PALABRA.containsKey(p) || p.equals("mil") || p.equals("millon") || p.equals("millones");
    }

    /**
     * Los valores de los números escritos en letras del texto: «treinta y uno»
     * es 31, «dos mil veintiseis» es 2026 y «cuatro millones quinientos mil»
     * es 4500000. Una secuencia que es solo «un», «una» o «uno» es un
     * artículo, no un número.
     */
    static List<String> valoresEnLetras(String texto) {
        List<String> r = new ArrayList<>();
        List<String> fs = fichas(texto);
        int i = 0;
        while (i < fs.size()) {
            if (!esPalabraNumerica(fs.get(i))) {
                i++;
                continue;
            }
            List<String> secuencia = new ArrayList<>();
            while (i < fs.size() && (esPalabraNumerica(fs.get(i)) || uneDecenaYUnidad(fs, i, secuencia))) {
                if (!fs.get(i).equals("y")) {
                    secuencia.add(fs.get(i));
                }
                i++;
            }
            boolean soloArticulos = secuencia.stream().allMatch(p -> p.equals("un") || p.equals("una")
                    || p.equals("uno"));
            if (!soloArticulos) {
                r.add(String.valueOf(valorDe(secuencia)));
            }
        }
        return r;
    }

    /** «treinta y cinco»: la «y» solo une una decena con una unidad; «cinco y seis» son dos números. */
    private static boolean uneDecenaYUnidad(List<String> fs, int i, List<String> secuencia) {
        if (!fs.get(i).equals("y") || secuencia.isEmpty() || i + 1 >= fs.size()) {
            return false;
        }
        Long decena = VALOR_DE_LA_PALABRA.get(secuencia.getLast());
        Long unidad = VALOR_DE_LA_PALABRA.get(fs.get(i + 1));
        return decena != null && unidad != null && decena >= 30 && decena <= 90 && decena % 10 == 0
                && unidad >= 1 && unidad <= 9;
    }

    private static long valorDe(List<String> secuencia) {
        long total = 0;
        long grupo = 0;
        for (String p : secuencia) {
            if (p.equals("millon") || p.equals("millones")) {
                total += Math.max(grupo, 1) * 1_000_000L;
                grupo = 0;
            } else if (p.equals("mil")) {
                grupo = Math.max(grupo, 1) * 1000L;
            } else {
                grupo += VALOR_DE_LA_PALABRA.get(p);
            }
        }
        return total + grupo;
    }

    /** El texto sin tildes y en minúsculas, partido en palabras y números. */
    private static List<String> fichas(String texto) {
        List<String> r = new ArrayList<>();
        if (texto == null) {
            return r;
        }
        for (String p : normalizar(texto).split("[^a-z0-9]+")) {
            if (!p.isEmpty()) {
                r.add(p);
            }
        }
        return r;
    }

    // ── Palabras cambiadas ──────────────────────────────────────────────────

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
            "alli", "siendo", "hace", "hizo", "haber", "habia", "tener", "tiene", "tenia", "mas", "menos",
            "segunda", "segundo", "primera", "primero", "tercera", "tercero");

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

    private static boolean esFlexion(String v, String w) {
        int comun = 0;
        while (comun < Math.min(v.length(), w.length()) && v.charAt(comun) == w.charAt(comun)) {
            comun++;
        }
        return comun >= 3 && FLEXIONES.contains(v.substring(comun)) && FLEXIONES.contains(w.substring(comun));
    }

    /** La misma palabra o la misma con otra flexión, sin igualar por sonido. */
    private static boolean mismaPalabra(String a, String b) {
        return a.equals(b) || esFlexion(a, b);
    }

    /** Cómo suena la palabra en español: b=v, s=z=c(e,i), j=g(e,i), y=ll, sin h, mb=nb. */
    static String claveFonetica(String palabra) {
        String p = palabra.replace("h", "").replace("ll", "y").replace("qu", "k")
                .replace("ce", "se").replace("ci", "si").replace("ge", "je").replace("gi", "ji")
                .replace('z', 's').replace('v', 'b').replace("nb", "mb").replace("np", "mp");
        return p.replace('c', 'k');
    }

    // ── Sentido invertido ───────────────────────────────────────────────────

    /** Prefijos que niegan: «incumplió», «imposible», «desorden», «disconforme». */
    private static final List<String> PREFIJOS_NEGATIVOS = List.of("des", "dis", "in", "im");

    /**
     * Palabras que empiezan como una negación sin serlo, y cuyo resto es una
     * palabra habitual en las notas de un supervisor del SENA: «información» y
     * «formación», «inmueble» y «muebles», «impuesto» y «puesto».
     */
    private static final Set<String> NO_NIEGAN = Set.of("informacion", "informaciones", "informe", "informes",
            "informa", "informar", "informo", "inmueble", "inmuebles", "impuesto", "impuestos", "dispuesto",
            "dispuesta", "dispuestos", "dispuestas", "desplace", "desplazo", "desplazamiento", "desplazamientos",
            "instalado", "instalados", "instalada", "instaladas");


    /**
     * ¿Dice la redacción lo contrario que las notas? «El contratista cumplió»
     * redactado como «incumplió» o como «no cumplió», o al revés: una negación
     * de las notas que la redacción quita. Las otras comprobaciones no lo ven,
     * porque no cambia ninguna cifra ni aparece una palabra parecida
     * (revisión del 29-09-2026).
     */
    public static boolean sinSentidoInvertido(String redactado, String notas) {
        Set<String> deNotas = palabrasLargas(notas);
        Set<String> enRedaccion = palabrasLargas(redactado);
        for (String w : enRedaccion) {
            if (deNotas.contains(w) || NO_NIEGAN.contains(w)) {
                continue;
            }
            for (String prefijo : PREFIJOS_NEGATIVOS) {
                if (w.startsWith(prefijo) && w.length() - prefijo.length() >= 4) {
                    String sinPrefijo = w.substring(prefijo.length());
                    if (deNotas.stream().anyMatch(v -> mismaPalabra(v, sinPrefijo))) {
                        return false;
                    }
                }
            }
        }
        for (String v : deNotas) {
            if (enRedaccion.contains(v) || NO_NIEGAN.contains(v)) {
                continue;
            }
            for (String prefijo : PREFIJOS_NEGATIVOS) {
                if (v.startsWith(prefijo) && v.length() - prefijo.length() >= 4) {
                    String sinPrefijo = v.substring(prefijo.length());
                    if (enRedaccion.stream().anyMatch(w -> mismaPalabra(sinPrefijo, w))) {
                        return false;
                    }
                }
            }
        }
        // Con «no»: la palabra que niega es la que le sigue, con a lo sumo un
        // pronombre en medio («no cumplió», «no se entregaron»). No se toma un
        // alcance más largo ni «sin»: «sin novedades» y «no se observaron
        // novedades» dicen lo mismo, y «sin embargo» o «no obstante» no niegan
        // nada; con un alcance de cuatro palabras se descartaban por eso
        // redacciones fieles.
        java.util.Map<String, int[]> enNotas = polaridades(notas);
        java.util.Map<String, int[]> enTexto = polaridades(redactado);
        for (var palabra : enTexto.entrySet()) {
            int afirmadaNotas = 0;
            int negadaNotas = 0;
            for (var v : enNotas.entrySet()) {
                if (mismaPalabra(v.getKey(), palabra.getKey())) {
                    afirmadaNotas += v.getValue()[0];
                    negadaNotas += v.getValue()[1];
                }
            }
            int afirmadaTexto = palabra.getValue()[0];
            int negadaTexto = palabra.getValue()[1];
            if (afirmadaNotas > 0 && negadaNotas == 0 && negadaTexto > 0) {
                return false;
            }
            if (negadaNotas > 0 && afirmadaNotas == 0 && afirmadaTexto > 0 && negadaTexto == 0) {
                return false;
            }
        }
        return true;
    }

    private static final Set<String> NIEGAN_LA_SIGUIENTE = Set.of("no", "nunca", "tampoco");

    private static final Set<String> PRONOMBRES = Set.of("se", "lo", "la", "le", "les", "los", "las", "me", "nos",
            "te", "ya");

    /** Por cada palabra de cuatro letras o más: cuántas veces aparece afirmada y cuántas negada. */
    private static java.util.Map<String, int[]> polaridades(String texto) {
        java.util.Map<String, int[]> r = new java.util.HashMap<>();
        List<String> fs = fichas(texto);
        for (int i = 0; i < fs.size(); i++) {
            String p = fs.get(i);
            if (p.length() < 4 || DEL_REGISTRO_FORMAL.contains(p)) {
                continue;
            }
            int j = i - 1;
            if (j >= 0 && PRONOMBRES.contains(fs.get(j))) {
                j--;
            }
            boolean negada = j >= 0 && NIEGAN_LA_SIGUIENTE.contains(fs.get(j));
            r.computeIfAbsent(p, k -> new int[2])[negada ? 1 : 0]++;
        }
        return r;
    }

    // ── Afirmaciones agregadas ──────────────────────────────────────────────

    /**
     * Raíces de afirmaciones que comprometen al supervisor: plazos,
     * cumplimiento, calidad, conformidad, sanciones. En la prueba en vivo del
     * 29-09-2026, «entregó la 2da parte de los bienes» salió como «entregando
     * la segunda parte de los bienes en el término establecido»: el Informe
     * Final afirmaba que la entrega fue a tiempo sin que el supervisor lo
     * dijera. No tiene cifras ni palabras parecidas, así que nada lo veía.
     */
    private static final List<List<String>> AFIRMACIONES = List.of(
            // plazos
            List.of("termino", "plazo", "tiempo", "oportun", "puntual", "cronograma"),
            // cumplimiento y conformidad con lo pactado
            List.of("cumpl", "conform", "cabalidad", "satisfac", "acordad", "pactad", "estipulad", "debida",
                    "especificac"),
            List.of("garantia"),
            List.of("multa", "sancion"),
            List.of("retras", "demora", "atras"));

    /**
     * ¿Afirma la redacción algo de plazos, cumplimiento, calidad o sanciones
     * que las notas no dicen? Si las notas ya hablan de lo mismo con otra
     * palabra de la misma familia («va dentro del plazo» y «en cumplimiento
     * del plazo», «como estaba acordado» y «conforme a lo acordado»), no es una
     * afirmación nueva. Convertir un hecho en una valoración sí lo es:
     * «entregó los muebles» redactado como «cumplió con la entrega».
     */
    public static boolean sinAfirmacionesAgregadas(String redactado, String notas) {
        Set<String> deNotas = palabrasLargas(notas);
        for (String w : palabrasLargas(redactado)) {
            for (List<String> familia : AFIRMACIONES) {
                boolean enLaRedaccion = familia.stream().anyMatch(w::startsWith);
                if (enLaRedaccion && !familiaEnLasNotas(familia, deNotas)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean familiaEnLasNotas(List<String> familia, Set<String> deNotas) {
        boolean esta = deNotas.stream().anyMatch(v -> familia.stream().anyMatch(v::startsWith));
        // «Cumplir el plazo» es hablar del plazo: si las notas hablan del
        // plazo, la redacción puede decir que se cumple.
        if (!esta && familia.contains("cumpl")) {
            esta = deNotas.stream().anyMatch(v -> AFIRMACIONES.getFirst().stream().anyMatch(v::startsWith));
        }
        return esta;
    }

    // ── Otra escritura ──────────────────────────────────────────────────────

    /**
     * ¿Trae la redacción texto en otra escritura que no estaba en las notas?
     * El modelo de SICOT (qwen) mezcló párrafos en chino en dos de veinte
     * redacciones de la revisión del 29-09-2026 («Se procedió al退货四箱…»);
     * si no llevan cifras, las demás comprobaciones no los ven. Un símbolo que
     * ya venía en las notas («10 Ω», «5 μm») no cuenta.
     */
    public static boolean enOtraEscritura(String redactado, String notas) {
        if (redactado == null) {
            return false;
        }
        Set<Integer> deNotas = new HashSet<>();
        if (notas != null) {
            notas.codePoints().forEach(deNotas::add);
        }
        return redactado.codePoints().anyMatch(cp -> {
            Character.UnicodeScript s = Character.UnicodeScript.of(cp);
            return s != Character.UnicodeScript.LATIN && s != Character.UnicodeScript.COMMON
                    && s != Character.UnicodeScript.INHERITED && !deNotas.contains(cp);
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
