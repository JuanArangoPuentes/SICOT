package co.sena.sicot.ia;

import co.sena.sicot.dto.documento.DocumentoGeneradoResponse;
import co.sena.sicot.entity.Contrato;
import co.sena.sicot.entity.Documento;
import co.sena.sicot.entity.Subetapa;
import co.sena.sicot.entity.enums.EstadoDocumento;
import co.sena.sicot.entity.enums.TipoDocumento;
import co.sena.sicot.exception.BusinessException;
import co.sena.sicot.exception.DemasiadasSolicitudesException;
import co.sena.sicot.mapper.DocumentoMapper;
import co.sena.sicot.repository.DocumentoRepository;
import co.sena.sicot.repository.SubetapaRepository;
import co.sena.sicot.service.ContratoService;
import co.sena.sicot.service.RegistroService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Genera el borrador (estado PENDIENTE) de un documento formal del supervisor.
 * El supervisor lo firma después con {@code DocumentoService.firmar}.
 *
 * <h2>Qué hace el código y qué hace la IA</h2>
 * El documento lo arma {@link RedactorDeDocumentos} con los formatos reales y
 * los datos exactos del contrato: ningún dato del contrato pasa por el modelo.
 * Así fue desde que la prueba integral del 24-09-2026 mostró al modelo
 * alterando nombres, valores y entidades en los cinco documentos (el detalle
 * está en {@link RedactorDeDocumentos}).
 *
 * <p>La IA hace una sola cosa, y solo si el supervisor escribió notas sobre lo
 * que hizo en el paso: pasarlas a redacción formal para el apartado de
 * observaciones. Esa redacción se revisa con {@link FidelidadDeRedaccion}
 * antes de usarse, y si trae cifras que no estaban, o si el modelo no
 * responde, van las notas tal cual. Un documento nunca deja de generarse
 * porque la IA falle: los datos que lo hacen válido no dependen de ella.
 */
@Service
public class GeneracionDocumentoService {

    private static final Logger log = LoggerFactory.getLogger(GeneracionDocumentoService.class);

    /**
     * Largo máximo de las notas que se mandan a redactar. Con más texto la
     * redacción en un equipo sin GPU se va de los minutos, y unas
     * observaciones de un paso no necesitan más: las que pasan de aquí van
     * tal cual, completas.
     */
    static final int MAX_NOTAS = 2000;

    private final ContratoService contratoService;
    private final SubetapaRepository subetapaRepository;
    private final DocumentoRepository documentoRepository;
    private final OllamaClient ollamaClient;
    private final PdfInstitucional pdfInstitucional;
    private final RegistroService registroService;
    private final Clock reloj;

    public GeneracionDocumentoService(ContratoService contratoService, SubetapaRepository subetapaRepository,
                                      DocumentoRepository documentoRepository, OllamaClient ollamaClient,
                                      PdfInstitucional pdfInstitucional, RegistroService registroService,
                                      Clock reloj) {
        this.contratoService = contratoService;
        this.subetapaRepository = subetapaRepository;
        this.documentoRepository = documentoRepository;
        this.ollamaClient = ollamaClient;
        this.pdfInstitucional = pdfInstitucional;
        this.registroService = registroService;
        this.reloj = reloj;
    }

    public DocumentoGeneradoResponse generar(Long contratoId, Long subetapaId, String tipoClave) {
        return generar(contratoId, subetapaId, tipoClave, null, Map.of());
    }

    public DocumentoGeneradoResponse generar(Long contratoId, Long subetapaId, String tipoClave, String notas) {
        return generar(contratoId, subetapaId, tipoClave, notas, Map.of());
    }

    public DocumentoGeneradoResponse generar(Long contratoId, Long subetapaId, String tipoClave, String notas,
                                             Map<String, String> datos) {
        return generar(contratoId, subetapaId, tipoClave, notas, datos, true);
    }

