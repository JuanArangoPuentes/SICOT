package co.sena.sicot.ia;

import co.sena.sicot.dto.ia.ExtraccionContratoResponse;
import co.sena.sicot.exception.BusinessException;
import co.sena.sicot.service.ArchivoValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.List;

/**
 * Lee los documentos reales (Acta de Inicio, notificación, formatos, etc.)
 * que carga Gestión al asignar un contrato y propone los campos del
 * formulario de creación de contrato. No inventa el proceso institucional:
 * solo extrae texto ya presente en los documentos cargados. Puede recibir
 * varios archivos a la vez (el correo real de asignación trae más de uno);
 * se extrae cada uno por separado y se combinan los resultados — el primer
 * valor no nulo encontrado gana por campo, sin sobrescribir con nulos de
 * documentos posteriores que no traían ese dato (p. ej. el manual de
 * supervisión no tiene NIT del contratista, pero el Acta de Inicio sí).
 */
@Service
public class ExtraccionContratoService {

    private static final Logger log = LoggerFactory.getLogger(ExtraccionContratoService.class);

    private static final int MAX_ARCHIVOS = 6;

    private final PdfTextExtractor pdfTextExtractor;
    private final OllamaClient ollamaClient;
    private final ObjectMapper objectMapper;
    private final ArchivoValidator archivoValidator;

    /**
     * Techo de tiempo para la petición COMPLETA, no para cada llamada.
     *
     * {@code sicot.ia.timeout-seconds} acota una sola llamada a Ollama, pero
     * aquí se hacen hasta {@value #MAX_ARCHIVOS} en serie: con el valor por
     * defecto de entonces (900 s) una única petición podía retener un hilo de Tomcat hasta
     * 90 minutos. Con unas pocas simultáneas se agota el pool de hilos y deja
     * de responder toda la API, no solo la extracción.
     *
     * En hardware sin GPU una extracción real ronda los 35-40 s por archivo
     * (≈4 min para los 6), así que este presupuesto no estorba el uso normal:
     * solo corta el caso patológico.
     */
    @Value("${sicot.ia.presupuesto-extraccion-seconds:900}")
    private long presupuestoSegundos;

    private final ExtraccionDeterminista extraccionDeterminista;

    private final DocxTextExtractor docxTextExtractor;

    /** Tipos del formulario de Gestión. Lo que el modelo proponga fuera de ellos se descarta. */
    static final List<String> TIPOS = List.of("Suministro de Bienes", "Compraventa", "Servicios", "Obras",
            "Arrendamiento");

    public ExtraccionContratoService(PdfTextExtractor pdfTextExtractor, OllamaClient ollamaClient,
                                      ObjectMapper objectMapper, ArchivoValidator archivoValidator,
                                      ExtraccionDeterminista extraccionDeterminista,
                                      DocxTextExtractor docxTextExtractor) {
        this.pdfTextExtractor = pdfTextExtractor;
        this.ollamaClient = ollamaClient;
        this.objectMapper = objectMapper;
        this.archivoValidator = archivoValidator;
        this.extraccionDeterminista = extraccionDeterminista;
        this.docxTextExtractor = docxTextExtractor;
    }

