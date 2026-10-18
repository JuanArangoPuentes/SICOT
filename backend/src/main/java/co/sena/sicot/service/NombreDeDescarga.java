package co.sena.sicot.service;

import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.Locale;
import java.util.Map;

/**
 * El nombre con que se guarda un archivo que se descarga de SICOT, y la
 * cabecera {@code Content-Disposition} que lo lleva.
 *
 * <h2>Los tres defectos que corrige (auditoría del 28-09-2026)</h2>
 * <ol>
 *   <li><b>Sin extensión.</b> Los documentos generados se guardan con nombre
 *   «Acta de Inicio — CO1.PCCNTR.7788991»: el sistema tomaba «.7788991» por
 *   extensión y el archivo no abría con el lector de PDF. La extensión se
 *   añade al descargar, según el tipo de contenido, y no se guarda en la
 *   columna: la firma compara nombres exactos con los documentos ya firmados.</li>
 *   <li><b>Una palabra codificada en {@code filename=}.</b> Spring escribía
 *   {@code filename="=?UTF-8?Q?…?="} hasta para nombres ASCII, y los clientes
 *   que solo leen ese parámetro (curl, gestores de descarga) guardaban el
 *   texto codificado literal. Ahora {@code filename=} lleva una versión ASCII
 *   legible y {@code filename*=} el nombre completo en UTF-8 (RFC 6266).</li>
 *   <li><b>Caracteres que Windows no admite</b> ({@code \ / : * ? " < > |}),
 *   posibles con un número de contrato como «CTMA-045/2026». Se cambian por
 *   «_» en vez de dejar que cada navegador haga lo suyo.</li>
 * </ol>
 */
public final class NombreDeDescarga {

    private static final Map<String, String> EXTENSIONES = Map.ofEntries(
            Map.entry("application/pdf", "pdf"),
            Map.entry("image/jpeg", "jpg"),
            Map.entry("image/png", "png"),
            Map.entry("image/webp", "webp"),
            Map.entry("image/heic", "heic"),
            Map.entry("text/csv", "csv"),
            Map.entry("text/plain", "txt"),
            Map.entry("application/vnd.openxmlformats-officedocument.wordprocessingml.document", "docx"),
            Map.entry("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "xlsx"),
            Map.entry("application/msword", "doc"),
            Map.entry("application/vnd.ms-excel", "xls"));

    private NombreDeDescarga() {
    }

    /** El nombre seguro para Windows y con la extensión que corresponde a su contenido. */
    public static String nombre(String nombre, String contentType) {
        String base = seguro(nombre == null || nombre.isBlank() ? "documento" : nombre.strip());
        String extension = contentType == null ? null
                : EXTENSIONES.get(contentType.toLowerCase(Locale.ROOT).split(";")[0].strip());
        if (extension == null) {
            return base;
        }
        String minusculas = base.toLowerCase(Locale.ROOT);
        boolean yaLaTiene = minusculas.endsWith("." + extension)
                || (extension.equals("jpg") && minusculas.endsWith(".jpeg"));
        return yaLaTiene ? base : base + "." + extension;
    }

    /** Valor completo de la cabecera {@code Content-Disposition: attachment}. */
    public static String cabecera(String nombre, String contentType) {
        String completo = nombre(nombre, contentType);
        return "attachment; filename=\"" + ascii(completo) + "\"; filename*=UTF-8''" + porcentaje(completo);
    }

    private static String seguro(String nombre) {
        String s = nombre.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_").replaceAll("\\s+", " ").strip();
        // Windows no admite nombres que terminen en punto o espacio.
        s = s.replaceAll("[. ]+$", "");
        return s.isEmpty() ? "documento" : s;
    }

    /** «Acta de Inicio — CO1.PCCNTR.1» → «Acta de Inicio - CO1.PCCNTR.1»; «Evidencia fotográfica» → «Evidencia fotografica». */
    static String ascii(String nombre) {
        String sinRayas = nombre.replace('—', '-').replace('–', '-').replace('“', '"').replace('”', '"')
                .replace('‘', '\'').replace('’', '\'');
        String sinTildes = Normalizer.normalize(sinRayas, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        StringBuilder sb = new StringBuilder();
        for (char c : sinTildes.toCharArray()) {
            sb.append(c >= 0x20 && c < 0x7F && c != '"' && c != '\\' ? c : '_');
        }
        return sb.toString();
    }

    /** Codificación RFC 5987 del nombre en UTF-8. */
    static String porcentaje(String nombre) {
        StringBuilder sb = new StringBuilder();
        for (byte b : nombre.getBytes(StandardCharsets.UTF_8)) {
            int c = b & 0xFF;
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')
                    || "!#$&+-.^_`|~".indexOf(c) >= 0) {
                sb.append((char) c);
            } else {
                sb.append('%').append(String.format("%02X", c));
            }
        }
        return sb.toString();
    }
}