    // Sin @Transactional a propósito — mismo motivo que CopilotoChatService.responder:
    // la redacción con el modelo puede tardar minutos y no debe retener una
    // conexión del pool de Hikari todo ese tiempo. contratoService.buscar,
    // subetapaRepository y documentoRepository.save abren y cierran sus propias
    // transacciones cortas.
    /**
     * @param datos         datos del documento que el contrato no tiene (factura,
     *                      póliza, cédulas…), por clave de {@link PlantillaDocumentoIA#campos}.
     *                      Los que falten salen en el PDF como «[dato pendiente…]».
     * @param redactarConIa {@code false} cuando el supervisor pide sus notas tal cual, después
     *                      de ver la redacción del Copiloto: no se llama al modelo.
     */
    public DocumentoGeneradoResponse generar(Long contratoId, Long subetapaId, String tipoClave, String notas,
                                             Map<String, String> datos, boolean redactarConIa) {
        return generar(contratoId, subetapaId, tipoClave, notas, datos, null, redactarConIa);
    }

    /**
     * @param tablas filas de las tablas del formato (obligaciones, amparos,
     *               órdenes de pago), por clave de {@link PlantillaDocumentoIA#tablas}.
     */
    public DocumentoGeneradoResponse generar(Long contratoId, Long subetapaId, String tipoClave, String notas,
                                             Map<String, String> datos, Map<String, List<List<String>>> tablas,
                                             boolean redactarConIa) {
        PlantillaDocumentoIA plantilla = PlantillaDocumentoIA.CATALOGO.get(tipoClave);
        if (plantilla == null) {
            throw new BusinessException("Tipo de documento no reconocido: " + tipoClave);
        }
        Map<String, String> datosValidos = datosDeLaPlantilla(plantilla, datos);
        Map<String, List<List<String>>> tablasValidas = tablasDeLaPlantilla(plantilla, tablas);
        // Antes de llamar al modelo: un error de los datos se corrige en el
        // formulario, y esperar minutos la redacción para enterarse era peor.
        String incoherencia = RedactorDeDocumentos.incoherencia(plantilla, datosValidos, tablasValidas);
        if (incoherencia != null) {
            throw new BusinessException(incoherencia);
        }
        Contrato contrato = contratoService.buscar(contratoId);

        // La consulta exige que la subetapa sea de ESTE contrato: sin esa
        // condición, un supervisor podía generar un documento en su contrato
        // apuntando a la subetapa de otro, contaminando el avance de un
        // contrato ajeno.
        Subetapa subetapa = null;
        if (subetapaId != null) {
            subetapa = subetapaRepository.findByIdAndEtapaContratoId(subetapaId, contrato.getId())
                    .orElseThrow(() -> new BusinessException(
                            "La subetapa indicada no existe o no pertenece a este contrato."));
        }

        // Un documento firmado es oficial: no se reemplaza generando otro
        // encima. Sin esta comprobación, un doble clic o un reintento dejaba un
        // segundo borrador que la bandeja mostraba como «documento sin firmar».
        if (subetapa != null && documentoRepository.existsByContratoIdAndSubetapaIdAndNombreStartingWithAndGeneradoPorIaTrueAndFirmaIdIsNotNull(
                contrato.getId(), subetapa.getId(), plantilla.nombre())) {
            throw new BusinessException("Ya hay un «" + plantilla.nombre() + "» firmado en la subetapa "
                    + subetapa.getCodigo() + ". Un documento firmado no se reemplaza generando otro.");
        }

        Observaciones obs = observaciones(plantilla, contrato, notas, redactarConIa);
        LocalDate hoy = LocalDate.now(reloj);
        List<BloqueDocumento> bloques = RedactorDeDocumentos.componer(plantilla, contrato, hoy, obs.texto(),
                datosValidos, tablasValidas);

        String firmante = contrato.getSupervisor() != null ? contrato.getSupervisor().getNombre() : null;
        String origen = obs.conIa() ? "Generado en SICOT con apoyo del Copiloto IA" : "Generado en SICOT";
        byte[] pdf = pdfInstitucional.generar(new DocumentoFormal(plantilla.formato(),
                plantilla.nombre() + " — " + contrato.getNumeroContrato(), contrato.getNumeroContrato(),
                firmante, origen, bloques, contrato.getSupervisor() != null ? contrato.getSupervisor().getId() : null));

        // Un borrador de este formato sin firmar en la misma subetapa es un
        // intento anterior que no llegó a firmarse (en el teléfono la conexión
        // se corta si SICOT pasa a segundo plano mientras se redacta). Se
        // actualiza ESE borrador en vez de crear otro, que quedaría para
        // siempre como «documento sin firmar» en la bandeja. Hasta el
        // 28-09-2026 se devolvía tal cual, sin regenerarlo: con los datos
        // complementarios eso habría firmado el borrador viejo sin los datos
        // que el supervisor acababa de corregir. Se busca justo antes de
        // guardar porque entre el inicio y aquí pasan los segundos de la
        // redacción con IA, y un doble clic o dos pestañas podían colarse.
        Documento documento = null;
        if (subetapa != null) {
            documento = documentoRepository
                    .findFirstByContratoIdAndSubetapaIdAndNombreStartingWithAndGeneradoPorIaTrueAndFirmaIdIsNullOrderByFechaSubidaDesc(
                            contrato.getId(), subetapa.getId(), plantilla.nombre())
                    .orElse(null);
        }
        boolean reutilizado = documento != null;
        if (documento == null) {
            documento = new Documento();
            documento.setContrato(contrato);
            documento.setSubetapa(subetapa);
            documento.setNombre(plantilla.nombre() + " — " + contrato.getNumeroContrato());
        } else {
            log.info("Se regenera el borrador {} de '{}' sin firmar en la subetapa {}.",
                    documento.getId(), plantilla.nombre(), subetapa.getCodigo());
            // El nombre se recalcula: si Gestión corrigió el número del
            // contrato, el borrador guardaba el viejo y el expediente y la
            // descarga lo seguían mostrando aunque el PDF ya llevara el nuevo.
            documento.setNombre(plantilla.nombre() + " — " + contrato.getNumeroContrato());
        }
        documento.setTipo(TipoDocumento.PDF);
        documento.setContentType("application/pdf");
        documento.setContenido(pdf);
        documento.setTamanioBytes((long) pdf.length);
        documento.setEstado(EstadoDocumento.PENDIENTE);
        // «generadoPorIa» conserva el nombre de la columna, pero lo que marca es
        // que el documento lo produjo SICOT y no se cargó desde fuera: el panel
        // del supervisor lo usa para reconocer los documentos formales del
        // proceso. Si la IA intervino lo dice el registro, que es donde queda
        // la trazabilidad.
        documento.setGeneradoPorIa(true);
        Documento guardado = documentoRepository.save(documento);

        int pendientes = RedactorDeDocumentos.contarPendientes(bloques);
        registroService.registrar(contrato, "DOCUMENTO_GENERADO",
                plantilla.nombre() + " (" + plantilla.codigo() + ") " + (reutilizado ? "regenerado" : "generado")
                        + " por SICOT con los datos del contrato"
                        + aportes(datosValidos, tablasValidas)
                        + obs.descripcion()
                        + (subetapa != null ? " en la subetapa " + subetapa.getCodigo() : "")
                        + "; queda pendiente de firma"
                        + (pendientes > 0 ? " (" + pendientes + " datos del formato quedaron marcados como pendientes)" : "")
                        + ".");
        return new DocumentoGeneradoResponse(DocumentoMapper.toResponse(guardado), obs.texto(), obs.conIa(),
                obs.motivoNotasTalCual(), co.sena.sicot.service.HuellaDeDocumento.calcular(pdf));
    }