    public ExtraccionContratoResponse extraer(List<MultipartFile> archivos) {
        List<MultipartFile> validos = archivos == null ? List.of() : archivos.stream().filter(a -> a != null && !a.isEmpty()).toList();
        if (validos.isEmpty()) {
            throw new BusinessException("Debe cargar al menos un archivo para analizar.");
        }
        if (validos.size() > MAX_ARCHIVOS) {
            throw new BusinessException("Cargue como máximo " + MAX_ARCHIVOS + " documentos a la vez.");
        }

        // Cada archivo pasa por la MISMA validación que usan DocumentoService y
        // FormatoDocumentalService: tamaño máximo y tipo real por bytes mágicos
        // (no por la extensión ni por el Content-Type que manda el cliente).
        // Antes no había ninguna: seis archivos de 500 MB, o un ejecutable
        // renombrado a .pdf, llegaban a memoria y a PDFBox sin que nadie lo
        // comprobara. Si un archivo no supera la validación se rechaza la
        // petición completa con un 400 claro, en vez de analizar unos e
        // ignorar otros en silencio.
        for (MultipartFile archivo : validos) {
            archivoValidator.validarTamanio(archivo);
            archivoValidator.tipoDeArchivo(archivo);
        }

        long limite = System.nanoTime() + Duration.ofSeconds(presupuestoSegundos).toNanos();
        int procesados = 0;
        List<String> sinTexto = new java.util.ArrayList<>();

        ExtraccionContratoResponse resultado = new ExtraccionContratoResponse(
                null, null, null, null, null, null, null, null, null, null, null);
        for (MultipartFile archivo : validos) {
            // Se comprueba ANTES de cada archivo, no después: si ya se agotó el
            // presupuesto, no se empieza otra llamada que podría durar otros 15
            // minutos. Se devuelve lo extraído hasta aquí en vez de fallar — es
            // información real y útil — y se avisa en el log de cuántos archivos
            // quedaron sin analizar, para no dar a entender que se leyeron todos.
            if (procesados > 0 && System.nanoTime() > limite) {
                log.warn("Presupuesto de {} s agotado tras {} de {} archivos; los restantes no se analizaron.",
                        presupuestoSegundos, procesados, validos.size());
                break;
            }
            ExtraccionContratoResponse deEsteArchivo = extraerDeUnArchivo(archivo);
            if (deEsteArchivo == null) {
                sinTexto.add(archivo.getOriginalFilename());
            } else {
                resultado = combinar(resultado, deEsteArchivo);
            }
            procesados++;
        }
        // Si no se pudo leer ningún archivo, un formulario vacío sin
        // explicación era lo que veía Gestión (prueba del 24-09-2026 con un PDF
        // escaneado: 200 con todos los campos en null). Se dice por qué.
        if (resultado.tipoContrato() == null && resultado.objeto() != null) {
            resultado = conTipo(resultado, ExtraccionDeterminista.tipoPorObjeto(resultado.objeto()));
        }
        if (!sinTexto.isEmpty() && sinTexto.size() == procesados) {
            throw new BusinessException("No se pudo leer texto en " + String.join(", ", sinTexto)
                    + ". SICOT lee PDF con texto y documentos de Word (.docx); un PDF escaneado es una imagen"
                    + " y todavía no se reconoce su texto. Diligencie los datos a mano o cargue el documento"
                    + " original.");
        }
        return resultado;
    }

