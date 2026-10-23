package co.sena.sicot.service;

import co.sena.sicot.entity.enums.RolDeContacto;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Los correos que trae el texto de un documento, con una sugerencia de si son
 * de SST o de ambiental.
 *
 * <h2>Para qué</h2>
 * El paso 3 de la lista de chequeo de Alex Zapata dice que la información de
 * contacto de los profesionales SST y ambiental «se debe encontrar en el
 * estudio previo». Ese documento pasa de los 5 MB y el supervisor tenía que
 * buscar los correos a ojo. Aquí se sacan del texto que el supervisor carga.
 *
 * <h2>Qué decide y qué no</h2>
 * Un correo electrónico tiene forma fija, así que encontrarlos no necesita
 * modelo ni puede inventar uno: lo que se devuelve está escrito en el
 * documento. Lo que sí es una suposición es el rol, y por eso es una
 * <em>sugerencia</em>: se toma la mención de SST o de ambiental más cercana
 * antes del correo, en la misma zona del texto. Sin mención cerca, no se
 * sugiere nada. Tampoco se adivina el nombre de la persona: se devuelve el
 * fragmento donde aparece el correo para que el supervisor lo lea.
 *
 * <p>El formato del estudio previo del Centro no se conoce todavía (el del
 * Lote 4 no se puede bajar del SECOP: responde 403), así que el criterio es
 * deliberadamente genérico y no depende de la posición de una tabla.
 */
final class ContactosEnUnDocumento {

    /** Cuántos correos se devuelven como mucho: un estudio previo trae unos pocos. */
    static final int MAXIMO = 30;

    /** Hasta dónde se busca hacia atrás la mención del rol: un renglón o dos de tabla. */
    private static final int VENTANA_ANTES = 160;
    private static final int VENTANA_DESPUES = 60;

    private static final Pattern CORREO = Pattern.compile(
            "[A-Za-z0-9][A-Za-z0-9._%+-]*@[A-Za-z0-9-]+(?:\\.[A-Za-z0-9-]+)*\\.[A-Za-z]{2,}");

    /**
     * Cómo se nombra cada rol en los documentos de contratación. Se busca sobre
     * el texto sin tildes y en minúsculas. «sst» va con bordes de palabra para
     * no confundirse con un trozo de otra.
     */
    private static final Pattern MENCION_SST = Pattern.compile(
            "\\bsst\\b|\\bsg-?sst\\b|seguridad y salud|salud ocupacional");
    private static final Pattern MENCION_AMBIENTAL = Pattern.compile(
            "ambiental|medio ambiente|gestion ambiental");

    private ContactosEnUnDocumento() {
    }

    /**
     * @param correo   en minúsculas, como se guardará
     * @param rol      la sugerencia, o {@code null} si no hay mención cerca
     * @param contexto el fragmento del documento alrededor del correo, en una
     *                 sola línea, para que el supervisor reconozca de quién es
     */
    record Encontrado(String correo, RolDeContacto rol, String contexto) {
    }

    static List<Encontrado> leer(String texto) {
        if (texto == null || texto.isBlank()) {
            return List.of();
        }
        // Los correos se buscan en el texto tal cual (son ASCII) y el fragmento
        // se muestra tal cual, con sus tildes y mayúsculas, porque es donde el
        // supervisor lee el nombre de la persona. Solo la búsqueda de la
        // mención del rol trabaja sobre una copia sin tildes ni mayúsculas.
        Map<String, Encontrado> unicos = new LinkedHashMap<>();
        Matcher m = CORREO.matcher(texto);
        // La mención del rol se busca solo después del correo anterior: en
        // «SST: a@x.co · Coordinador: b@x.co» la palabra SST es de a@, y sin
        // este tope se le sugería también a b@.
        int finDelAnterior = 0;
        while (m.find() && unicos.size() < MAXIMO) {
            String correo = recortarPuntoFinal(m.group()).toLowerCase(Locale.ROOT);
            int desde = Math.max(0, m.start() - VENTANA_ANTES);
            int desdeElRol = Math.max(desde, finDelAnterior);
            finDelAnterior = m.end();
            if (unicos.containsKey(correo)) {
                continue;
            }
            int hasta = Math.min(texto.length(), m.end() + VENTANA_DESPUES);
            String antes = sinTildes(texto.substring(desdeElRol, m.start())).toLowerCase(Locale.ROOT);
            unicos.put(correo, new Encontrado(correo, rolMasCercano(antes),
                    unaLinea(texto.substring(desde, hasta))));
        }
        // Primero los que tienen rol sugerido: son los que el supervisor buscaba.
        List<Encontrado> lista = new ArrayList<>(unicos.values());
        lista.sort(Comparator.comparing(e -> e.rol() == null));
        return lista;
    }

    /**
     * La mención que queda más cerca del correo, mirando hacia atrás. En una
     * tabla «Profesional SST … sst@… · Profesional ambiental … amb@…» el
     * segundo correo tiene las dos menciones antes; la que le corresponde es la
     * última.
     */
    static RolDeContacto rolMasCercano(String antes) {
        int sst = ultimaPosicion(MENCION_SST, antes);
        int ambiental = ultimaPosicion(MENCION_AMBIENTAL, antes);
        if (sst < 0 && ambiental < 0) {
            return null;
        }
        return sst > ambiental ? RolDeContacto.SST : RolDeContacto.AMBIENTAL;
    }

    private static int ultimaPosicion(Pattern patron, String texto) {
        Matcher m = patron.matcher(texto);
        int ultima = -1;
        while (m.find()) {
            ultima = m.start();
        }
        return ultima;
    }

    /** Un correo al final de una oración arrastra el punto: «…escriba a sst@sena.edu.co.» */
    private static String recortarPuntoFinal(String correo) {
        String c = correo;
        while (c.endsWith(".") || c.endsWith("-")) {
            c = c.substring(0, c.length() - 1);
        }
        return c;
    }

    private static String unaLinea(String fragmento) {
        return fragmento.replaceAll("\\s+", " ").strip();
    }

    private static String sinTildes(String v) {
        return Normalizer.normalize(v, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    }
}