    /** Longitud máxima de un dato complementario: una forma de pago o un rubro caben de sobra. */
    static final int MAX_DATO = 600;
    /**
     * Filas por tabla. El Informe Final real tiene 16 obligaciones específicas
     * y 15 generales; 60 deja margen sin permitir un documento de cientos de
     * páginas por error.
     */
    static final int MAX_FILAS = 60;
    /** Una obligación larga del Informe Final real ronda los 600 caracteres. */
    static final int MAX_CELDA = 1500;

    /** Saltos de línea normalizados a «\n» y los demás caracteres de control a espacio. */
    private static String conSaltosDeLinea(String v) {
        return v.replace("\r\n", "\n").replace('\r', '\n').replaceAll("[\\p{Cntrl}&&[^\\n]]", " ");
    }

    /**
     * Una celda como se dibuja: sin los cortes de renglón de un texto copiado
     * de un PDF (una obligación pegada desde el contrato traía un salto donde
     * el PDF partía el renglón, y en la celda salían renglones cortados), pero
     * con los saltos que separan viñetas o frases. Un salto seguido de una
     * minúscula y precedido de algo que no cierra la frase es un corte de
     * renglón; las líneas en blanco se juntan.
     */
    static String celdaLimpia(String v) {
        return conSaltosDeLinea(v).replaceAll("[ \\t]*\\n[ \\t]*", "\n")
                .replaceAll("\\n{2,}", "\n")
                .replaceAll("(?<![.:;])\\n(?=\\p{Ll})", " ")
                .strip();
    }

