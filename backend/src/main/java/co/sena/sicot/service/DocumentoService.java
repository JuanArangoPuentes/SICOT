package co.sena.sicot.service;

import co.sena.sicot.dto.documento.DocumentoResponse;
import co.sena.sicot.dto.documento.VerificacionIntegridadResponse;
import co.sena.sicot.entity.Contrato;
import co.sena.sicot.entity.Documento;
import co.sena.sicot.entity.FirmaElectronica;
import co.sena.sicot.entity.FormatoDocumental;
import co.sena.sicot.entity.Subetapa;
import co.sena.sicot.entity.enums.EstadoDocumento;
import co.sena.sicot.entity.enums.TipoDocumento;
import co.sena.sicot.exception.BusinessException;
import co.sena.sicot.exception.ResourceNotFoundException;
import co.sena.sicot.ia.EstampaDeFirma;
import co.sena.sicot.mapper.DocumentoMapper;
import co.sena.sicot.repository.DocumentoRepository;
import co.sena.sicot.repository.FirmaElectronicaRepository;
import co.sena.sicot.repository.FormatoDocumentalRepository;
import co.sena.sicot.repository.SubetapaRepository;
import co.sena.sicot.security.SecurityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.List;

@Service
public class DocumentoService {

    private static final Logger log = LoggerFactory.getLogger(DocumentoService.class);

    private final DocumentoRepository documentoRepository;
    private final ContratoService contratoService;
    private final SubetapaRepository subetapaRepository;
    private final FirmaElectronicaRepository firmaElectronicaRepository;
    private final FormatoDocumentalRepository formatoDocumentalRepository;
    private final RegistroService registroService;
    private final ArchivoValidator archivoValidator;
    private final LectorDeCaptura lectorDeCaptura;
    private final EstampaDeFirma estampaDeFirma;

    public DocumentoService(DocumentoRepository documentoRepository, ContratoService contratoService,
                             SubetapaRepository subetapaRepository, FirmaElectronicaRepository firmaElectronicaRepository,
                             FormatoDocumentalRepository formatoDocumentalRepository,
                             RegistroService registroService, ArchivoValidator archivoValidator,
                             LectorDeCaptura lectorDeCaptura, EstampaDeFirma estampaDeFirma) {
        this.documentoRepository = documentoRepository;
        this.contratoService = contratoService;
        this.subetapaRepository = subetapaRepository;
        this.firmaElectronicaRepository = firmaElectronicaRepository;
        this.formatoDocumentalRepository = formatoDocumentalRepository;
        this.registroService = registroService;
        this.archivoValidator = archivoValidator;
        this.lectorDeCaptura = lectorDeCaptura;
        this.estampaDeFirma = estampaDeFirma;
    }

    /**
     * Documentos de un contrato, sin traer un solo byte de los archivos — ver
     * {@code DocumentoRepository.listarPorContrato}. La comprobación de acceso
     * sigue estando primero: {@code contratoService.buscar} lanza 404 si el
     * usuario no es el supervisor de ese contrato.
     */
    @Transactional(readOnly = true)
    public List<DocumentoResponse> listarPorContrato(Long contratoId) {
        contratoService.buscar(contratoId);
        return documentoRepository.listarPorContrato(contratoId);
    }

