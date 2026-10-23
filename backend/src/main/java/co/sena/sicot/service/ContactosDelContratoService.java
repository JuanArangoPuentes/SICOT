package co.sena.sicot.service;

import co.sena.sicot.dto.contacto.ContactosDelContratoResponse;
import co.sena.sicot.dto.contacto.GuardarContactosRequest;
import co.sena.sicot.dto.contacto.LecturaDeContactosResponse;
import co.sena.sicot.entity.ContactoDelContrato;
import co.sena.sicot.entity.Contrato;
import co.sena.sicot.entity.enums.TipoDocumento;
import co.sena.sicot.exception.BusinessException;
import co.sena.sicot.ia.DocxTextExtractor;
import co.sena.sicot.ia.PdfTextExtractor;
import co.sena.sicot.repository.ContactoDelContratoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Los profesionales de SST y ambiental del contrato: a quién le avisa el
 * supervisor en el paso 2.2 que el contrato inició, y a quién le comparte las
 * fechas de entrega acordadas en el 2.4.
 *
 * <h2>De dónde salen</h2>
 * Del estudio previo, dice el paso 3 de la lista de chequeo de Alex Zapata. El
 * supervisor lo abre en el SECOP II con el enlace del proceso y los anota aquí,
 * o carga el documento y SICOT le propone los correos que encuentra en él
 * ({@link #leerDelDocumento}). Lo cargado no se guarda en el expediente: se lee
 * y se descarta, porque el estudio previo ya está publicado en el SECOP y lo
 * que el contrato necesita de él son dos correos, no otra copia de 5 MB.
 */
@Service
public class ContactosDelContratoService {

    private final ContratoService contratoService;
    private final ContactoDelContratoRepository contactoRepository;
    private final RegistroService registroService;
    private final ArchivoValidator archivoValidator;
    private final PdfTextExtractor pdfTextExtractor;
    private final DocxTextExtractor docxTextExtractor;

    public ContactosDelContratoService(ContratoService contratoService,
                                       ContactoDelContratoRepository contactoRepository,
                                       RegistroService registroService, ArchivoValidator archivoValidator,
                                       PdfTextExtractor pdfTextExtractor, DocxTextExtractor docxTextExtractor) {
        this.contratoService = contratoService;
        this.contactoRepository = contactoRepository;
        this.registroService = registroService;
        this.archivoValidator = archivoValidator;
        this.pdfTextExtractor = pdfTextExtractor;
        this.docxTextExtractor = docxTextExtractor;
    }

    @Transactional(readOnly = true)
    public ContactosDelContratoResponse obtener(Long contratoId) {
        contratoService.buscar(contratoId);
        return respuesta(contactoRepository.findByContratoIdOrderByIdAsc(contratoId));
    }

    /**
     * Reemplaza los contactos del contrato. El mismo correo con el mismo rol
     * dos veces se guarda una sola: duplicado solo repetiría el destinatario.
     */
    @Transactional
    public ContactosDelContratoResponse guardar(Long contratoId, GuardarContactosRequest request) {
        Contrato contrato = contratoService.buscar(contratoId);
        Map<String, ContactoDelContrato> unicos = new LinkedHashMap<>();
        for (GuardarContactosRequest.Contacto c : request.contactos() == null
                ? List.<GuardarContactosRequest.Contacto>of() : request.contactos()) {
            String correo = c.correo().strip().toLowerCase(Locale.ROOT);
            String nombre = c.nombre() == null || c.nombre().isBlank() ? null : c.nombre().strip();
            unicos.putIfAbsent(c.rol() + "|" + correo, new ContactoDelContrato(contrato, c.rol(), nombre, correo));
        }

        contactoRepository.borrarDelContrato(contratoId);
        contactoRepository.flush();
        List<ContactoDelContrato> guardados = contactoRepository.saveAll(unicos.values());

        registroService.registrar(contrato, "CONTACTOS_SST_AMBIENTAL", guardados.isEmpty()
                ? "Se borraron los contactos de SST y ambiental del contrato."
                : "Contactos de SST y ambiental del contrato: " + guardados.stream()
                        .map(g -> g.getRol() + " " + g.getCorreo())
                        .collect(Collectors.joining(", ")) + ".");
        return respuesta(contactoRepository.findByContratoIdOrderByIdAsc(contratoId));
    }

    /**
     * Los correos del estudio previo que carga el supervisor, con el rol que
     * sugiere el texto que los rodea. No guarda nada: ni el archivo ni los
     * contactos, que el supervisor elige después.
     *
     * <p>Sin {@code @Transactional}, a propósito: leer un PDF de varios MB toma
     * segundos y no necesita la base. El acceso lo verifica
     * {@code ContratoService.buscar} en su propia transacción, que se cierra
     * antes de abrir el archivo; con una transacción aquí, cada carga retenía
     * una de las 10 conexiones del pool mientras PDFBox trabajaba.
     */
    public LecturaDeContactosResponse leerDelDocumento(Long contratoId, MultipartFile archivo) {
        contratoService.buscar(contratoId);
        if (archivo == null || archivo.isEmpty()) {
            throw new BusinessException("Cargue el estudio previo en PDF o Word.");
        }
        archivoValidator.validarTamanio(archivo);
        TipoDocumento tipo = archivoValidator.aceptar(archivo, Set.of(TipoDocumento.PDF, TipoDocumento.DOCX)).tipo();
        byte[] contenido;
        try {
            contenido = archivo.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer el archivo cargado.", e);
        }
        String texto = tipo == TipoDocumento.PDF
                ? pdfTextExtractor.extraerTexto(contenido)
                : docxTextExtractor.extraerTexto(contenido);
        if (texto == null || texto.isBlank()) {
            return new LecturaDeContactosResponse(List.of(), "El documento no tiene texto que se pueda leer: "
                    + "parece una imagen escaneada, y SICOT todavía no lee documentos escaneados. Copie los "
                    + "correos de SST y ambiental a mano.");
        }
        List<LecturaDeContactosResponse.Encontrado> encontrados = ContactosEnUnDocumento.leer(texto).stream()
                .map(e -> new LecturaDeContactosResponse.Encontrado(e.correo(), e.rol(), e.contexto()))
                .toList();
        if (encontrados.isEmpty()) {
            return new LecturaDeContactosResponse(List.of(), "El documento no trae ningún correo electrónico. "
                    + "Si es el estudio previo, los profesionales pueden figurar solo por su cargo: pregúntele a "
                    + "Contratación por sus correos.");
        }
        return new LecturaDeContactosResponse(encontrados, null);
    }

    /** Primero SST y después ambiental, el orden en que los nombra la hoja de Alex. */
    private static ContactosDelContratoResponse respuesta(List<ContactoDelContrato> contactos) {
        return new ContactosDelContratoResponse(contactos.stream()
                .sorted(Comparator.comparing(ContactoDelContrato::getRol))
                .map(c -> new ContactosDelContratoResponse.Contacto(c.getRol(), c.getNombre(), c.getCorreo()))
                .toList());
    }
}