    /**
     * «y 5 datos aportados por el supervisor», «y 1 fila de tablas aportada…»,
     * «y 1 dato y 3 filas de tablas aportados…», o nada. El registro es la
     * trazabilidad del documento y se lee: «1 fila aportados» se notaba.
     */
    static String aportes(Map<String, String> datos, Map<String, List<List<String>>> tablas) {
        int filas = tablas.values().stream().mapToInt(List::size).sum();
        if (datos.isEmpty() && filas == 0) {
            return "";
        }
        String deDatos = datos.size() + (datos.size() == 1 ? " dato" : " datos");
        String deFilas = filas + (filas == 1 ? " fila" : " filas") + " de tablas";
        String aportados = datos.isEmpty() ? deFilas + (filas == 1 ? " aportada" : " aportadas")
                : filas == 0 ? deDatos + (datos.size() == 1 ? " aportado" : " aportados")
                : deDatos + " y " + deFilas + " aportados";
        return " y " + aportados + " por el supervisor";
    }

    /**
     * Solo pasan las tablas que el formato declara, con tantas celdas por fila
     * como columnas tiene. Las filas vacías se descartan; una celda vacía en
     * una fila con datos queda como «[dato pendiente]» en el PDF. Los saltos
     * de línea se conservan (una obligación con viñetas) y los demás
     * caracteres de control pasan a espacio.
     */
    static Map<String, List<List<String>>> tablasDeLaPlantilla(PlantillaDocumentoIA plantilla,
                                                                Map<String, List<List<String>>> tablas) {
        Map<String, List<List<String>>> validas = new LinkedHashMap<>();
        if (tablas == null) {
            return validas;
        }
        for (PlantillaDocumentoIA.TablaDelDocumento t : plantilla.tablas()) {
            List<List<String>> filas = tablas.get(t.clave());
            if (filas == null) {
                continue;
            }
            // Antes de limpiar: las filas vacías también ocupan la petición, y
            // contarlas solo después dejaba mandar miles de ellas.
            if (filas.size() > 2 * MAX_FILAS) {
                throw new BusinessException("La tabla «" + t.etiqueta() + "» admite hasta " + MAX_FILAS + " filas.");
            }
            int columnas = t.columnas().size();
            List<List<String>> limpias = new ArrayList<>();
            for (List<String> fila : filas) {
                if (fila == null) {
                    continue;
                }
                for (int i = columnas; i < fila.size(); i++) {
                    if (fila.get(i) != null && !fila.get(i).isBlank()) {
                        throw new BusinessException("La tabla «" + t.etiqueta() + "» tiene " + columnas
                                + " columnas y una de sus filas trae " + fila.size() + ".");
                    }
                }
                List<String> celdas = new ArrayList<>();
                for (int i = 0; i < columnas; i++) {
                    String v = i < fila.size() && fila.get(i) != null ? celdaLimpia(fila.get(i)) : "";
                    if (v.length() > MAX_CELDA) {
                        throw new BusinessException("Una celda de «" + t.etiqueta() + "» supera " + MAX_CELDA
                                + " caracteres.");
                    }
                    celdas.add(v);
                }
                if (celdas.stream().anyMatch(c -> !c.isEmpty())) {
                    limpias.add(List.copyOf(celdas));
                }
            }
            if (limpias.size() > MAX_FILAS) {
                throw new BusinessException("La tabla «" + t.etiqueta() + "» admite hasta " + MAX_FILAS + " filas.");
            }
            if (!limpias.isEmpty()) {
                validas.put(t.clave(), List.copyOf(limpias));
            }
        }
        return validas;
    }

