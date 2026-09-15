package co.sena.sicot.ia;

import co.sena.sicot.dto.ia.ExtraccionContratoResponse;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Saca del texto de un contrato los campos que tienen forma regular, sin pasar
 * por el modelo de IA.
 *
 * <h2>Por qué existe</h2>
 * Es la misma lección que ADR-008 dejó escrita al retirar el modelo del motor de
 * automatizaciones —no pedirle al modelo que sostenga hechos que el código ya
 * puede calcular— aplicada ahora a la extracción de contratos.
 *
 * <p>La medición del 14 de septiembre de 2026 la justifica con números. Sobre un
 * contrato de suministro del SENA con los once campos conocidos de antemano:
 *
 * <table border="1">
 *   <caption>Aciertos y tiempo por tamaño de modelo</caption>
 *   <tr><th>Modelo</th><th>Tiempo</th><th>Aciertos</th></tr>
 *   <tr><td>qwen2.5:1.5b</td><td>48,5 s</td><td>2 de 11</td></tr>
 *   <tr><td>qwen2.5:3b</td><td>105,7 s</td><td>8 de 11</td></tr>
 *   <tr><td>qwen2.5:7b</td><td>136,7 s</td><td>10 de 11</td></tr>
 * </table>
 *
 * <p>Lo revelador no es la columna de aciertos sino <b>cuáles</b> fallaban. El
 * modelo de 3B no consiguió sacar el valor del contrato —una cifra que está
 * escrita entre paréntesis con su signo de pesos— y el de 7B devolvió el nombre
 * del representante legal con la cédula pegada detrás. Son campos con formato
 * fijo: pedirle a un modelo de lenguaje que los copie es gastar dos minutos de
 * CPU en algo que una expresión regular resuelve en menos de un milisegundo y
 * sin equivocarse.
 *
 * <h2>Qué NO hace, a propósito</h2>
 * No toca {@code objeto} ni {@code tipoContrato}. El objeto es prosa que hay que
 * resumir y el tipo es una clasificación: ahí el modelo sí aporta algo que el
 * código no tiene. Esos dos siguen siendo suyos, y al quedarse solo con ellos el
 * prompt se acorta y el trabajo cabe en un modelo mucho más pequeño — que es el
 * objetivo de fondo del proyecto.
 *
 * <h2>Dos formas de escribir los mismos datos</h2>
 * El contrato los redacta en prosa; el acta de inicio (GCCON-F-018) y la
 * notificación al supervisor los ponen en una tabla de etiqueta y valor. Hay
 * una familia de patrones para cada forma, y la de prosa corre primero — ver el
 * comentario que separa las dos, más abajo. Importa porque Gestión no carga el
 * contrato: carga el acta y la notificación.
 *
 * <h2>Cuando un patrón no aparece, devuelve null</h2>
 * Nunca adivina. Un campo que no está en el documento vuelve vacío y lo rellena
 * después el modelo o la persona, igual que hasta ahora. Esta clase solo puede
 * mejorar el resultado, nunca empeorarlo.
 */
@Component
public class ExtraccionDeterminista {

    /**
     * Número de proceso de SECOP II. El prefijo es estable en toda la
     * contratación estatal colombiana, que es lo que lo hace fiable aquí.
     */
    private static final Pattern NUMERO_CONTRATO =
            Pattern.compile("\\bCO1\\.PCCNTR\\.[A-Z0-9]+");