    private ExtraccionContratoResponse extraerDeUnArchivo(MultipartFile archivo) {
        String nombreOriginal = archivo.getOriginalFilename();
        String nombreArchivo = nombreOriginal == null ? "" : nombreOriginal.toLowerCase();
        boolean esPdf = nombreArchivo.endsWith(".pdf");
        boolean esDocx = nombreArchivo.endsWith(".docx");
        if (!esPdf && !esDocx) {
            log.info("Se omite '{}' del análisis: la lectura automática admite PDF y DOCX.", nombreOriginal);
            return null;
        }

        byte[] contenido;
        try {
            contenido = archivo.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer el archivo cargado: " + archivo.getOriginalFilename(), e);
        }

        String texto = esPdf ? pdfTextExtractor.extraerTexto(contenido) : docxTextExtractor.extraerTexto(contenido);
        if (texto == null || texto.isBlank()) {
            log.info("'{}' no tiene texto legible (¿imagen escaneada sin OCR?); se omite del análisis.", archivo.getOriginalFilename());
            return null;
        }
        // Los documentos reales con datos de contrato (Acta de Inicio, notificación) rara
        // vez superan 3000 caracteres. Los manuales/formatos en blanco son mucho más largos
        // pero no aportan datos — igual se recortan para no gastar minutos de CPU en ellos.
        String textoRecortado = texto.length() > 6000 ? texto.substring(0, 6000) : texto;

        // PRIMERO lo que no necesita modelo. Número de contrato, NIT, valor,
        // fechas y registro presupuestal tienen forma fija en un contrato
        // estatal colombiano: sacarlos con expresiones regulares cuesta menos de
        // un milisegundo y no se equivoca. La medición del 14 de septiembre de
        // 2026 mostró que era justo donde fallaban los modelos —el de 3B no
        // encontró el valor del contrato, el de 7B pegó la cédula al nombre del
        // representante— y que cada intento costaba más de dos minutos de CPU.
        // Ver ExtraccionDeterminista para las cifras completas.
        ExtraccionContratoResponse deterministica = extraccionDeterminista.extraer(texto);

        // Si el documento rotulaba el objeto y el tipo, ya no queda nada que
        // preguntarle al modelo: la extracción termina en milisegundos en vez
        // de minutos, que en un equipo sin GPU es la diferencia entre usarla o
        // no (ver feedback de ADR-008: bastar con una IA pequeña).
        // Si el objeto trae las palabras que dicen el tipo («…contratar el
        // suministro de…»), tampoco hace falta el modelo: llamarlo solo para
        // proponer un valor de una lista desplegable costaba de uno a cuatro
        // minutos, y el 24-09-2026 una notificación real se cortó por tiempo
        // esperando justo eso. El tipo deducido NO se fija aquí sino al final
        // de extraer(), después de combinar: así un rótulo explícito en otro
        // archivo gana siempre a una deducción de este.
        boolean tipoDeducible = deterministica.objeto() != null
                && ExtraccionDeterminista.tipoPorObjeto(deterministica.objeto()) != null;
        if (extraccionDeterminista.estaCompleta(deterministica)
                || (deterministica.objeto() != null && tipoDeducible)) {
            log.info("'{}': todos los campos salieron del documento sin usar el modelo.", archivo.getOriginalFilename());
            return deterministica;
        }

        String prompt = """
                Eres un asistente que extrae datos de documentos de contratación pública colombiana (SENA).
                Del siguiente texto, extrae ÚNICAMENTE los datos que aparezcan explícitamente. Si un dato no
                aparece, usa null — NUNCA inventes un valor. Este documento puede ser un manual, un formato
                en blanco o una plantilla sin diligenciar (en ese caso casi todos los campos serán null; eso
                es correcto, no inventes datos para llenarlos).

                Responde solo con un objeto JSON con exactamente estas dos claves:
                objeto (el objeto contractual copiado LITERALMENTE del documento, sin cambiar ninguna palabra),
                tipoContrato (una de: "Suministro de Bienes", "Compraventa", "Servicios", "Obras",
                "Arrendamiento" — la que mejor describa el objeto; si no es clara, usa null).

                No incluyas ninguna otra clave. Los demás datos del contrato —número, NIT, valor, fechas,
                registro presupuestal— ya se extrajeron por otro medio y no se te están pidiendo.

                %s

                %s
                """.formatted(EntradaNoConfiable.INSTRUCCION,
                        EntradaNoConfiable.bloque("TEXTO DEL DOCUMENTO", textoRecortado));

        log.info("Extrayendo datos de '{}' ({} bytes, {} caracteres de texto) con Ollama...",
                archivo.getOriginalFilename(), contenido.length, textoRecortado.length());
        long inicio = System.currentTimeMillis();
        String respuestaCruda;
        try {
            respuestaCruda = ollamaClient.generar(prompt, true);
        } catch (IaNoDisponibleException | co.sena.sicot.exception.DemasiadasSolicitudesException e) {
            // Lo que el código ya leyó del documento no depende del modelo: si
            // el modelo no responde, se devuelve eso en vez de perderlo todo.
            // Antes un corte por tiempo tiraba el número, el valor y las fechas
            // que ya estaban leídos, y Gestión recibía un 503.
            log.warn("El modelo no respondió para '{}'; se devuelve lo leído del documento: {}",
                    archivo.getOriginalFilename(), e.getMessage());
            return deterministica;
        }
        log.info("Extracción de '{}' completada en {} ms", archivo.getOriginalFilename(), System.currentTimeMillis() - inicio);
        if (respuestaCruda == null || respuestaCruda.isBlank()) {
            return deterministica;
        }
        ExtraccionContratoResponse delModelo;
        try {
            delModelo = objectMapper.readValue(respuestaCruda, ExtraccionContratoResponse.class);
        } catch (IOException e) {
            // Solo un prefijo corto: la respuesta cruda puede reflejar texto del
            // PDF (incluido un intento de inyección) y no tiene por qué quedar
            // completa en el log del servidor.
            String muestra = respuestaCruda.length() > 300 ? respuestaCruda.substring(0, 300) + "…" : respuestaCruda;
            log.warn("Respuesta de Ollama para '{}' no es el JSON esperado: {}", archivo.getOriginalFilename(), muestra);
            // Un documento con formato inesperado no debe tumbar el análisis de
            // los demás: se devuelve lo que sacó el extractor determinista.
            return deterministica;
        }
        // Lo que devuelve el modelo se revisa antes de usarlo. El objeto tiene
        // que estar escrito en el documento: si el modelo lo reescribió, se
        // descarta y la persona lo copia. El tipo tiene que ser una de las
        // opciones del formulario.
        String objeto = delModelo.objeto();
        if (objeto != null && !apareceEnElTexto(objeto, texto)) {
            log.warn("El objeto que propuso el modelo para '{}' no está escrito en el documento; se descarta.",
                    archivo.getOriginalFilename());
            objeto = null;
        }
        // List.of(...).contains(null) lanza NullPointerException: se comprueba antes.
        String tipo = delModelo.tipoContrato() != null && TIPOS.contains(delModelo.tipoContrato())
                ? delModelo.tipoContrato() : null;
        // El determinista manda: si sacó un campo, ese valor gana.
        return combinar(deterministica, new ExtraccionContratoResponse(null, objeto, null, null, null, null,
                null, null, null, null, tipo));
    }