    /**
     * Solo pasan las claves que el formato declara: una clave desconocida no
     * llega al documento, y un valor demasiado largo se rechaza con un
     * mensaje en vez de dibujarse a medias. Los caracteres de control (un
     * tabulador pegado de Excel) pasan a espacio.
     */
    static Map<String, String> datosDeLaPlantilla(PlantillaDocumentoIA plantilla, Map<String, String> datos) {
        Map<String, String> validos = new LinkedHashMap<>();
        if (datos == null) {
            return validos;
        }
        Map<String, PlantillaDocumentoIA.CampoDelDocumento> campos = new HashMap<>();
        plantilla.campos().forEach(cd -> campos.put(cd.clave(), cd));
        for (Map.Entry<String, String> e : datos.entrySet()) {
            String valor = e.getValue() == null ? "" : e.getValue().strip();
            PlantillaDocumentoIA.CampoDelDocumento campo = campos.get(e.getKey());
            if (valor.isEmpty() || campo == null) {
                continue;
            }
            // Un párrafo (el cumplimiento del SIGA) admite más y conserva sus
            // saltos de línea; un dato de una línea no.
            int maximo = campo.largo() ? PlantillaDocumentoIA.MAX_LARGO : MAX_DATO;
            if (valor.length() > maximo) {
                throw new BusinessException("El dato «" + e.getKey() + "» supera " + maximo + " caracteres.");
            }
            validos.put(e.getKey(), campo.largo() ? conSaltosDeLinea(valor) : valor.replaceAll("\\p{Cntrl}", " "));
        }
        return validos;
    }

    /**
     * El apartado de observaciones y cómo se obtuvo: la descripción va al
     * registro; el motivo, al supervisor, que decide si firma.
     */
    private record Observaciones(String texto, boolean conIa, String descripcion, String motivoNotasTalCual) {
        Observaciones(String texto, boolean conIa, String descripcion) {
            this(texto, conIa, descripcion, null);
        }
    }