    /**
     * NIT del contratista.
     *
     * <p>Se ancla en «sociedad … identificada con NIT» y no se coge el primer
     * NIT del documento a propósito: en estos contratos aparece antes el del
     * propio SENA (899.999.034-1), así que el primero que se cruce es
     * sistemáticamente el equivocado.
     */
    private static final Pattern CONTRATISTA_Y_NIT = Pattern.compile(
            "sociedad\\s+(.{5,90}?),?\\s+identificad[ao]\\s+con\\s+NIT\\s+([\\d.\\-]+)",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private static final Pattern REPRESENTANTE = Pattern.compile(
            "representad[ao]\\s+legalmente\\s+por\\s+([\\p{Lu}ÁÉÍÓÚÑ\\s]{6,60}?)\\s*,",
            Pattern.CASE_INSENSITIVE);

    /**
     * Valor del contrato. Se busca la cifra en dígitos, no el importe en letras:
     * la forma canónica en estos documentos es «… PESOS ($184.750.000) M/CTE».
     */
    private static final Pattern VALOR = Pattern.compile("\\$\\s?([\\d.]{5,20})");

    private static final Pattern VIGENCIA = Pattern.compile(
            "desde\\s+el\\s+(.{5,45}?)\\s+hasta\\s+el\\s+(.{5,45}?)[,.]",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    /**
     * Registro presupuestal. El «No.» va como opcional a propósito: el contrato
     * escribe «Registro Presupuestal No. RP-2026-00471», pero el acta de inicio
     * escribe «Número y fecha del registro presupuestal 56725 del 17 de junio
     * de 2025», sin numeral. Exigirlo dejaba el campo vacío en toda acta.
     *
     * <p>Al soltar el numeral el patrón puede engancharse con una palabra
     * cualquiera («registro presupuestal del contrato» → «del»), así que quien
     * lo usa exige además que lo capturado traiga algún dígito.
     */
    private static final Pattern REGISTRO_PRESUPUESTAL = Pattern.compile(
            "registro\\s+presupuestal\\s+(?:n[o°]\\.?\\s*)?([A-Z0-9\\-]+)",
            Pattern.CASE_INSENSITIVE);

    /**
     * Lugar de ejecución, hasta el punto que cierra la frase.
     *
     * <p>Ese punto no es «cualquier punto»: una dirección real trae puntos de
     * abreviatura antes del final. «CALLE 63 NO. 58 B 03, BARRIO CALATRAVA-
     * ITAGÜÍ, ANTIOQUIA.» se cortaba en «CALLE 63 NO», porque el primer punto
     * del texto es el de «NO.». El anticipado descarta un punto seguido de
     * dígitos —la forma que toma toda abreviatura de numeral— y deja pasar el
     * que sí termina la frase.
     */
    private static final Pattern LUGAR = Pattern.compile(
            "LUGAR\\s+DE\\s+EJECUCI[OÓ]N:?\\s*(.{10,200}?)\\.(?!\\s*\\d)",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    /** «02 de febrero de 2026» → 2026-02-02. */
    private static final Pattern FECHA_EN_LETRAS = Pattern.compile(
            "(\\d{1,2})\\s+de\\s+(\\p{L}+)\\s+de\\s+(\\d{4})", Pattern.CASE_INSENSITIVE);

    /**
     * «11/08/2025» → 2025-08-11. Día primero, que es como se escribe la fecha
     * en Colombia; la notificación al supervisor usa esta forma y no la de
     * letras.
     */
    private static final Pattern FECHA_NUMERICA = Pattern.compile(
            "(\\d{1,2})[/\\-](\\d{1,2})[/\\-](\\d{4})");

    // ── Segunda familia: el documento como formulario, no como prosa ────────
    //
    // Los patrones de arriba leen el CONTRATO, que redacta sus datos en prosa
    // («la sociedad X, identificada con NIT Y, representada legalmente por Z»).
    // Pero Gestión no carga el contrato: carga el acta de inicio (GCCON-F-018)
    // y la notificación al supervisor, y esos dos ponen exactamente los mismos
    // datos en una tabla de etiqueta y valor:
    //
    //     CONTRATISTA           EVENTOS SUPERNOVA S.A.S.
    //     CC o NIT              900.478.852-5
    //     REPRESENTANTE LEGAL   CARLOS MARIO REINA MEJÍA
    //
    // Sin estos patrones, de un acta real solo salían el número de contrato y
    // el valor; proveedor, NIT, representante y fechas volvían vacíos aunque
    // estuvieran escritos en el documento. Y el modelo no podía taparlo: el
    // prompt le dice expresamente que esos campos «ya se extrajeron por otro
    // medio», así que nadie los llenaba.
    //
    // Van como respaldo, no como reemplazo: primero corre la prosa, y solo si
    // no encontró nada se mira la etiqueta. Importa por el NIT — el patrón en
    // prosa está escrito para saltarse el NIT del propio SENA, y ese cuidado se
    // perdería si la etiqueta ganara.

    /**
     * Fila «CONTRATISTA …» de la tabla del acta.
     *
     * <p>Distingue mayúsculas a propósito. La misma acta cierra con «Elaboró:
     * … Contratista Abogada Apoyo Bienes y Servicios», que es el cargo de quien
     * redactó el documento, no el proveedor. Sin distinguir mayúsculas, el
     * proveedor de media contratación del SENA sería «Abogada Apoyo Bienes y
     * Servicios».
     */
    private static final Pattern CONTRATISTA_ETIQUETA = Pattern.compile(
            "^[ \\t]*CONTRATISTA[ \\t]+(\\S.*?)[ \\t]*$", Pattern.MULTILINE);

    /** Fila «CC o NIT …», y también el «NIT. 900.478.852-5» del pie de firma. */
    private static final Pattern NIT_ETIQUETA = Pattern.compile(
            "^[ \\t]*(?:CC\\s+o\\s+)?NIT[.:]?[ \\t]*([\\d][\\d.\\-]{6,20})[ \\t]*$",
            Pattern.MULTILINE | Pattern.CASE_INSENSITIVE);

    /**
     * Fila «REPRESENTANTE LEGAL …» de la tabla.
     *
     * <p>También distingue mayúsculas, y por un motivo más fino que el de
     * arriba: el cuerpo del acta dice «en calidad de representante legal de
     * EVENTOS SUPERNOVA S.A.S.», donde lo que sigue es la <b>empresa</b>. Leer
     * esa frase daría como representante legal el nombre del proveedor. La fila
     * en mayúsculas de la tabla es la que trae a la persona.
     */
    private static final Pattern REPRESENTANTE_ETIQUETA = Pattern.compile(
            "^[ \\t]*REPRESENTANTE[ \\t]+LEGAL[ \\t]+(\\S.*?)[ \\t]*$", Pattern.MULTILINE);

    /**
     * «Fecha de inicio 17 de junio de 2025» (acta) y «• Fecha de inicio:
     * 11/08/2025» (notificación). El prefijo suelto al comienzo deja pasar la
     * viñeta de la notificación.
     */
    private static final Pattern FECHA_INICIO_ETIQUETA = Pattern.compile(
            "^[^\\n]*?Fecha\\s+de\\s+inicio[ \\t]*:?[ \\t]*(.{5,60}?)[ \\t]*$",
            Pattern.MULTILINE | Pattern.CASE_INSENSITIVE);

    /** La pareja de {@link #FECHA_INICIO_ETIQUETA} al otro extremo del plazo. */
    private static final Pattern FECHA_FIN_ETIQUETA = Pattern.compile(
            "^[^\\n]*?Fecha\\s+de\\s+(?:terminaci[oó]n|finalizaci[oó]n)[ \\t]*:?[ \\t]*(.{5,60}?)[ \\t]*$",
            Pattern.MULTILINE | Pattern.CASE_INSENSITIVE);

    private static final Map<String, String> MESES = Map.ofEntries(
            Map.entry("enero", "01"), Map.entry("febrero", "02"), Map.entry("marzo", "03"),
            Map.entry("abril", "04"), Map.entry("mayo", "05"), Map.entry("junio", "06"),
            Map.entry("julio", "07"), Map.entry("agosto", "08"),
            Map.entry("septiembre", "09"), Map.entry("setiembre", "09"),
            Map.entry("octubre", "10"), Map.entry("noviembre", "11"),
            Map.entry("diciembre", "12"));

    /**
     * Extrae lo que se pueda del texto. Los campos que no aparezcan vuelven a
     * {@code null}; {@code objeto} y {@code tipoContrato} vuelven siempre a
     * {@code null} porque son trabajo del modelo.
     */
    public ExtraccionContratoResponse extraer(String texto) {
        if (texto == null || texto.isBlank()) {
            return vacia();
        }

        String proveedor = null;
        String nit = null;
        Matcher m = CONTRATISTA_Y_NIT.matcher(texto);
        if (m.find()) {
            proveedor = normalizarEspacios(m.group(1));
            nit = recortarPuntuacionFinal(m.group(2));
        }
        // Respaldo de formulario, solo para lo que la prosa no trajo.
        if (proveedor == null) {
            proveedor = normalizarEspacios(primerGrupo(CONTRATISTA_ETIQUETA, texto, 1));
        }
        if (nit == null) {
            nit = recortarPuntuacionFinal(primerGrupo(NIT_ETIQUETA, texto, 1));
        }

        String representante = normalizarEspacios(primerGrupo(REPRESENTANTE, texto, 1));
        if (representante == null) {
            representante = normalizarEspacios(primerGrupo(REPRESENTANTE_ETIQUETA, texto, 1));
        }

        String inicio = null;
        String fin = null;
        m = VIGENCIA.matcher(texto);
        if (m.find()) {
            inicio = aIso(m.group(1));
            fin = aIso(m.group(2));
        }
        // Las dos fechas se respaldan por separado: un documento puede traer
        // una y no la otra, y media vigencia sigue siendo mejor que ninguna.
        if (inicio == null) {
            inicio = aIso(primerGrupo(FECHA_INICIO_ETIQUETA, texto, 1));
        }
        if (fin == null) {
            fin = aIso(primerGrupo(FECHA_FIN_ETIQUETA, texto, 1));
        }

        return new ExtraccionContratoResponse(
                primerGrupo(NUMERO_CONTRATO, texto, 0),
                null, // objeto — es prosa, lo resume el modelo
                proveedor,
                nit,
                representante,
                soloDigitos(primerGrupo(VALOR, texto, 1)),
                inicio,
                fin,
                normalizarEspacios(primerGrupo(LUGAR, texto, 1)),
                conAlgunDigito(primerGrupo(REGISTRO_PRESUPUESTAL, texto, 1)),
                null // tipoContrato — es clasificación, la hace el modelo
        );
    }

    /** True si ya no queda nada útil que preguntarle al modelo. */
    public boolean estaCompleta(ExtraccionContratoResponse r) {
        return r.objeto() != null && !r.objeto().isBlank()
                && r.tipoContrato() != null && !r.tipoContrato().isBlank();
    }

    private static ExtraccionContratoResponse vacia() {
        return new ExtraccionContratoResponse(null, null, null, null, null, null,
                null, null, null, null, null);
    }

    private static String primerGrupo(Pattern patron, String texto, int grupo) {
        Matcher m = patron.matcher(texto);
        return m.find() ? m.group(grupo) : null;
    }

    private static String normalizarEspacios(String v) {
        if (v == null) {
            return null;
        }
        String limpio = v.replaceAll("\\s+", " ").trim();
        return limpio.isEmpty() ? null : limpio;
    }

    private static String recortarPuntuacionFinal(String v) {
        return v == null ? null : v.replaceAll("[.,]+$", "");
    }

    /**
     * Quita los puntos de miles y deja el entero plano que espera el resto del
     * sistema. «184.750.000» → «184750000».
     */
    private static String soloDigitos(String v) {
        if (v == null) {
            return null;
        }
        String digitos = v.replaceAll("[^0-9]", "");
        return digitos.isEmpty() ? null : digitos;
    }

    /**
     * Descarta lo capturado si no trae ningún dígito. Solo lo usa el registro
     * presupuestal: al volverse opcional su «No.», el patrón podría quedarse
     * con la palabra que siguiera («del», «que»), y un campo vacío es mejor
     * que un campo con basura que la persona tiene que borrar a mano.
     */
    private static String conAlgunDigito(String v) {
        return v != null && v.matches(".*\\d.*") ? v : null;
    }

    private static String aIso(String fragmento) {
        if (fragmento == null) {
            return null;
        }
        Matcher m = FECHA_EN_LETRAS.matcher(fragmento);
        if (m.find()) {
            String mes = MESES.get(m.group(2).toLowerCase(Locale.ROOT));
            if (mes != null) {
                return "%s-%s-%02d".formatted(m.group(3), mes, Integer.parseInt(m.group(1)));
            }
        }
        m = FECHA_NUMERICA.matcher(fragmento);
        if (m.find()) {
            int dia = Integer.parseInt(m.group(1));
            int mes = Integer.parseInt(m.group(2));
            // Una fecha con mes 13 no es una fecha en otro formato: es basura
            // que se coló en la línea. Se descarta en vez de normalizarla.
            if (dia >= 1 && dia <= 31 && mes >= 1 && mes <= 12) {
                return "%s-%02d-%02d".formatted(m.group(3), mes, dia);
            }
        }
        return null;
    }
}