    @Transactional
    /**
     * Carga un documento contra un contrato.
     *
     * <p>{@code formatoId} indica de qué formato institucional es instancia este
     * archivo —el Acta de Inicio, el GCCON-F-031 del paquete de asignación…—.
     * Es opcional porque no todo documento de un contrato lo es: un soporte
     * cualquiera que adjunta el supervisor no representa ningún formato
     * oficial, y obligar a clasificarlo llevaría a elegir cualquier entrada del
     * catálogo con tal de poder guardar.
     */
    public DocumentoResponse subir(Long contratoId, Long subetapaId, Long formatoId, String nombre, MultipartFile archivo) {
        Contrato contrato = contratoService.buscar(contratoId);
        String nombreLimpio = nombre == null || nombre.isBlank()
                ? (archivo != null ? archivo.getOriginalFilename() : null)
                : nombre.trim();
        if (nombreLimpio == null || nombreLimpio.isBlank()) {
            throw new BusinessException("El nombre del documento es obligatorio.");
        }
        // «�» (U+FFFD) es lo que queda de un nombre enviado en otra codificación
        // que UTF-8: dos cargas del 21-09-2026 quedaron como «supervisi�n» y así
        // se descargaban. Mejor rechazarlo con un mensaje que guardarlo dañado.
        if (nombreLimpio.indexOf('�') >= 0 || nombreLimpio.chars().anyMatch(Character::isISOControl)) {
            throw new BusinessException("El nombre del documento tiene caracteres no válidos; escríbalo de nuevo.");
        }
        if (archivo == null || archivo.isEmpty()) {
            throw new BusinessException("Debe seleccionar un archivo para cargar.");
        }
        archivoValidator.validarTamanio(archivo);
        // El expediente admite fotos además de los documentos de ofimática: la
        // evidencia de la entrega en bodega llega desde la cámara del teléfono.
        ArchivoValidator.ArchivoAceptado aceptado =
                archivoValidator.aceptar(archivo, ArchivoValidator.EVIDENCIAS_DEL_EXPEDIENTE);

        Documento documento = new Documento();
        documento.setContrato(contrato);
        if (subetapaId != null) {
            documento.setSubetapa(buscarSubetapaDelContrato(subetapaId, contrato));
        }
        if (formatoId != null) {
            documento.setFormato(buscarFormatoDelCatalogo(formatoId));
        }
        documento.setNombre(nombreLimpio);
        documento.setTipo(aceptado.tipo());
        documento.setContentType(aceptado.contentType());
        documento.setTamanioBytes(archivo.getSize());
        documento.setEstado(EstadoDocumento.PENDIENTE);
        documento.setSubidoPor(SecurityUtils.currentUsuario());
        try {
            documento.setContenido(archivo.getBytes());
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer el archivo cargado.", e);
        }

        // Una foto trae dentro cuándo y dónde se tomó, y es lo que la hace
        // servir de evidencia de la entrega (subetapa 3.2). Se lee de los mismos
        // bytes que se guardan, no de una copia: lo que queda en el expediente
        // y lo que se afirma de él salen del mismo archivo.
        String sobreLaCaptura = "";
        if (aceptado.tipo() == TipoDocumento.IMAGEN) {
            LectorDeCaptura.Captura captura = lectorDeCaptura.leer(documento.getContenido());
            documento.setCapturaFecha(captura.fecha());
            documento.setCapturaLatitud(captura.latitud());
            documento.setCapturaLongitud(captura.longitud());
            sobreLaCaptura = " " + lectorDeCaptura.describir(captura);
        }

        Documento guardado = documentoRepository.save(documento);

        // En la misma transacción que el documento: si la carga se deshace, su
        // rastro también. Hasta el 21-09-2026 cargar un documento no dejaba
        // ninguno: /api/registros no mostraba cargas, y el resumen periódico
        // del motor (RedaccionDeResumen), que ya contaba DOCUMENTO_CARGADO,
        // nunca podía contar nada — un periodo en el que solo se cargaron
        // documentos se descartaba por «sin movimientos».
        registroService.registrar(contrato, "DOCUMENTO_CARGADO",
                "Documento «" + nombreLimpio + "» cargado"
                        + (guardado.getSubetapa() != null
                                ? " en la subetapa " + guardado.getSubetapa().getCodigo()
                                : "")
                        + "." + sobreLaCaptura);
        return DocumentoMapper.toResponse(guardado);
    }