    private Observaciones observaciones(PlantillaDocumentoIA plantilla, Contrato contrato, String notas,
                                        boolean redactarConIa) {
        if (notas == null || notas.isBlank()) {
            return new Observaciones(null, false, "");
        }
        // El Acta de Inicio y el certificado no tienen apartado de
        // observaciones: agregarles uno los apartaría del formato. Las notas no
        // se mandan al modelo —serían minutos de redacción para nada— y el
        // registro dice por qué no aparecen.
        if (!plantilla.llevaObservaciones()) {
            return new Observaciones(null, false,
                    "; las notas del supervisor no se incluyen porque el formato no tiene apartado de observaciones");
        }
        // Tal cual quiere decir completas. Recortarlas al usarlas tal cual
        // cortaba a mitad de palabra unas notas que el diálogo de revisión
        // mostraba enteras (revisión del 29-09-2026). El DTO ya las limita a 4000.
        String completas = notas.strip();
        if (!redactarConIa) {
            return new Observaciones(completas, false,
                    "; las observaciones van tal como las escribió el supervisor (así lo pidió)");
        }
        // Hasta el 02-10-2026 al modelo le llegaban solo los primeros 2000
        // caracteres y la fidelidad se comparaba con esos mismos: una
        // redacción fiel del principio pasaba, y el documento firmable perdía
        // el final de las notas, que es donde suelen ir los faltantes o el
        // requerimiento al contratista. Unas notas así de largas no se
        // redactan, y el supervisor no espera minutos para nada.
        if (completas.length() > MAX_NOTAS) {
            return new Observaciones(completas, false,
                    "; las observaciones van tal como las escribió el supervisor (eran demasiado largas para"
                            + " redactarlas con la IA)",
                    "sus notas son demasiado largas para que el Copiloto las redacte en este equipo");
        }

        String redactado;
        try {
            long inicio = System.currentTimeMillis();
            redactado = ollamaClient.generarSinCompetir(promptDeRedaccion(plantilla, completas),
                    topeDeTokens(plantilla, completas), ESPERA_POR_OTRA_INFERENCIA);
            log.info("Observaciones de '{}' redactadas en {} ms", plantilla.nombre(), System.currentTimeMillis() - inicio);
        } catch (IaNoDisponibleException | DemasiadasSolicitudesException e) {
            // El documento no depende de la IA: si no responde, o el limitador
            // la rechaza, van las notas tal cual y el supervisor no pierde el paso.
            log.warn("No se pudieron redactar las observaciones de '{}' con la IA; se usan las notas tal cual: {}",
                    plantilla.nombre(), e.getMessage());
            return sinRedaccion(completas, e);
        }

        List<String> nombres = new ArrayList<>();
        nombres.add(contrato.getContratista());
        nombres.add(contrato.getRepresentanteLegal());
        if (contrato.getSupervisor() != null) {
            nombres.add(contrato.getSupervisor().getNombre());
        }
        String corregido = depurar(redactado, completas, nombres);

        List<String> datos = new ArrayList<>(nombres);
        datos.add(contrato.getNumeroContrato());
        datos.add(contrato.getContratistaNit());
        datos.add(contrato.getNumeroRegistroPresupuestal());
        // stripTrailingZeros: con la columna en escala 2, toPlainString daba
        // «120450000.00» y el valor bien escrito («$120.450.000») se tomaba
        // por una cifra inventada.
        datos.add(contrato.getValor() != null ? contrato.getValor().stripTrailingZeros().toPlainString() : null);
        // El valor en letras correcto tampoco es una cifra inventada.
        datos.add(contrato.getValor() != null ? NumeroEnLetras.pesos(contrato.getValor()) : null);
        String infiel = motivoDeInfidelidad(corregido, completas, datos);
        if (infiel != null) {
            log.warn("La redacción de '{}' {}; se usan las notas tal cual.", plantilla.nombre(), infiel);
            return new Observaciones(completas, false,
                    "; las observaciones van tal como las escribió el supervisor (la redacción de la IA " + infiel
                            + ")",
                    "la redacción del Copiloto " + infiel);
        }
        return new Observaciones(corregido, true,
                "; las observaciones del supervisor se redactaron con el copiloto");
    }

    /**
     * Cuánto espera la redacción a que termine otra inferencia en curso antes
     * de empezar (ver {@link OllamaClient#generarSinCompetir}). Lo típico es
     * el precalentado que sale al abrir el contrato, de unos 160 s: el
     * supervisor que genera en seguida espera a lo sumo esto más la redacción,
     * y si la IA sigue ocupada recibe sus notas tal cual con la causa verdadera.
     */
    static final Duration ESPERA_POR_OTRA_INFERENCIA = Duration.ofSeconds(90);

    private static final String ACTA_RECIBO = "ACTA_RECIBO";

    /** El prompt de la redacción de observaciones. */
    static String promptDeRedaccion(PlantillaDocumentoIA plantilla, String notas) {
        return """
                Eres el Copiloto de SICOT. Vas a redactar el apartado de observaciones del documento "%s" (%s).
                Convierte en uno o dos párrafos formales, en primera persona del supervisor y en español \
                institucional, las NOTAS DEL SUPERVISOR que aparecen más abajo.

                Reglas obligatorias:
                - Usa únicamente los hechos que dicen las notas. No agregues cifras, fechas, nombres, \
                entidades, cantidades ni verificaciones que no estén en ellas.
                - No repitas los datos del contrato (número, valor, fechas, contratista): ya van en la ficha.
                - Si las notas son breves, el texto también debe serlo.%s
                - Responde solo con el texto final, sin títulos, sin viñetas y sin markdown.

                %s

                %s
                """.formatted(plantilla.nombre(), plantilla.codigo(),
                // La hoja GIL-F-010 solo tiene dos renglones para observaciones:
                // lo que no quepa va a una hoja de continuación, pero el acta
                // se lee mejor si cabe en su casilla.
                ACTA_RECIBO.equals(plantilla.clave())
                        ? "\n- No pases de 350 caracteres: en el formato solo caben dos renglones." : "",
                EntradaNoConfiable.INSTRUCCION,
                EntradaNoConfiable.bloque("NOTAS DEL SUPERVISOR", notas));
    }