    /**
     * ¿El texto propuesto está escrito en el documento? Se compara sin tildes,
     * mayúsculas, puntuación ni saltos de línea, para no descartar una copia
     * fiel por un espacio de más. Un cambio de palabra («CONTRAER» por
     * «CONTRATAR») sí la descarta.
     */
    static boolean apareceEnElTexto(String propuesto, String texto) {
        String p = soloLetrasYDigitos(propuesto);
        return !p.isBlank() && soloLetrasYDigitos(texto).contains(p);
    }

    private static String soloLetrasYDigitos(String s) {
        String sinTildes = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return sinTildes.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
    }

    private static ExtraccionContratoResponse conTipo(ExtraccionContratoResponse r, String tipo) {
        return new ExtraccionContratoResponse(r.idContrato(), r.objeto(), r.proveedor(), r.nit(),
                r.representanteLegal(), r.valor(), r.vigenciaInicio(), r.vigenciaFin(), r.lugarEjecucion(),
                r.registroPresupuestal(), tipo);
    }

    private ExtraccionContratoResponse combinar(ExtraccionContratoResponse base, ExtraccionContratoResponse nuevo) {
        return new ExtraccionContratoResponse(
                primero(base.idContrato(), nuevo.idContrato()),
                primero(base.objeto(), nuevo.objeto()),
                primero(base.proveedor(), nuevo.proveedor()),
                primero(base.nit(), nuevo.nit()),
                primero(base.representanteLegal(), nuevo.representanteLegal()),
                primero(base.valor(), nuevo.valor()),
                primero(base.vigenciaInicio(), nuevo.vigenciaInicio()),
                primero(base.vigenciaFin(), nuevo.vigenciaFin()),
                primero(base.lugarEjecucion(), nuevo.lugarEjecucion()),
                primero(base.registroPresupuestal(), nuevo.registroPresupuestal()),
                primero(base.tipoContrato(), nuevo.tipoContrato()));
    }

    private String primero(String existente, String candidato) {
        return existente != null && !existente.isBlank() ? existente : candidato;
    }
}
