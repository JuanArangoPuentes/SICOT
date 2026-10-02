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

    /**
     * Quita el año que la redacción le agrega a una fecha que las notas
     * escriben sin año: «antes del 20 de octubre» redactado «antes del 20 de
     * octubre de 2026» queda como lo escribió el supervisor.
     *
     * <p>En un registro formal el modelo completa el año casi siempre, con
     * cualquier temperatura, y la comprobación lo descartaba como «agregaba
     * cifras» (auditoría del 02-10-2026, en vivo con qwen2.5:7b). Aceptar el
     * año supuesto no es una opción: al cambiar de año sería un dato
     * inventado, y el diálogo de revisión no resalta números. Así el
     * documento no lleva nada que el supervisor no dio, y la comprobación de
     * fechas sigue exigiendo que una fecha sin año se quede sin año. Si las
     * notas también escriben ese mismo día con año, ese año es suyo y no se
     * toca.
     */
    public static String quitarAniosAgregados(String redactado, String notas) {
        if (redactado == null || notas == null) {
            return redactado;
        }
        List<Fecha> deNotas = fechas(normalizarCifras(notas));
        String r = redactado;
        for (Fecha f : deNotas) {
            if (f.anio() != null || f.mes() < 1 || f.mes() > 12
                    || deNotas.stream().anyMatch(o -> o.anio() != null && o.dia() == f.dia() && o.mes() == f.mes())) {
                continue;
            }
            String d = "0?" + f.dia();
            String diaLargo = f.dia() == 1 ? "(?:0?1|primero)" : d;
            r = Pattern.compile("(?<![\\d/.,-])(" + d + "[/-]0?" + f.mes() + ")[/-]\\d{4}(?![\\d/-])")
                    .matcher(r).replaceAll("$1");
            r = Pattern.compile("(?<![\\p{L}\\d])(" + diaLargo + "\\s+de\\s+" + MESES[f.mes()]
                            + ")\\s+(?:de|del)\\s+\\d{4}(?!\\d)", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE)
                    .matcher(r).replaceAll("$1");
        }
        return r;
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
     * Por qué la redacción no es fiel a las notas, o {@code null} si lo es: la
     * cadena completa de comprobaciones, en el orden en que se informan. La
     * usan el servicio y las pruebas, para que las dos no puedan divergir.
     */
    public static String motivoDeInfidelidad(String corregido, String recortadas, List<String> datos) {
        return corregido.isBlank() ? "venía vacía"
                : enOtraEscritura(corregido, recortadas)
                        ? "mezclaba texto en otro idioma"
                : !sinCifrasInventadas(corregido, recortadas, datos)
                        ? "agregaba cifras que no estaban en sus notas"
                : !sinFechasNiHorasCambiadas(corregido, recortadas)
                        ? "cambiaba o perdía fechas u horas de sus notas"
                : !conservaLasCifras(corregido, recortadas, datos)
                        ? "perdía cifras de sus notas"
                : !sinPalabrasCambiadas(corregido, recortadas, datos)
                        ? "cambiaba palabras de sus notas por otras parecidas"
                : !sinSentidoInvertido(corregido, recortadas)
                        ? "decía lo contrario de sus notas"
                : !sinAfirmacionesAgregadas(corregido, recortadas)
                        ? "afirmaba sobre plazos, cumplimiento o calidad algo que sus notas no dicen"
                : null;
    }

    // ── Cifras, fechas y horas ──────────────────────────────────────────────

    /**
     * ¿Cada número del texto redactado está en las notas o en los datos del
     * contrato? En cifras se comparan por su valor, así que «120.450.000» y
     * «120450000» son el mismo número pero «2,5» y «25» no. En letras también
     * se comparan por valor: «treinta pupitres» por «30 pupitres» es la misma
     * cifra. El modelo convirtió el valor del contrato en letras equivocadas el
     * 24-09-2026, y un número inventado en letras pasaba por no tener dígitos.
     *
     * <p>Las piezas de una fecha, de una hora o de un dato del contrato no
     * autorizan una cantidad: el «2» de «02/10/2026», el «8» de «8 am» o el
     * dígito de verificación del NIT dejaban pasar «dos de ellas», «los 8
     * computadores» o «7 computadores» inventados (revisión del 29-09-2026).
     * Una fecha o una hora de las notas se reconoce entera en la redacción, y
     * un dato del contrato cuenta solo como identificador completo.
     */
    public static boolean sinCifrasInventadas(String redactado, String notas, List<String> datosConocidos) {
        String textoNotas = normalizarCifras(notas == null ? "" : notas);
        String textoRedactado = normalizarCifras(redactado == null ? "" : redactado);
        StringBuilder notasSinReferencias = new StringBuilder(textoNotas);
        StringBuilder redaccionSinReferencias = new StringBuilder(textoRedactado);
        taparFechasYHoras(textoNotas, notasSinReferencias, textoRedactado, redaccionSinReferencias);
        taparDatosDelContrato(notasSinReferencias, datosConocidos);
        taparDatosDelContrato(redaccionSinReferencias, datosConocidos);
        Set<String> conocidas = new HashSet<>(valoresEnOrden(notasSinReferencias.toString()));
        conocidas.addAll(subPasos(textoNotas));
        conocidas.addAll(identificadores(datosConocidos));
        for (String valor : valoresEnOrden(redaccionSinReferencias.toString())) {
            if (!conocidas.contains(valor)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Un número de sub-paso del procedimiento («3.1», «4.2») al principio de
     * una frase. La revisión del paso le pide al supervisor contar qué hizo
     * «en cada uno de estos puntos», así que sus notas suelen empezar cada
     * frase con uno, y la redacción formal los quita con razón. Solo ahí: un
     * «2.5 toneladas» en medio de la frase es un decimal, no un sub-paso, y
     * perderlo se tiene que notar (revisión del 29-09-2026).
     */
    private static final Pattern SUB_PASO = Pattern.compile("(?:^|(?<=[\\n;.]))\\s*([1-6]\\.[1-9])(?=\\s+\\p{L})");

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

    private static final String[] MESES = {"", "enero", "febrero", "marzo", "abril", "mayo", "junio", "julio",
            "agosto", "septiembre", "octubre", "noviembre", "diciembre"};

    private static final Set<String> DIAS_DE_LA_SEMANA = Set.of("lunes", "martes", "miercoles", "jueves", "viernes",
            "sabado", "domingo");

    /** Una fecha en cifras, con año o sin él: «15/09/2026», «29-09-2026», «15/09». */
    private static final Pattern FECHA = Pattern.compile(
            "(?<![\\d/.,-])(\\d{1,2})[/-](\\d{1,2})(?:[/-](\\d{4}))?(?![\\d/-])");

    /** Una fecha en letras: «15 de septiembre», «1 de octubre de 2026», «primero de mayo». */
    private static final Pattern FECHA_LARGA = Pattern.compile("(?<![\\p{L}\\d])(\\d{1,2}|primero)\\s+de\\s+("
            + String.join("|", java.util.Arrays.copyOfRange(MESES, 1, MESES.length))
            + ")(?:\\s+(?:de|del)\\s+(\\d{4}))?(?!\\d)");

    /** Una hora con minutos o con «a. m.»/«p. m.»: «10:30», «10:30 am», «8 a. m.», «2 pm». */
    private static final Pattern HORA = Pattern.compile("(?<![\\d:/.,])(\\d{1,2})(?::(\\d{2})"
            + "(?:\\s*([ap])\\.?\\s?m\\.?(?![a-z]))?|\\s*([ap])\\.?\\s?m\\.?(?![a-z]))");

    private record Fecha(int dia, int mes, String anio) {
    }

    private record Hora(int hora, int minutos, char meridiano) {
    }

    /** Las fechas de un texto normalizado, en el orden en que aparecen. */
    private static List<Fecha> fechas(String texto) {
        java.util.TreeMap<Integer, Fecha> porPosicion = new java.util.TreeMap<>();
        Matcher corta = FECHA.matcher(texto);
        while (corta.find()) {
            porPosicion.put(corta.start(), new Fecha(Integer.parseInt(corta.group(1)),
                    Integer.parseInt(corta.group(2)), corta.group(3)));
        }
        Matcher larga = FECHA_LARGA.matcher(texto);
        while (larga.find()) {
            int dia = larga.group(1).equals("primero") ? 1 : Integer.parseInt(larga.group(1));
            int mes = java.util.Arrays.asList(MESES).indexOf(larga.group(2));
            porPosicion.put(larga.start(), new Fecha(dia, mes, larga.group(3)));
        }
        return new ArrayList<>(porPosicion.values());
    }

    /** Dónde está la fecha en el texto normalizado, en cifras o en letras, entera; {@code null} si no está. */
    private static int[] dondeEsta(String texto, Fecha f) {
        String d = "0?" + f.dia();
        String anio = f.anio() == null ? "(?![/-]?\\d)" : "[/-]" + f.anio() + "(?!\\d)";
        Matcher corta = Pattern.compile("(?<![\\d/.,-])" + d + "[/-]0?" + f.mes() + anio).matcher(texto);
        if (corta.find()) {
            return new int[]{corta.start(), corta.end()};
        }
        if (f.mes() < 1 || f.mes() > 12) {
            return null;
        }
        String diaLargo = f.dia() == 1 ? "(?:0?1|primero)" : d;
        String anioLargo = f.anio() == null ? "(?!\\s+(?:de|del)\\s+\\d)" : "\\s+(?:de|del)\\s+" + f.anio() + "(?!\\d)";
        Matcher larga = Pattern.compile("(?<![\\p{L}\\d])" + diaLargo + "\\s+de\\s+" + MESES[f.mes()] + anioLargo)
                .matcher(texto);
        return larga.find() ? new int[]{larga.start(), larga.end()} : null;
    }

    private static List<Hora> horas(String texto) {
        List<Hora> r = new ArrayList<>();
        Matcher m = HORA.matcher(texto);
        while (m.find()) {
            r.add(horaDe(m));
        }
        return r;
    }

    private static Hora horaDe(Matcher m) {
        int minutos = m.group(2) == null ? -1 : Integer.parseInt(m.group(2));
        String meridiano = m.group(3) != null ? m.group(3) : m.group(4);
        return new Hora(Integer.parseInt(m.group(1)), minutos, meridiano == null ? 0 : meridiano.charAt(0));
    }

    /**
     * Dónde está la hora en la redacción: la misma hora y minutos, y el mismo
     * meridiano si los dos lo dicen. «2 pm» y «14:00» son la misma hora.
     */
    private static int[] dondeEsta(String texto, Hora h) {
        Matcher m = HORA.matcher(texto);
        while (m.find()) {
            Hora otra = horaDe(m);
            boolean mismosMinutos = otra.minutos() == h.minutos() || (h.minutos() <= 0 && otra.minutos() <= 0);
            boolean mismoMeridiano = otra.meridiano() == h.meridiano() || otra.meridiano() == 0 || h.meridiano() == 0;
            boolean mismaHora = otra.hora() == h.hora() || en24(otra) == en24(h);
            if (mismaHora && mismosMinutos && mismoMeridiano) {
                return new int[]{m.start(), m.end()};
            }
        }
        return null;
    }

    private static int en24(Hora h) {
        if (h.meridiano() == 'p' && h.hora() < 12) {
            return h.hora() + 12;
        }
        if (h.meridiano() == 'a' && h.hora() == 12) {
            return 0;
        }
        return h.hora();
    }

    /**
     * Tapa en las notas todas sus fechas y horas, y en la redacción las MISMAS
     * fechas y horas de las notas (no otras: una fecha inventada tiene que
     * seguir viéndose como cifras que no estaban).
     */
    private static void taparFechasYHoras(String textoNotas, StringBuilder notas, String textoRedactado,
                                          StringBuilder redaccion) {
        for (Pattern p : List.of(FECHA, FECHA_LARGA, HORA)) {
            Matcher m = p.matcher(textoNotas);
            while (m.find()) {
                tapar(notas, m.start(), m.end());
            }
        }
        for (Fecha f : fechas(textoNotas)) {
            int[] donde = dondeEsta(textoRedactado, f);
            if (donde != null) {
                tapar(redaccion, donde[0], donde[1]);
            }
        }
        for (Hora h : horas(textoNotas)) {
            int[] donde = dondeEsta(textoRedactado, h);
            if (donde != null) {
                tapar(redaccion, donde[0], donde[1]);
            }
        }
    }

    /**
     * Los datos del contrato que la redacción puede omitir o repetir son
     * identificadores completos (número del contrato, NIT, valor), de cinco
     * cifras o más. Los fragmentos de un código —el «1» de «CO1», el dígito de
     * verificación del NIT— no: eximirlos dejaba perder «7 computadores» sin
     * que se notara (revisión del 29-09-2026).
     */
    private static final int DIGITOS_DE_UN_IDENTIFICADOR = 5;

    private static Set<String> identificadores(List<String> datosConocidos) {
        Set<String> r = new HashSet<>();
        for (String dato : datosConocidos) {
            if (dato == null) {
                continue;
            }
            for (String v : valoresEnOrden(normalizar(dato))) {
                if (v.replace(".", "").length() >= DIGITOS_DE_UN_IDENTIFICADOR) {
                    r.add(v);
                }
            }
        }
        return r;
    }

    /** Tapa cada dato del contrato escrito entero («71204-2026», «900123456-7»): sus números no son cantidades. */
    private static void taparDatosDelContrato(StringBuilder texto, List<String> datosConocidos) {
        String t = texto.toString();
        for (String dato : datosConocidos) {
            if (dato == null || dato.strip().length() < DIGITOS_DE_UN_IDENTIFICADOR || !dato.matches(".*\\d.*")) {
                continue;
            }
            String buscado = normalizar(dato.strip());
            for (int k = t.indexOf(buscado); k >= 0; k = t.indexOf(buscado, k + 1)) {
                tapar(texto, k, k + buscado.length());
            }
        }
    }

    private static Set<String> subPasos(String textoNotas) {
        Set<String> r = new HashSet<>();
        Matcher m = SUB_PASO.matcher(textoNotas);
        while (m.find()) {
            r.add(valorCanonico(m.group(1)));
        }
        return r;
    }

    /**
     * ¿Cada cantidad de las notas sigue en la redacción? El 29-09-2026 el
     * supervisor escribió «la entrega de 5 camas» y el modelo redactó «la
     * recepción de las cunas»: sin la cantidad, el acta ya no dice lo que el
     * supervisor verificó. Una cantidad que se pierde es tan grave como una que
     * se inventa.
     *
     * <p>Una cantidad se da por conservada si la redacción trae el mismo valor,
     * en cifras o en letras («treinta y uno» por «31»; «treinta» no), tantas
     * veces como en las notas («5 sillas y 5 mesas» necesita los dos cincos) y
     * en el mismo orden relativo («12 mesas y 5 sillas» no es «5 mesas y 12
     * sillas»). No se exige lo que la redacción quita con razón, y cada
     * excepción es exacta, porque una excepción por piezas dejaba pasar
     * cantidades cambiadas:
     * <ul>
     *   <li>los números de sub-paso al principio de una frase, y los de una
     *       lista solo si van 1, 2, 3… y la lista empieza al principio de las
     *       notas o de un renglón («mesas recibidas: 12.» es una cantidad);</li>
     *   <li>los identificadores del contrato, que el prompt pide no repetir;</li>
     *   <li>las fechas y las horas, que se comprueban enteras en
     *       {@link #sinFechasNiHorasCambiadas};</li>
     *   <li>un ordinal en letras junto a la misma palabra («segundo piso» por
     *       «2do piso»).</li>
     * </ul>
     */
    public static boolean conservaLasCifras(String redactado, String notas, List<String> datosConocidos) {
        if (notas == null || notas.isBlank()) {
            return true;
        }
        String textoNotas = normalizarCifras(notas);
        String textoRedactado = redactado == null ? "" : normalizarCifras(redactado);
        List<String> fichasRedaccion = fichas(redactado);
        if (!ordinalesConservados(textoNotas, textoRedactado, fichasRedaccion)) {
            return false;
        }
        // Calificadores que limitan lo recibido: perderlos cambia la cantidad
        // («entregó la mitad de los bienes» no es «entregó los bienes»).
        Set<String> palabrasRedaccion = palabrasLargas(redactado);
        for (String v : palabrasLargas(notas)) {
            for (String raiz : List.of("mitad", "parcial")) {
                if (v.startsWith(raiz) && palabrasRedaccion.stream().noneMatch(w -> w.startsWith(raiz))) {
                    return false;
                }
            }
        }
        StringBuilder notasSinReferencias = new StringBuilder(textoNotas);
        StringBuilder redaccionSinReferencias = new StringBuilder(textoRedactado);
        taparFechasYHoras(textoNotas, notasSinReferencias, textoRedactado, redaccionSinReferencias);
        taparDatosDelContrato(notasSinReferencias, datosConocidos);
        taparDatosDelContrato(redaccionSinReferencias, datosConocidos);
        taparOrdinales(notasSinReferencias);
        taparOrdinales(redaccionSinReferencias);
        Matcher sub = SUB_PASO.matcher(notasSinReferencias.toString());
        while (sub.find()) {
            tapar(notasSinReferencias, sub.start(1), sub.end(1));
        }
        for (int[] e : enumeradores(notasSinReferencias.toString())) {
            tapar(notasSinReferencias, e[0], e[1]);
        }
        Set<String> identificadores = identificadores(datosConocidos);
        List<String[]> conCosa = valoresConCosa(notasSinReferencias.toString()).stream()
                .filter(v -> !identificadores.contains(v[0])).toList();
        List<String> deNotas = conCosa.stream().map(v -> v[0]).toList();
        // «2 hornos, 1 nevera» redactado «dos hornos, una nevera»: el artículo
        // vale por el 1 solo delante de la misma cosa que las notas cuentan
        // con 1. En general no cuenta, porque casi siempre es un artículo
        // (medición con qwen2.5:7b del 01-10-2026).
        Set<String> contadasConUno = new HashSet<>();
        conCosa.stream().filter(v -> v[0].equals("1") && v[1] != null).forEach(v -> contadasConUno.add(v[1]));
        if (!contadasConUno.isEmpty()) {
            Matcher articulo = Pattern.compile("(?<![\\p{L}\\d])(un[ao]?)\\s+(\\p{L}+)")
                    .matcher(redaccionSinReferencias.toString());
            while (articulo.find()) {
                String cosa = articulo.group(2);
                if (contadasConUno.stream().anyMatch(c -> mismaPalabra(c, cosa))) {
                    redaccionSinReferencias.replace(articulo.start(1), articulo.end(1),
                            "1" + " ".repeat(articulo.group(1).length() - 1));
                }
            }
        }
        List<String> deRedaccion = new ArrayList<>(valoresEnOrden(redaccionSinReferencias.toString()));
        // «las dos corresponden a lo entregado» → «ambas corresponden…»: para
        // conservar un «dos» de las notas, «ambas» vale. Para inventar no
        // cuenta: «5 camas y 12 mesas, ambas en buen estado» no agrega un 2.
        fichas(redactado).stream().filter(f -> f.equals("ambos") || f.equals("ambas")).forEach(f -> deRedaccion.add("2"));
        java.util.Map<String, Long> cuantasEnRedaccion = deRedaccion.stream()
                .collect(java.util.stream.Collectors.groupingBy(v -> v, java.util.stream.Collectors.counting()));
        // Una cifra repetida se exige tantas veces como cosas distintas
        // acompaña: «5 sillas y 5 mesas» necesita los dos cincos, pero «2
        // sillas con rayones, se pidió el cambio de esas 2» es una sola cosa.
        java.util.Map<String, Set<String>> cosasPorValor = new java.util.HashMap<>();
        for (String[] v : conCosa) {
            Set<String> cosas = cosasPorValor.computeIfAbsent(v[0], k -> new HashSet<>());
            if (v[1] != null && cosas.stream().noneMatch(c -> mismaPalabra(c, v[1]))) {
                cosas.add(v[1]);
            }
        }
        for (var e : cosasPorValor.entrySet()) {
            long necesarias = Math.max(1, e.getValue().size());
            if (cuantasEnRedaccion.getOrDefault(e.getKey(), 0L) < necesarias) {
                return false;
            }
        }
        List<String> ordenNotas = deNotas.stream().distinct().toList();
        List<String> ordenRedaccion = deRedaccion.stream().distinct().filter(ordenNotas::contains).toList();
        return ordenNotas.equals(ordenRedaccion);
    }

    private static void taparOrdinales(StringBuilder texto) {
        Matcher m = ORDINAL.matcher(texto.toString());
        while (m.find()) {
            tapar(texto, m.start(1), m.end(1));
        }
    }

    /**
     * Los ordinales de las notas siguen en la redacción, en el mismo orden, y
     * la redacción no cambia el de una cosa que las notas nombran: «la primera
     * entrega» no puede salir como «la segunda entrega». En letras también:
     * antes solo se miraban los que las notas escribían en cifras («2do»).
     */
    private static boolean ordinalesConservados(String textoNotas, String textoRedactado,
                                                List<String> fichasRedaccion) {
        Matcher ordinal = ORDINAL.matcher(textoNotas);
        while (ordinal.find()) {
            int n = Integer.parseInt(ordinal.group(2));
            String siguiente = ordinal.group(3);
            if (!ordinalEnLaRedaccion(fichasRedaccion, textoRedactado, n, ordinal.group(1), siguiente)) {
                return false;
            }
        }
        List<int[]> enNotas = ordinalesConPalabra(fichas(textoNotas));
        List<int[]> enRedaccion = ordinalesConPalabra(fichasRedaccion);
        List<String> fichasNotas = fichas(textoNotas);
        List<String> palabrasNotas = fichasNotas.stream().filter(p -> p.length() >= 4).toList();
        for (int[] o : enRedaccion) {
            String cosa = o[1] >= 0 ? fichasRedaccion.get(o[1]) : null;
            if (cosa == null || palabrasNotas.stream().noneMatch(v -> mismaPalabra(v, cosa))) {
                continue;
            }
            // «falta la segunda» sin decir de qué: la redacción puede completar
            // «la segunda entrega» si las notas ya tienen ese ordinal.
            boolean mismoEnNotas = enNotas.stream().anyMatch(on -> on[0] == o[0]
                    && (on[1] < 0 || mismaPalabra(fichasNotas.get(on[1]), cosa)));
            boolean otroEnNotas = enNotas.stream().anyMatch(on -> on[0] != o[0] && on[1] >= 0
                    && mismaPalabra(fichasNotas.get(on[1]), cosa));
            boolean sinOrdinalEnNotas = enNotas.stream().noneMatch(on -> on[1] >= 0
                    && mismaPalabra(fichasNotas.get(on[1]), cosa));
            if (!mismoEnNotas && (otroEnNotas || sinOrdinalEnNotas && o[0] > 1)) {
                return false;
            }
        }
        List<Integer> ordenNotas = enNotas.stream().map(o -> o[0]).toList();
        if (ordenNotas.size() >= 2) {
            List<Integer> ordenRedaccion = enRedaccion.stream().map(o -> o[0]).filter(ordenNotas::contains).toList();
            List<Integer> esperados = ordenNotas.stream().filter(ordenRedaccion::contains).toList();
            return ordenRedaccion.equals(esperados);
        }
        return true;
    }

    /**
     * Los ordinales de una lista de fichas —«2do», «segunda»— con la posición
     * de la palabra de cuatro letras o más que los sigue (o -1): {valor, posición}.
     */
    private static List<int[]> ordinalesConPalabra(List<String> fs) {
        List<int[]> r = new ArrayList<>();
        Pattern enCifras = Pattern.compile("(\\d{1,2})(?:ro|ra|do|da|er|to|ta|vo|va|no|na|mo|ma|[°ºª])");
        for (int i = 0; i < fs.size(); i++) {
            int valor = -1;
            Matcher d = enCifras.matcher(fs.get(i));
            if (d.matches()) {
                valor = Integer.parseInt(d.group(1));
            } else if (i + 1 < fs.size() && fs.get(i).matches("\\d{1,2}")
                    && fs.get(i + 1).matches("ro|ra|do|da|er|to|ta|vo|va|no|na|mo|ma")) {
                valor = Integer.parseInt(fs.get(i));
                i++;
            } else {
                for (int n = 1; n < ORDINALES.length; n++) {
                    if (Pattern.compile(ORDINALES[n]).matcher(fs.get(i)).matches()) {
                        valor = n;
                        break;
                    }
                }
            }
            if (valor < 0) {
                continue;
            }
            int palabra = -1;
            for (int j = i + 1; j < Math.min(fs.size(), i + 3); j++) {
                if (fs.get(j).length() >= 4) {
                    palabra = j;
                    break;
                }
            }
            r.add(new int[]{valor, palabra});
        }
        return r;
    }

    private static final String[] ORDINALES = {"", "primer(?:o|a|os|as)?", "segund(?:o|a|os|as)",
            "tercer(?:o|a|os|as)?", "cuart(?:o|a|os|as)", "quint(?:o|a|os|as)", "sext(?:o|a|os|as)",
            "septim(?:o|a|os|as)", "octav(?:o|a|os|as)", "noven(?:o|a|os|as)", "decim(?:o|a|os|as)"};

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
                // «los primeros y segundos pisos»: la cosa va después de una
                // enumeración de ordinales.
                for (int j = i + 1; j < Math.min(fichasRedaccion.size(), i + 5); j++) {
                    String despues = fichasRedaccion.get(j);
                    if (mismaPalabra(siguiente, despues)) {
                        return true;
                    }
                    if (!despues.equals("y") && !despues.equals("e") && !esOrdinalEnLetras(despues)) {
                        break;
                    }
                }
                // «los pisos primero y segundo»: la cosa va antes, en una
                // enumeración de ordinales (medición del 01-10-2026, donde
                // «el 1er piso y el 2do piso» redactado así se descartaba).
                for (int j = i - 1; j >= Math.max(0, i - 4); j--) {
                    String antes = fichasRedaccion.get(j);
                    if (mismaPalabra(siguiente, antes)) {
                        return true;
                    }
                    if (!antes.equals("y") && !antes.equals("e") && !esOrdinalEnLetras(antes)) {
                        break;
                    }
                }
            }
        }
        return false;
    }

    private static boolean esOrdinalEnLetras(String ficha) {
        for (int n = 1; n < ORDINALES.length; n++) {
            if (ficha.matches(ORDINALES[n])) {
                return true;
            }
        }
        return false;
    }

    /**
     * ¿Siguen las fechas y horas de las notas, enteras y en el mismo orden, y
     * no cambió un mes, un día de la semana o un «a. m.»? Cada una se busca
     * entera y seguida —«15 de septiembre de 2026», no el día, el mes y el año
     * repartidos por el texto— y con dos o más se exige el mismo orden que en
     * las notas: «la visita el 15/09 y la entrega el 30/09» redactado con las
     * fechas cambiadas de papel tiene las dos, pero dice otra cosa. Los meses,
     * los días de la semana y el «a. m.»/«p. m.» son palabras: ninguna otra
     * comprobación veía «agosto» por «septiembre» o «lunes» por «martes»
     * (revisión del 29-09-2026).
     */
    public static boolean sinFechasNiHorasCambiadas(String redactado, String notas) {
        if (notas == null || notas.isBlank()) {
            return true;
        }
        String textoNotas = normalizarCifras(notas);
        String textoRedactado = redactado == null ? "" : normalizarCifras(redactado);
        int anterior = -1;
        for (Fecha f : fechas(textoNotas)) {
            int[] donde = dondeEsta(textoRedactado, f);
            if (donde == null || donde[0] < anterior) {
                return false;
            }
            anterior = donde[0];
        }
        anterior = -1;
        for (Hora h : horas(textoNotas)) {
            int[] donde = dondeEsta(textoRedactado, h);
            if (donde == null || donde[0] < anterior) {
                return false;
            }
            anterior = donde[0];
        }
        Set<String> mesesNotas = new HashSet<>();
        Set<String> mesesRedaccion = new HashSet<>();
        for (Fecha f : fechas(textoNotas)) {
            if (f.mes() >= 1 && f.mes() <= 12) {
                mesesNotas.add(MESES[f.mes()]);
            }
        }
        List<String> fichasNotas = fichas(notas);
        List<String> fichasRedaccion = fichas(redactado);
        Set<String> mesesConNombre = new HashSet<>(java.util.Arrays.asList(MESES).subList(1, MESES.length));
        fichasNotas.stream().filter(mesesConNombre::contains).forEach(mesesNotas::add);
        fichasRedaccion.stream().filter(mesesConNombre::contains).forEach(mesesRedaccion::add);
        for (Fecha f : fechas(textoRedactado)) {
            if (f.mes() >= 1 && f.mes() <= 12) {
                mesesRedaccion.add(MESES[f.mes()]);
            }
        }
        if (!mesesNotas.containsAll(mesesRedaccion) || !mesesRedaccion.containsAll(mesesNotas)) {
            return false;
        }
        Set<String> diasNotas = new HashSet<>(fichasNotas);
        diasNotas.retainAll(DIAS_DE_LA_SEMANA);
        Set<String> diasRedaccion = new HashSet<>(fichasRedaccion);
        diasRedaccion.retainAll(DIAS_DE_LA_SEMANA);
        return diasNotas.equals(diasRedaccion);
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

    /** Números del texto normalizado, en cifras o en letras, en el orden en que aparecen. */
    private static final Pattern FICHA_CON_NUMEROS = Pattern.compile(
            "[a-z]+|\\d{1,3}(?:\\.\\d{3})+(?:,\\d+)?|\\d+(?:[.,]\\d+)?");

    /**
     * Los valores de los números de un texto ya normalizado, en orden: «30» y
     * «treinta» valen 30, «treinta y uno» 31, «dos mil veintiseis» 2026,
     * «cuatro millones quinientos mil» 4500000, «un par» 2 y «una docena» 12.
     * Una secuencia que es solo «un», «una» o «uno» es un artículo, no un número.
     */
    static List<String> valoresEnOrden(String textoNormalizado) {
        List<String> r = new ArrayList<>();
        List<String> fs = new ArrayList<>();
        Matcher m = FICHA_CON_NUMEROS.matcher(textoNormalizado == null ? "" : textoNormalizado);
        while (m.find()) {
            fs.add(m.group());
        }
        int i = 0;
        while (i < fs.size()) {
            String f = fs.get(i);
            if (Character.isDigit(f.charAt(0))) {
                r.add(valorCanonico(f));
                i++;
                continue;
            }
            if (!esPalabraNumerica(f)) {
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

    /**
     * Como {@link #valoresEnOrden}, pero con la palabra de cuatro letras o más
     * que sigue a cada número (la cosa que cuenta), o {@code null}.
     */
    private static List<String[]> valoresConCosa(String textoNormalizado) {
        List<String[]> r = new ArrayList<>();
        List<String> fs = new ArrayList<>();
        Matcher m = FICHA_CON_NUMEROS.matcher(textoNormalizado == null ? "" : textoNormalizado);
        while (m.find()) {
            fs.add(m.group());
        }
        int i = 0;
        while (i < fs.size()) {
            String f = fs.get(i);
            String valor = null;
            if (Character.isDigit(f.charAt(0))) {
                valor = valorCanonico(f);
                i++;
            } else if (esPalabraNumerica(f)) {
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
                    valor = String.valueOf(valorDe(secuencia));
                }
            } else {
                i++;
            }
            if (valor == null) {
                continue;
            }
            String cosa = null;
            for (int j = i; j < Math.min(fs.size(), i + 3); j++) {
                String g = fs.get(j);
                if (DE_ENLACE.contains(g) || Character.isDigit(g.charAt(0))) {
                    continue;
                }
                if (g.length() >= 4) {
                    cosa = g;
                }
                break;
            }
            r.add(new String[]{valor, cosa});
        }
        return r;
    }

    /** Los valores en letras de un texto sin normalizar (se mantiene para quien lo usa). */
    static List<String> valoresEnLetras(String texto) {
        List<String> r = new ArrayList<>();
        for (String v : valoresEnOrden(texto == null ? "" : normalizar(texto).replaceAll("\\d", " "))) {
            r.add(v);
        }
        return r;
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
        // «el saldo a liberar es 0» redactado «es cero» perdía el 0 (medición
        // con qwen2.5:7b del 01-10-2026).
        v.put("cero", 0L);
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

    /**
     * Palabras que multiplican lo anterior: «un par» es 2 y «dos docenas» 24.
     * Sin ellas, «un par de ellas con rayones» fijaba una cantidad que las
     * notas no daban («algunas») sin que se notara.
     */
    private static final java.util.Map<String, Long> MULTIPLICADORES = java.util.Map.of("par", 2L, "pares", 2L,
            "docena", 12L, "docenas", 12L, "decena", 10L, "decenas", 10L);

    private static boolean esPalabraNumerica(String p) {
        return VALOR_DE_LA_PALABRA.containsKey(p) || MULTIPLICADORES.containsKey(p) || p.equals("mil")
                || p.equals("millon") || p.equals("millones");
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
            } else if (MULTIPLICADORES.containsKey(p)) {
                grupo = Math.max(grupo, 1) * MULTIPLICADORES.get(p);
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
            "iste", "n", "s", "r", "ra", "ro", "io");

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

    /**
     * ¿Son la misma palabra con otra terminación? Se prueba desde el prefijo
     * común más largo hacia atrás, hasta tres letras: con el más largo solo,
     * «cumplió» y «cumplido» («o» / «do») o «entregaron» y «entregado» no se
     * reconocían, y una negación en una de las dos pasaba sin verse.
     */
    private static boolean esFlexion(String v, String w) {
        int comun = 0;
        while (comun < Math.min(v.length(), w.length()) && v.charAt(comun) == w.charAt(comun)) {
            comun++;
        }
        for (int k = comun; k >= 3; k--) {
            if (FLEXIONES.contains(v.substring(k)) && FLEXIONES.contains(w.substring(k))) {
                return true;
            }
        }
        return false;
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
        // Se probaron también una negación de alcance amplio y estados opuestos
        // de todo el texto («pendiente» / «entregada»): con 44 redacciones
        // reales descartaban de más paráfrasis fieles («no llegaron los conos»
        // → «los conos no fueron entregados», «no hay energía» → «ausencia de
        // energía»). Desde que el supervisor ve la redacción antes de firmar,
        // se prefieren reglas precisas; lo que no ven, lo ve él.
        if (!sinOpuestosCambiados(redactado, notas)) {
            return false;
        }
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
            // Quitar una negación solo cuenta con la misma forma de la palabra:
            // «no ha pagado» → «el pago» es otra cosa, «no ha pagado» → «ya ha
            // pagado» no.
            boolean mismaForma = enNotas.containsKey(palabra.getKey());
            if (mismaForma && negadaNotas > 0 && afirmadaNotas == 0 && afirmadaTexto > 0 && negadaTexto == 0) {
                return false;
            }
        }
        return true;
    }

    private static final Set<String> NIEGAN_LA_SIGUIENTE = Set.of("no", "nunca", "tampoco");

    private static boolean tieneUnNegadorAntes(List<String> fs, int i) {
        for (int j = Math.max(0, i - 3); j < i; j++) {
            if (NEGADORES.contains(fs.get(j))) {
                return true;
            }
        }
        return false;
    }

    /** Negadores: una valoración que va detrás de uno («no encendían correctamente») no es una afirmación. */
    private static final Set<String> NEGADORES = Set.of("no", "sin", "ningun", "ninguna", "ninguno", "ningunas",
            "ningunos", "nunca", "tampoco", "ni");

    /**
     * Pares de palabras opuestas que califican a la palabra que las sigue:
     * «en mal estado» / «en buen estado», «con rayones» / «sin rayones»,
     * «dentro del plazo» / «fuera del plazo». Un Acta de Recibo a Satisfacción
     * podía decir «las sillas llegaron en buen estado» cuando las notas decían
     * «en mal estado» (revisión del 29-09-2026). Se comparan con la palabra que
     * acompañan, no sueltas: «con» y «sin» aparecen en cualquier frase.
     */
    private static final List<List<Set<String>>> OPUESTOS = List.of(
            List.of(Set.of("mal", "mala", "malas", "malos"), Set.of("buen", "buena", "buenas", "buenos", "bien")),
            List.of(Set.of("con"), Set.of("sin")),
            List.of(Set.of("dentro"), Set.of("fuera")),
            List.of(Set.of("antes"), Set.of("despues")),
            List.of(Set.of("mas"), Set.of("menos")),
            List.of(Set.of("algunas", "algunos", "varias", "varios", "unas", "unos"), Set.of("todas", "todos")));

    /** Palabras que se saltan para llegar a la que el calificador acompaña. */
    private static final Set<String> DE_ENLACE = Set.of("de", "del", "la", "las", "el", "los", "un", "una", "en",
            "al");

    private static boolean sinOpuestosCambiados(String redactado, String notas) {
        List<String> fn = fichas(notas);
        List<String> fr = fichas(redactado);
        for (List<Set<String>> par : OPUESTOS) {
            Set<String> enNotasA = acompanadas(fn, par.get(0));
            Set<String> enNotasB = acompanadas(fn, par.get(1));
            Set<String> enTextoA = acompanadas(fr, par.get(0));
            Set<String> enTextoB = acompanadas(fr, par.get(1));
            for (String cosa : enTextoB) {
                if (contiene(enNotasA, cosa) && !contiene(enNotasB, cosa) && !contiene(enTextoA, cosa)) {
                    return false;
                }
            }
            for (String cosa : enTextoA) {
                if (contiene(enNotasB, cosa) && !contiene(enNotasA, cosa) && !contiene(enTextoB, cosa)) {
                    return false;
                }
            }
        }
        return true;
    }

    /** Las palabras (de cuatro letras o más, o cifras) que acompañan a cada aparición de esos calificadores. */
    private static Set<String> acompanadas(List<String> fs, Set<String> calificadores) {
        Set<String> r = new HashSet<>();
        for (int i = 0; i < fs.size(); i++) {
            if (!calificadores.contains(fs.get(i))) {
                continue;
            }
            if (fs.get(i).equals("sin") && i + 1 < fs.size() && fs.get(i + 1).equals("embargo")) {
                continue;
            }
            for (int j = i + 1; j < Math.min(fs.size(), i + 4); j++) {
                String f = fs.get(j);
                if (DE_ENLACE.contains(f) || f.matches("\\d+")) {
                    continue;
                }
                if (f.length() >= 4) {
                    r.add(f);
                }
                break;
            }
        }
        return r;
    }

    private static boolean contiene(Set<String> palabras, String cosa) {
        return palabras.stream().anyMatch(p -> mismaPalabra(p, cosa));
    }

    private static final Set<String> PRONOMBRES = Set.of("se", "lo", "la", "le", "les", "los", "las", "me", "nos",
            "te", "ya");

    private static final Set<String> AUXILIARES = Set.of("ha", "han", "he", "hemos", "habia", "habian",
            "fue", "fueron", "es", "son", "era", "eran", "esta", "estan", "estaba", "estaban", "sido", "aun",
            "todavia", "sera", "seran");

    /**
     * Palabras cortas o del registro formal que sí llevan el sentido de la
     * frase: «no hay saldo» frente a «hay saldo», «no está al día» frente a
     * «está al día», «no todas» frente a «todas».
     */
    private static final Set<String> LLEVAN_EL_SENTIDO = Set.of("hay", "esta", "estan", "todas", "todos", "todo",
            "toda", "fue", "fueron");

    /** Por cada palabra de cuatro letras o más: cuántas veces aparece afirmada y cuántas negada. */
    private static java.util.Map<String, int[]> polaridades(String texto) {
        java.util.Map<String, int[]> r = new java.util.HashMap<>();
        List<String> fs = fichas(texto);
        for (int i = 0; i < fs.size(); i++) {
            String p = fs.get(i);
            boolean llevaSentido = LLEVAN_EL_SENTIDO.contains(p);
            if (!llevaSentido && (p.length() < 4 || DEL_REGISTRO_FORMAL.contains(p) || AUXILIARES.contains(p))) {
                continue;
            }
            // Entre el «no» y la palabra puede haber pronombres y auxiliares:
            // «no ha cumplido», «no se han entregado», «no está al día»,
            // «todavía no ha pagado». Con solo un pronombre, esas negaciones
            // no se veían en ningún sentido (revisión del 29-09-2026).
            int j = i - 1;
            int saltos = 0;
            while (j >= 0 && saltos < 3 && (PRONOMBRES.contains(fs.get(j)) || AUXILIARES.contains(fs.get(j)))) {
                j--;
                saltos++;
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
            List.of("termino", "plazo", "tiempo", "oportun", "puntual", "cronograma", "previst", "programad"),
            // «reprogramar» va aparte: «se reprograma» es una decisión sin
            // fecha, y «se programó una nueva fecha» afirma que ya la hay. La
            // revisión ciega del 01-10-2026 lo marcó como un cambio de estado,
            // de pendiente a hecho, cuando las dos estaban en la misma familia.
            List.of("reprogram"),
            // cumplimiento y conformidad con lo pactado
            List.of("cumpl", "conform", "acordad", "pactad", "estipulad", "debida"),
            // estado: «correctamente», «adecuadamente»
            List.of("correct", "adecuad"),
            // valoraciones fuertes: cada una necesita su propia raíz en las notas
            List.of("satisfac"),
            List.of("cabalidad"),
            List.of("especificac"),
            List.of("perfect", "optim", "excelent"),
            List.of("aprob"),
            List.of("complet", "totalidad", "integr"),
            List.of("garantia"),
            List.of("multa", "sancion"),
            List.of("retras", "demora", "atras"),
            // sustento normativo: «su valor es correcto según las regulaciones
            // vigentes» convertía una factura revisada en una verificación
            // legal que nadie hizo (medición del 01-10-2026)
            List.of("regulac", "normativ", "reglament", "legislac"),
            // Revisión ciega del 01-10-2026 de las redacciones aceptadas:
            // «entregó todos los bienes» salió «sin incidencias», y «la otra
            // semana» (la próxima) salió «la semana anterior».
            List.of("incidenc", "contratiemp"),
            List.of("anterior", "pasad"));

    /**
     * «en buen estado», «buen funcionamiento», «óptimas condiciones»: una
     * calificación del estado de lo recibido. En la revisión ciega del
     * 01-10-2026 el modelo la puso por «todo completo», «está bien», «en
     * orden» o «están vigentes», que certifican otra cosa (la cantidad, la
     * corrección, la vigencia), y por «no funcionan», que rebajaba un
     * incumplimiento a «no se encuentran en buen funcionamiento».
     */
    private static final Pattern CALIFICACION_DEL_ESTADO = Pattern.compile("(?<!\\p{L})(?:buen[oa]?s?|perfect[oa]s?"
            + "|optim[oa]s?|excelentes?|adecuad[oa]s?)\\s+(?:estado|funcionamiento|condicion(?:es)?|calidad)(?!\\p{L})");

    /** Lo que en las notas ya califica bien el estado: «llegaron bien», «en buen estado», «óptimas». */
    private static final List<String> CALIFICACION_EN_LAS_NOTAS = List.of("buen", "bien", "perfect", "optim",
            "excelent", "adecuad");

    /**
     * Palabras de las notas que dicen que el plazo NO se cumplió: con ellas,
     * hablar del plazo no autoriza a la redacción a decir que se cumplió
     * («fuera del plazo» redactado como «en cumplimiento del plazo»).
     */
    private static final List<String> PLAZO_INCUMPLIDO = List.of("fuera", "vencid", "prorrog", "ampli", "retras",
            "tarde", "atras", "demora", "incumpl",
            // Reprogramar es que lo previsto no se hizo en su fecha: habla del
            // plazo, pero no autoriza a decir que se cumplió.
            "reprogram");

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
        List<String> fs = fichas(redactado);
        for (int i = 0; i < fs.size(); i++) {
            String w = fs.get(i);
            if (w.length() < 4) {
                continue;
            }
            boolean negada = tieneUnNegadorAntes(fs, i);
            for (List<String> familia : AFIRMACIONES) {
                // «no encendían correctamente», «no cumplió puntualmente»: la
                // valoración va negada, no se afirma nada nuevo. Salvo donde
                // la ausencia es lo que se afirma: «sin incidencias», «sin
                // retrasos», «no se aplicaron multas» certifican algo que las
                // notas no dicen (revisión ciega del 01-10-2026).
                if (negada && !familia.contains("incidenc") && !familia.contains("retras")
                        && !familia.contains("multa")) {
                    continue;
                }
                boolean enLaRedaccion = familia.stream().anyMatch(w::startsWith);
                if (enLaRedaccion && !familiaEnLasNotas(familia, deNotas)) {
                    return false;
                }
            }
        }
        return !CALIFICACION_DEL_ESTADO.matcher(normalizar(redactado)).find()
                || palabrasDeTresLetrasOMas(notas).stream()
                        .anyMatch(v -> CALIFICACION_EN_LAS_NOTAS.stream().anyMatch(v::startsWith));
    }

    /** Las palabras del texto, incluidas las cortas que {@code palabrasLargas} deja fuera («bien», «buen»). */
    private static Set<String> palabrasDeTresLetrasOMas(String texto) {
        Set<String> r = new HashSet<>();
        for (String p : normalizar(texto == null ? "" : texto).split("[^a-z]+")) {
            if (p.length() >= 3) {
                r.add(p);
            }
        }
        return r;
    }

    /**
     * Lo que en las notas ya dice lo mismo que «correctamente» o
     * «adecuadamente»: «llegaron bien», «en buen estado», «todo en orden»,
     * «funcionan todos». Sin esto, «funcionan correctamente» por «funcionan
     * todos» se descartaba.
     */
    private static final List<String> ESTADO_EN_LAS_NOTAS = List.of("bien", "buen", "orden", "funcion", "debida",
            "normal", "correct", "adecuad");

    private static boolean familiaEnLasNotas(List<String> familia, Set<String> deNotas) {
        if (familia.contains("correct")) {
            return deNotas.stream().anyMatch(v -> ESTADO_EN_LAS_NOTAS.stream().anyMatch(v::startsWith));
        }
        boolean esta = deNotas.stream().anyMatch(v -> familia.stream().anyMatch(v::startsWith));
        // «Cumplir el plazo» es hablar del plazo: si las notas hablan del
        // plazo, la redacción puede decir que se cumple. Pero no si las notas
        // dicen que se venció, se prorrogó o hubo retraso.
        if (!esta && familia.contains("cumpl")) {
            boolean hablanDelPlazo = deNotas.stream()
                    .anyMatch(v -> AFIRMACIONES.getFirst().stream().anyMatch(v::startsWith));
            boolean incumplido = deNotas.stream().anyMatch(v -> PLAZO_INCUMPLIDO.stream().anyMatch(v::startsWith));
            esta = hablanDelPlazo && !incumplido;
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
        // En inglés la escritura es la misma y ninguna comprobación basada en
        // el español lo veía: qwen respondió «…Pedro Gómez; however, the
        // portable computer loaders are yet to arrive» en 2 de 44 redacciones
        // (revisión del 29-09-2026). Palabras funcionales del inglés que no son
        // palabras del español y no estaban en las notas.
        Set<String> deLasNotas = new HashSet<>(fichas(notas));
        for (String f : fichas(redactado)) {
            if (PALABRAS_DEL_INGLES.contains(f) && !deLasNotas.contains(f)) {
                return true;
            }
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

    // «being» salió en vivo el 29-09-2026: «…en buen estado, being la
    // recepción confirmada por el almacenista». Ninguna de estas es una
    // palabra del español.
    private static final Set<String> PALABRAS_DEL_INGLES = Set.of("the", "and", "of", "is", "are", "was", "were",
            "however", "yet", "still", "with", "have", "been", "will", "which", "this", "these", "those", "from",
            "that", "for", "by", "to", "their", "they", "it", "its", "has", "not", "being", "had", "would", "should",
            "could", "there", "where", "while", "when", "into", "also", "such", "only", "some", "any", "all", "what",
            "who", "than", "then", "because", "after", "before", "without", "within", "between", "our", "your",
            "his", "her", "them", "we", "you", "upon", "whose");

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

    /** «m2», «m²», «cm2», «km²»: la unidad, con su exponente pegado. */
    private static final Pattern UNIDAD_CON_EXPONENTE = Pattern.compile("(?<!\\p{L})(k|c)?m(2|3|²|³)(?![\\p{L}\\d])");

    /** «tres de la tarde», «3 y media de la mañana», «la una de la tarde». */
    private static final Pattern HORA_EN_PALABRAS = Pattern.compile("(?<![\\p{L}\\d])(\\d{1,2}|una|dos|tres|cuatro"
            + "|cinco|seis|siete|ocho|nueve|diez|once|doce)(?:\\s+y\\s+(media|cuarto))?\\s+de\\s+la\\s+"
            + "(manana|tarde|noche)(?!\\p{L})");

    private static final List<String> HORAS_EN_LETRAS = List.of("", "una", "dos", "tres", "cuatro", "cinco", "seis",
            "siete", "ocho", "nueve", "diez", "once", "doce");

    /**
     * La normalización de las comprobaciones de cifras, fechas y horas, con
     * las equivalencias de escritura que no cambian lo que se dice. En la
     * medición con qwen2.5:7b del 01-10-2026 se descartaban redacciones
     * fieles por escribir lo mismo de otra forma: «$4'500.000» (los millones
     * con apóstrofo, como se escriben a mano) leído como un 4 y un 500.000,
     * el «2» de «120 m2» tomado por una cantidad que se perdía en «120 metros
     * cuadrados», y «a las 3 pm» redactado «a las tres de la tarde». Una hora
     * o una unidad distinta sigue viéndose: «las cuatro de la tarde» queda
     * como «4 pm» y no es la hora de las notas.
     */
    static String normalizarCifras(String s) {
        String t = normalizar(s).replaceAll("(?<=\\d)['’](?=\\d{3}(?!\\d))", ".");
        Matcher unidad = UNIDAD_CON_EXPONENTE.matcher(t);
        StringBuilder conUnidades = new StringBuilder();
        while (unidad.find()) {
            String prefijo = unidad.group(1) == null ? "" : unidad.group(1).equals("k") ? "kilo" : "centi";
            String potencia = unidad.group(2).equals("2") || unidad.group(2).equals("²") ? "cuadrados" : "cubicos";
            unidad.appendReplacement(conUnidades, " " + prefijo + "metros " + potencia);
        }
        unidad.appendTail(conUnidades);
        Matcher hora = HORA_EN_PALABRAS.matcher(conUnidades.toString());
        StringBuilder conHoras = new StringBuilder();
        while (hora.find()) {
            int h = hora.group(1).matches("\\d+") ? Integer.parseInt(hora.group(1))
                    : HORAS_EN_LETRAS.indexOf(hora.group(1));
            String minutos = hora.group(2) == null ? "" : hora.group(2).equals("media") ? ":30" : ":15";
            String meridiano = hora.group(3).equals("manana") ? " am" : " pm";
            hora.appendReplacement(conHoras, h + minutos + meridiano);
        }
        hora.appendTail(conHoras);
        return conHoras.toString();
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