    /**
     * Tope de tokens de la redacción: unas dos veces lo que ocupan las notas
     * (un token son unos cuatro caracteres), con un mínimo para las notas de
     * una línea. Corta al modelo que entra en un bucle de repetición en vez de
     * dejarlo escribir hasta el tiempo límite; la redacción que llega al tope
     * sale cortada y se trata como un fallo. El Acta de Recibo lleva uno
     * propio, porque se le piden 350 caracteres.
     */
    static int topeDeTokens(PlantillaDocumentoIA plantilla, String notas) {
        if (ACTA_RECIBO.equals(plantilla.clave())) {
            return 240;
        }
        return Math.min(800, Math.max(160, notas.length() / 2));
    }

    /**
     * Lo que se le hace a la respuesta del modelo antes de comprobarla, sin
     * cambiar lo que dice: poner exactos los nombres conocidos y quitar el año
     * que le agrega a una fecha que el supervisor escribió sin él. Lo que se
     * comprueba y lo que entra al documento es el mismo texto.
     */
    static String depurar(String redactado, String notas, List<String> nombres) {
        return FidelidadDeRedaccion.quitarAniosAgregados(
                FidelidadDeRedaccion.corregirNombres(redactado.strip(), nombres), notas);
    }

    /**
     * Las notas tal cual, con la causa verdadera de que no haya redacción.
     * Hasta el 02-10-2026 todo fallo de la IA se decía «el Copiloto no
     * respondió a tiempo»: con Ollama apagado o el modelo sin descargar falla
     * en milisegundos, y el supervisor reintentaba creyendo que el equipo
     * estaba lento en vez de avisar a sistemas.
     */
    private static Observaciones sinRedaccion(String completas, RuntimeException e) {
        IaNoDisponibleException.Causa causa = e instanceof IaNoDisponibleException ia ? ia.getCausa() : null;
        String registro;
        String motivo;
        if (e instanceof IaOcupadaException) {
            registro = "la IA estaba atendiendo otra solicitud";
            motivo = "el Copiloto estaba atendiendo otra solicitud";
        } else if (e instanceof DemasiadasSolicitudesException) {
            registro = "se alcanzó el tope de consultas a la IA por minuto";
            motivo = "hizo demasiadas consultas al Copiloto en el último minuto";
        } else if (causa == IaNoDisponibleException.Causa.TIEMPO_AGOTADO) {
            registro = "la IA no respondió a tiempo";
            motivo = "el Copiloto no respondió a tiempo";
        } else if (causa == IaNoDisponibleException.Causa.RESPUESTA_CORTADA) {
            registro = "la redacción de la IA quedó cortada";
            motivo = "la redacción del Copiloto quedó cortada";
        } else {
            registro = "la IA no estaba disponible";
            motivo = "el Copiloto no está disponible en este momento";
        }
        return new Observaciones(completas, false,
                "; las observaciones van tal como las escribió el supervisor (" + registro + ")", motivo);
    }

    /**
     * Por qué la redacción no es fiel a las notas, o {@code null} si lo es.
     * Las comprobaciones son heurísticas: si alguna fallara con una entrada
     * que no se previó, se toma la redacción por no fiel y van las notas tal
     * cual, en vez de dejar al supervisor sin su documento.
     */
    private static String motivoDeInfidelidad(String corregido, String notas, List<String> datos) {
        try {
            return FidelidadDeRedaccion.motivoDeInfidelidad(corregido, notas, datos);
        } catch (RuntimeException e) {
            log.warn("La comprobación de fidelidad falló; se usan las notas tal cual", e);
            return "no se pudo comprobar";
        }
    }
}
