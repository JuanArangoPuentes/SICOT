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
     * defecto (900 s) una única petición podía retener un hilo de Tomcat hasta
     * 90 minutos. Con unas pocas simultáneas se agota el pool de hilos y deja
     * de responder toda la API, no solo la extracción.
     *
     * En hardware sin GPU una extracción real ronda los 35-40 s por archivo
     * (≈4 min para los 6), así que este presupuesto no estorba el uso normal:
     * solo corta el caso patológico.
     */
    @Value("${sicot.ia.presupuesto-extraccion-seconds:900}")
    private long presupuestoSegundos;

    public ExtraccionContratoService(PdfTextExtractor pdfTextExtractor, OllamaClient ollamaClient,
                                      ObjectMapper objectMapper, ArchivoValidator archivoValidator) {
        this.pdfTextExtractor = pdfTextExtractor;
        this.ollamaClient = ollamaClient;
        this.objectMapper = objectMapper;
        this.archivoValidator = archivoValidator;
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
            resultado = combinar(resultado, deEsteArchivo);
            procesados++;
        }
        return resultado;
    }

    private ExtraccionContratoResponse extraerDeUnArchivo(MultipartFile archivo) {
        String nombreOriginal = archivo.getOriginalFilename();
        String nombreArchivo = nombreOriginal == null ? "" : nombreOriginal.toLowerCase();
        if (!nombreArchivo.endsWith(".pdf")) {
            log.info("Se omite '{}' del análisis: por ahora la lectura automática solo admite PDF.", nombreOriginal);
            return new ExtraccionContratoResponse(null, null, null, null, null, null, null, null, null, null, null);
        }

        byte[] contenido;
        try {
            contenido = archivo.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer el archivo cargado: " + archivo.getOriginalFilename(), e);
        }

        String texto = pdfTextExtractor.extraerTexto(contenido);
        if (texto == null || texto.isBlank()) {
            log.info("'{}' no tiene texto legible (¿imagen escaneada sin OCR?); se omite del análisis.", archivo.getOriginalFilename());
            return new ExtraccionContratoResponse(null, null, null, null, null, null, null, null, null, null, null);
        }
        // Los documentos reales con datos de contrato (Acta de Inicio, notificación) rara
        // vez superan 3000 caracteres. Los manuales/formatos en blanco son mucho más largos
        // pero no aportan datos — igual se recortan para no gastar minutos de CPU en ellos.
        String textoRecortado = texto.length() > 6000 ? texto.substring(0, 6000) : texto;

        String prompt = """
                Eres un asistente que extrae datos de documentos de contratación pública colombiana (SENA).
                Del siguiente texto, extrae ÚNICAMENTE los datos que aparezcan explícitamente. Si un dato no
                aparece, usa null — NUNCA inventes un valor. Este documento puede ser un manual, un formato
                en blanco o una plantilla sin diligenciar (en ese caso casi todos los campos serán null; eso
                es correcto, no inventes datos para llenarlos).

                Responde solo con un objeto JSON con exactamente estas claves:
                idContrato (número de contrato, ej. CO1.PCCNTR.xxxxxxx), objeto (objeto contractual),
                proveedor (razón social del contratista), nit (NIT o cédula del contratista),
                representanteLegal,
                valor (SOLO la cifra numérica del valor del contrato, sin texto, sin "$", sin puntos ni
                comas de miles — un entero plano. Ejemplo: si el documento dice "DIEZ MILLONES DE PESOS
                ($10.000.000 COP)" o "$10.000.000", el valor es 10000000. Si no hay cifra numérica clara,
                usa null),
                vigenciaInicio (fecha de inicio en formato AAAA-MM-DD), vigenciaFin (fecha de terminación
                en formato AAAA-MM-DD), lugarEjecucion, registroPresupuestal (número de registro
                presupuestal), tipoContrato (una de: "Suministro de Bienes", "Servicios", "Obras",
                "Arrendamiento" — la que mejor describa el objeto; si no es clara, usa null).

                %s

                %s
                """.formatted(EntradaNoConfiable.INSTRUCCION,
                        EntradaNoConfiable.bloque("TEXTO DEL DOCUMENTO", textoRecortado));

        log.info("Extrayendo datos de '{}' ({} bytes, {} caracteres de texto) con Ollama...",
                archivo.getOriginalFilename(), contenido.length, textoRecortado.length());
        long inicio = System.currentTimeMillis();
        String respuestaCruda = ollamaClient.generar(prompt, true);
        log.info("Extracción de '{}' completada en {} ms", archivo.getOriginalFilename(), System.currentTimeMillis() - inicio);
        try {
            return objectMapper.readValue(respuestaCruda, ExtraccionContratoResponse.class);
        } catch (IOException e) {
            // Solo un prefijo corto: la respuesta cruda puede reflejar texto del
            // PDF (incluido un intento de inyección) y no tiene por qué quedar
            // completa en el log del servidor.
            String muestra = respuestaCruda.length() > 300 ? respuestaCruda.substring(0, 300) + "…" : respuestaCruda;
            log.warn("Respuesta de Ollama para '{}' no es el JSON esperado: {}", archivo.getOriginalFilename(), muestra);
            // Un documento con formato inesperado no debe tumbar el análisis de los demás.
            return new ExtraccionContratoResponse(null, null, null, null, null, null, null, null, null, null, null);
        }
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