    /**
     * El formato tiene que existir en el catálogo. Se rechaza con un error de
     * negocio y no con un 404 porque quien se equivoca aquí no está pidiendo un
     * recurso inexistente: está cargando un archivo y eligiendo mal la etiqueta.
     */
    private FormatoDocumental buscarFormatoDelCatalogo(Long formatoId) {
        return formatoDocumentalRepository.findById(formatoId)
                .orElseThrow(() -> new BusinessException(
                        "El formato documental indicado no existe en el catálogo."));
    }

    @Transactional(readOnly = true)
    public Documento buscarConContenido(Long id) {
        Documento documento = documentoRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Documento", id));
        SecurityUtils.verificarAccesoAlContrato(documento.getContrato());
        return documento;
    }

    /**
     * Firma el documento y registra la huella de lo que se está firmando.
     *
     * <p>El SHA-256 es lo que convierte la firma en algo comprobable: sin él,
     * "firmado" era una etiqueta que sobrevivía a cualquier modificación
     * posterior del contenido. Se calcula sobre los bytes que hay en ese
     * instante, dentro de la misma transacción que escribe la firma, para que
     * no exista ninguna ventana entre lo que se midió y lo que se firmó.
     *
     * @param huellaRevisada SHA-256 del borrador que el supervisor leyó antes de
     *                       firmar. Si el contenido ya no es ese —otra pestaña o un
     *                       reintento lo regeneró entre medias—, no se firma: se habría
     *                       firmado una redacción que nadie leyó (revisión del
     *                       29-09-2026). Obligatoria para lo que genera SICOT; para un
     *                       documento cargado puede ser {@code null}.
     */
    @Transactional
    public DocumentoResponse firmar(Long id, String huellaRevisada) {
        Documento documento = documentoRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Documento", id));
        SecurityUtils.verificarAccesoAlContrato(documento.getContrato());
        if (documento.getFirmaId() != null) {
            throw new BusinessException("Este documento ya fue firmado.");
        }
        if (documento.getContenido() == null || documento.getContenido().length == 0) {
            throw new BusinessException("Este documento no tiene contenido: no hay nada que firmar.");
        }
        // Un borrador generado se regenera sobre la misma fila (un reintento,
        // otra pestaña), así que sin la huella de lo que se revisó se firmaba lo
        // que hubiera en la fila en ese instante: quizá los datos de otra
        // petición, que quien firma nunca vio (auditoría del 02-10-2026).
        if (documento.isGeneradoPorIa() && (huellaRevisada == null || huellaRevisada.isBlank())) {
            throw new BusinessException("Falta indicar qué borrador revisó: un documento que genera SICOT solo se"
                    + " firma junto con la huella del borrador que se le mostró. Vuelva a firmar el paso para ver"
                    + " el borrador actual.");
        }
        if (huellaRevisada != null && !huellaRevisada.isBlank()
                && !huellaRevisada.strip().equalsIgnoreCase(HuellaDeDocumento.calcular(documento.getContenido()))) {
            throw new BusinessException("El borrador cambió desde que lo revisó: se volvió a generar en otra"
                    + " pestaña o en un intento anterior. Vuelva a firmar el paso para ver la redacción actual.");
        }
        // Dos actas firmadas del mismo paso son dos documentos oficiales que
        // pueden contradecirse. Pasaba si quedaba un borrador de un intento
        // anterior y se firmaba después desde Documentos (revisión del
        // 24-09-2026). Solo aplica a lo generado por SICOT en una subetapa: dos
        // fotos de evidencia de la misma subetapa son perfectamente válidas.
        if (documento.isGeneradoPorIa() && documento.getSubetapa() != null
                && documentoRepository.existsByContratoIdAndSubetapaIdAndNombreAndFirmaIdIsNotNullAndIdNot(
                        documento.getContrato().getId(), documento.getSubetapa().getId(),
                        documento.getNombre(), documento.getId())) {
            throw new BusinessException("Ya hay un «" + documento.getNombre() + "» firmado en esta subetapa. "
                    + "Un documento firmado no se reemplaza firmando otro.");
        }
        var usuario = SecurityUtils.currentUsuario();
        // Los documentos de un contrato los firma solo su supervisor asignado.
        // Un documento formal que genera SICOT lleva además su nombre en el
        // bloque de firma, y la firma se estampa encima: que la firmara otra
        // persona —un administrador con firma propia— dejaba su nombre
        // estampado en el hueco del supervisor (revisión del 28-09-2026). Hasta
        // la auditoría del 02-10-2026 la regla valía solo para los generados:
        // un acta cargada por Gestión la podía firmar cualquier administrador,
        // y quedaba aprobada e inmutable sin que el supervisor la viera.
        //
        // Tres casos, según la revisión del 29-09-2026: sin supervisor asignado
        // el administrador podía firmarlo sobre «[dato pendiente: supervisor]»;
        // y si Gestión reasignaba el contrato, el supervisor nuevo firmaba un
        // borrador que llevaba impreso el nombre del anterior. Por eso se mira
        // también para quién se generó (queda en el PDF), no solo quién es
        // el supervisor hoy. Regenerar el borrador lo pone a nombre del actual.
        var supervisor = documento.getContrato().getSupervisor();
        if (supervisor == null) {
            throw new BusinessException(documento.isGeneradoPorIa()
                    ? "El contrato no tiene supervisor asignado, y un documento generado lleva el nombre del"
                            + " supervisor en su bloque de firma. Asigne el supervisor y vuelva a generarlo."
                    : "El contrato no tiene supervisor asignado, y sus documentos solo los firma su supervisor.");
        }
        if (!supervisor.getId().equals(usuario.getId())) {
            throw new BusinessException("Este documento lo firma el supervisor del contrato (" + supervisor.getNombre()
                    + (documento.isGeneradoPorIa() ? "): su nombre es el que aparece en el bloque de firma." : ")."));
        }
        if (documento.isGeneradoPorIa()) {
            Long previsto = estampaDeFirma.firmantePrevisto(documento.getContenido());
            if (previsto != null && !previsto.equals(usuario.getId())) {
                throw new BusinessException("Este borrador se generó cuando el supervisor del contrato era otra"
                        + " persona, y lleva su nombre en el bloque de firma. Vuelva a generarlo para que lleve el suyo.");
            }
        }
        FirmaElectronica firma = firmaElectronicaRepository.findFirstByUsuarioIdAndActivaTrue(usuario.getId())
                .orElseThrow(() -> new BusinessException(
                        "No tiene una firma electrónica activa asignada. Solicítela al Administrador."));

        Instant ahora = Instant.now();
        // Los documentos que genera SICOT llevan la firma visible: se estampa
        // en el hueco que el PDF reservó para ella ANTES de calcular la
        // huella, así que lo que queda registrado como firmado es el PDF con
        // la firma ya puesta. Un PDF cargado desde fuera se firma tal cual. Si
        // la estampa falla, estampar lanza y no se firma: el documento sigue
        // pendiente y se puede regenerar, en vez de quedar firmado sin firma
        // visible y sin arreglo (auditoría del 02-10-2026).
        if (documento.isGeneradoPorIa() && "application/pdf".equalsIgnoreCase(documento.getContentType())) {
            byte[] estampado = estampaDeFirma.estampar(documento.getContenido(), usuario.getNombre(),
                    firma.getFirmaId(), ahora);
            documento.setContenido(estampado);
            documento.setTamanioBytes((long) estampado.length);
        }
        String huella = HuellaDeDocumento.calcular(documento.getContenido());

        documento.setFirmaId(firma.getFirmaId());
        documento.setFirmaHashSha256(huella);
        documento.setFirmadoPor(usuario);
        documento.setFechaFirma(ahora);
        documento.setEstado(EstadoDocumento.APROBADO);
        Documento guardado = documentoRepository.save(documento);

        registroService.registrar(documento.getContrato(), "DOCUMENTO_FIRMADO",
                documento.getNombre() + " firmado por " + usuario.getNombre() + " (" + firma.getFirmaId()
                        + "). Huella SHA-256: " + huella + ".");
        return DocumentoMapper.toResponse(guardado);
    }

