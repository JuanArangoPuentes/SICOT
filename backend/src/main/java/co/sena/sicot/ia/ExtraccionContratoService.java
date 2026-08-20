package co.sena.sicot.ia;

import co.sena.sicot.dto.ia.ExtraccionContratoResponse;
import co.sena.sicot.exception.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
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

    private final PdfTextExtractor pdfTextExtractor;
    private final OllamaClient ollamaClient;
    private final ObjectMapper objectMapper;

    public ExtraccionContratoService(PdfTextExtractor pdfTextExtractor, OllamaClient ollamaClient,
                                      ObjectMapper objectMapper) {
        this.pdfTextExtractor = pdfTextExtractor;
        this.ollamaClient = ollamaClient;
        this.objectMapper = objectMapper;
    }

    public ExtraccionContratoResponse extraer(List<MultipartFile> archivos) {
        List<MultipartFile> validos = archivos == null ? List.of() : archivos.stream().filter(a -> a != null && !a.isEmpty()).toList();
        if (validos.isEmpty()) {
            throw new BusinessException("Debe cargar al menos un archivo para analizar.");
        }
        if (validos.size() > 6) {
            throw new BusinessException("Cargue como máximo 6 documentos a la vez.");
        }

        ExtraccionContratoResponse resultado = new ExtraccionContratoResponse(
                null, null, null, null, null, null, null, null, null, null, null);
        for (MultipartFile archivo : validos) {
            ExtraccionContratoResponse deEsteArchivo = extraerDeUnArchivo(archivo);
            resultado = combinar(resultado, deEsteArchivo);
        }
        return resultado;
    }

    private ExtraccionContratoResponse extraerDeUnArchivo(MultipartFile archivo) {
        String nombreArchivo = archivo.getOriginalFilename() == null ? "" : archivo.getOriginalFilename().toLowerCase();
        if (!nombreArchivo.endsWith(".pdf")) {
            log.info("Se omite '{}' del análisis: por ahora la lectura automática solo admite PDF.", archivo.getOriginalFilename());
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

                TEXTO DEL DOCUMENTO:
                %s
                """.formatted(textoRecortado);

        log.info("Extrayendo datos de '{}' ({} bytes, {} caracteres de texto) con Ollama...",
                archivo.getOriginalFilename(), contenido.length, textoRecortado.length());
        long inicio = System.currentTimeMillis();
        String respuestaCruda = ollamaClient.generar(prompt, true);
        log.info("Extracción de '{}' completada en {} ms", archivo.getOriginalFilename(), System.currentTimeMillis() - inicio);
        try {
            return objectMapper.readValue(respuestaCruda, ExtraccionContratoResponse.class);
        } catch (IOException e) {
            log.warn("Respuesta de Ollama para '{}' no es el JSON esperado: {}", archivo.getOriginalFilename(), respuestaCruda);
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