    /**
     * Compara la huella registrada al firmar con la del contenido actual.
     *
     * <p>Sin efectos: no escribe log ni auditoría. Es la versión que puede
     * llamarse en cada descarga sin ensuciar nada. La entidad ya debe venir
     * cargada con su contenido.
     */
    @Transactional(readOnly = true)
    public VerificacionIntegridadResponse.Estado estadoDeIntegridad(Documento documento) {
        if (documento.getFirmaId() == null) {
            return VerificacionIntegridadResponse.Estado.SIN_FIRMA;
        }
        if (documento.getFirmaHashSha256() == null) {
            return VerificacionIntegridadResponse.Estado.NO_VERIFICABLE;
        }
        return HuellaDeDocumento.coincide(documento.getFirmaHashSha256(),
                HuellaDeDocumento.calcular(documento.getContenido()))
                ? VerificacionIntegridadResponse.Estado.INTEGRO
                : VerificacionIntegridadResponse.Estado.ALTERADO;
    }

    /**
     * Verificación completa, con explicación para el funcionario.
     *
     * <p>Un resultado {@code ALTERADO} se registra en el log a nivel ERROR y en
     * la auditoría del contrato: un documento oficial que cambió después de
     * firmado es un incidente, no un dato más de una respuesta HTTP. Por eso
     * este método sí escribe y {@link #estadoDeIntegridad} no.
     */
    @Transactional
    public VerificacionIntegridadResponse verificarIntegridad(Long id) {
        Documento documento = buscarConContenido(id);
        String firmadoPor = documento.getFirmadoPor() != null ? documento.getFirmadoPor().getNombre() : null;
        VerificacionIntegridadResponse.Estado estado = estadoDeIntegridad(documento);

        String hashActual = documento.getFirmaId() == null
                ? null
                : HuellaDeDocumento.calcular(documento.getContenido());

        String mensaje = switch (estado) {
            case SIN_FIRMA -> "Este documento todavía no está firmado.";
            case NO_VERIFICABLE -> "Este documento se firmó antes de que el sistema registrara la huella "
                    + "del contenido. Su integridad no se puede confirmar ni descartar.";
            case INTEGRO -> "El documento coincide exactamente con lo que se firmó.";
            case ALTERADO -> "ATENCIÓN: el contenido de este documento cambió después de haber sido "
                    + "firmado. No lo dé por válido y avise al área de sistemas.";
        };

        if (estado == VerificacionIntegridadResponse.Estado.ALTERADO) {
            log.error("INTEGRIDAD: el documento {} ('{}') del contrato {} NO coincide con la huella "
                            + "registrada al firmar. Registrada={} Actual={}",
                    documento.getId(), documento.getNombre(), documento.getContrato().getId(),
                    documento.getFirmaHashSha256(), hashActual);
            registroService.registrar(documento.getContrato(), "INTEGRIDAD_COMPROMETIDA",
                    "El documento " + documento.getNombre() + " no coincide con la huella registrada al "
                            + "firmarlo: su contenido cambió después de la firma.");
        }

        return new VerificacionIntegridadResponse(documento.getId(), documento.getNombre(), estado,
                documento.getFirmaHashSha256(), hashActual, documento.getFirmaId(),
                documento.getFechaFirma(), firmadoPor, mensaje);
    }

    private Subetapa buscarSubetapaDelContrato(Long subetapaId, Contrato contrato) {
        // La pertenencia al contrato es parte de la consulta, no una
        // comprobación posterior — ver SubetapaRepository.
        return subetapaRepository.findByIdAndEtapaContratoId(subetapaId, contrato.getId())
                .orElseThrow(() -> new BusinessException(
                        "La subetapa indicada no existe o no pertenece a este contrato."));
    }

}
