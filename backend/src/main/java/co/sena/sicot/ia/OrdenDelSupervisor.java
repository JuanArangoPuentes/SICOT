package co.sena.sicot.ia;

import co.sena.sicot.dto.documento.DocumentoResponse;
import co.sena.sicot.dto.etapa.EtapaResponse;
import co.sena.sicot.dto.etapa.SubetapaResponse;
import co.sena.sicot.dto.ia.ChatResponse;
import co.sena.sicot.entity.enums.EstadoSubetapa;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.MatchResult;
import java.util.stream.Collectors;

/**
 * Atiende sin modelo las órdenes que el supervisor le da al Copiloto
 * («genera el acta de inicio», «marca como completado el 2.3», «llévame al paso
 * 3», «muéstrame las alertas») y las convierte en una acción que la interfaz
 * ofrece como botón.
 *
 * <h2>Por qué existe</h2>
 * El panel invita a pedirle cosas al Copiloto, y hasta el 2-10-2026 toda orden
 * iba al modelo: minutos de CPU para, en el mejor caso, explicar dónde está el
 * botón. En el peor, un modelo de 3B/7B contestaba «listo, marqué el 2.3» sin
 * que nada hubiera pasado —teatro de IA sobre un acto con efectos, como
 * firmar—, redactaba en el chat un acta entera con datos que nadie dio, o le
 * advertía al propio supervisor de que su mensaje «parecía un intento de darle
 * instrucciones».
 *
 * <h2>Qué hace y qué no</h2>
 * <b>Ninguna orden firma, marca, genera ni modifica nada.</b> La respuesta dice
 * qué hay que hacer y quién lo hace (siempre el supervisor), y la acción solo
 * abre la pantalla donde él decide: el sub-paso con su botón «Firmar
 * documento» o «Marcar completado», la carga de fotos, Alertas, Documentos o
 * Configuración. Descargar se ofrece solo para un documento firmado que existe.
 *
 * <p>El reconocimiento es por una lista cerrada de verbos y objetos, igual que
 * los demás atajos y por la misma razón: se audita en un <i>pull request</i> y
 * falla de forma predecible. Solo actúa si el mensaje EMPIEZA por un verbo de
 * la lista (tras «por favor», «quiero», «¿puedes…?» y similares) y nombra algo
 * que reconoce; si no, devuelve vacío y la pregunta sigue su camino. «¿Cómo se
 * genera el acta de inicio?» no empieza por el verbo, y «firma el acta si…» es
 * una condicional: las dos siguen de largo.
 */
@Component
public class OrdenDelSupervisor {

    enum Verbo { GENERAR, FIRMAR, MARCAR, ABRIR, DESCARGAR, CARGAR, CAMBIAR }

    /** Los verbos, ya normalizados (sin tildes): «llévame» es «llevame». */
    private static final Map<String, Verbo> VERBOS = verbos();

    /**
     * Lo que se dice antes de la orden sin cambiarla. Las más largas primero,
     * para que «me puedes» se quite entero y no deje un «me» suelto.
     */
    private static final List<String> CORTESIA = List.of(
            "por favor me", "ayudame a", "ayudeme a", "me ayudas a", "me ayuda a", "me puedes", "me podrias",
            "me puede", "me podria", "por favor", "porfavor", "porfa", "puedes", "podrias", "puede", "podria",
            "quiero", "quisiera", "necesito", "vamos a", "copiloto", "oye", "hola", "bueno", "listo", "okay", "ok",
            "ahora", "entonces", "ya", "y");

    /** Con estas, una orden con «?» sigue siendo una petición: «¿me puedes abrir el paso 3?». */
    private static final Set<String> PETICION_EN_PREGUNTA = Set.of(
            "me puedes", "me podrias", "me puede", "me podria", "puedes", "podrias", "puede", "podria",
            "me ayudas a", "me ayuda a");

    private static final Set<String> CONFIGURACION = Set.of("tema", "colores", "color", "oscuro", "claro",
            "configuracion", "ajustes", "preset", "presets", "servidor");
    private static final Set<String> DATOS_DEL_CONTRATO = Set.of("valor", "fecha", "fechas", "contratista",
            "objeto", "nit", "supervisor", "plazo", "contrato");
    private static final Set<String> ALERTAS = Set.of("alerta", "alertas", "semaforo", "cronograma");
    private static final Set<String> DOCUMENTOS_EN_PLURAL = Set.of("documentos", "expediente");
    private static final Set<String> FOTOS = Set.of("foto", "fotos", "fotografia", "fotografias", "evidencia",
            "evidencias", "camara");
    private static final Set<String> OTROS_ARCHIVOS = Set.of("factura", "rut", "pila", "planilla", "poliza",
            "documento", "archivo", "pdf", "soporte", "certificado");
    private static final Set<String> DOCUMENTO_SIN_NOMBRE = Set.of("documento", "formato");
    private static final Set<String> MARCADO = Set.of("completado", "completada", "hecho", "listo", "terminado",
            "realizado");
    private static final Set<String> LO_ACTUAL = PreguntaNormalizada.normalizarTodas(
            "paso actual", "siguiente paso", "paso siguiente", "siguiente sub-paso", "siguiente subpaso",
            "dónde voy", "lo que sigue", "lo siguiente", "lo pendiente");

    /** «¿Qué puedes hacer?»: lo que el Copiloto hace y lo que no, sin que el modelo se lo invente. */
    private static final Set<String> PREGUNTAS_POR_LO_QUE_HACE = PreguntaNormalizada.normalizarTodas(
            "qué puedes hacer", "qué sabes hacer", "qué haces", "para qué sirves", "cómo me ayudas",
            "cómo me puedes ayudar", "en qué me puedes ayudar", "en qué me ayudas", "ayuda", "qué te puedo pedir",
            "qué puedo pedirte", "qué puedes hacer por mí");

    static final String LO_QUE_HACE = """
            Puedo ayudarle con este contrato:
            - decirle en qué paso va, qué le falta y qué botón pulsar;
            - explicarle cada documento formal: qué es, quién lo firma y en qué sub-paso se genera;
            - darle los datos del contrato: valor, fechas, días que quedan y cómo va el cronograma;
            - llevarlo a un paso, a un sub-paso, a la carga de fotos, a Alertas o a Documentos: pídamelo \
            («lléveme al paso 3») y le dejo un botón para abrirlo;
            - responder dudas abiertas del proceso con el modelo de IA local, que tarda más.

            Lo que no hago: no firmo, no genero documentos, no marco sub-pasos ni cambio nada en SICOT. Eso \
            lo hace usted con los botones de cada sub-paso.""";

    /**
     * La orden, si el mensaje es una que se puede atender sin modelo.
     *
     * @param documentos los documentos del contrato; solo se leen si la orden
     *                   necesita saber si uno ya está firmado
     * @param cronograma el mensaje del cronograma, si se puede calcular; solo
     *                   se lee para «muéstrame las alertas»
     */
    public Optional<ChatResponse> interpretar(String pregunta, List<EtapaResponse> etapas,
                                              Supplier<List<DocumentoResponse>> documentos,
                                              Supplier<Optional<String>> cronograma) {
        PreguntaNormalizada p = PreguntaNormalizada.de(pregunta);
        if (p.estaVacia() || p.largo() > 200 || p.tienePalabra("si")) {
            return Optional.empty();
        }
        if (p.esExactamenteAlguna(PREGUNTAS_POR_LO_QUE_HACE)) {
            return Optional.of(ChatResponse.delSistema(LO_QUE_HACE));
        }
        if (etapas == null || etapas.isEmpty()) {
            return Optional.empty();
        }
        String resto = p.toString();
        boolean esPeticion = false;
        for (boolean quito = true; quito; ) {
            quito = false;
            for (String cortesia : CORTESIA) {
                if (resto.startsWith(cortesia + " ")) {
                    esPeticion |= PETICION_EN_PREGUNTA.contains(cortesia);
                    resto = resto.substring(cortesia.length() + 1);
                    quito = true;
                    break;
                }
            }
        }
        // «¿Firma el contratista el acta?» empieza por «firma» y es una
        // pregunta. Con signo de interrogación solo es orden si se pide
        // («¿me puedes abrir el paso 3?»).
        if (!esPeticion && pregunta.contains("?")) {
            return Optional.empty();
        }
        int espacio = resto.indexOf(' ');
        Verbo verbo = VERBOS.get(espacio < 0 ? resto : resto.substring(0, espacio));
        if (verbo == null) {
            return Optional.empty();
        }
        PreguntaNormalizada objeto = PreguntaNormalizada.de(espacio < 0 ? "" : resto.substring(espacio + 1));
        Contexto ctx = new Contexto(etapas, documentos, cronograma);
        return switch (verbo) {
            case GENERAR, FIRMAR -> generarOFirmar(verbo, objeto, ctx);
            case MARCAR -> marcar(objeto, ctx);
            case ABRIR -> abrir(objeto, ctx);
            case DESCARGAR -> descargar(objeto, ctx);
            case CARGAR -> cargar(objeto, ctx);
            case CAMBIAR -> cambiar(objeto);
        };
    }

    private record Contexto(List<EtapaResponse> etapas, Supplier<List<DocumentoResponse>> documentos,
                            Supplier<Optional<String>> cronograma) {
    }

    /** Un sub-paso del contrato con la etapa a la que pertenece. */
    private record Ubicado(EtapaResponse etapa, SubetapaResponse sub) {
    }

    // ─────────────────────────────────────────────────────────────────────
    // Cada verbo
    // ─────────────────────────────────────────────────────────────────────

    private Optional<ChatResponse> generarOFirmar(Verbo verbo, PreguntaNormalizada objeto, Contexto ctx) {
        Optional<Ubicado> sub = subpasoNombrado(objeto, ctx.etapas());
        if (sub.isPresent()) {
            Optional<FichaDeDocumentoFormal.Documento> doc = FichaDeDocumentoFormal.delSubpaso(sub.get().sub().codigo());
            if (doc.isPresent()) {
                return Optional.of(sobreElDocumento(verbo, doc.get(), ctx));
            }
            return Optional.of(ChatResponse.delSistema(
                    "En el sub-paso %s no se firma ningún documento. %s Con el botón de abajo abre ese sub-paso."
                            .formatted(sub.get().sub().codigo(), GuiaDelPasoActual.comoSeRegistra(sub.get().sub())),
                    AccionesDelCopiloto.abrirSubpaso(sub.get().etapa().numero(), sub.get().sub())));
        }
        Optional<ChatResponse> porElDocumento = documentoNombrado(verbo, objeto, ctx);
        if (porElDocumento.isPresent()) {
            return porElDocumento;
        }
        // «Firma» a secas, o «firma el documento»: el que le queda por delante.
        // «Genera» a secas no: «hazme un resumen» también empieza así.
        if (verbo == Verbo.FIRMAR || objeto.contieneAlgunaFrase(DOCUMENTO_SIN_NOMBRE)) {
            return Optional.of(elDocumentoQueSigue(verbo, objeto, ctx));
        }
        return Optional.empty();
    }

    private Optional<ChatResponse> marcar(PreguntaNormalizada objeto, Contexto ctx) {
        Optional<Ubicado> sub = subpasoNombrado(objeto, ctx.etapas());
        if (sub.isPresent()) {
            return Optional.of(marcarElSubpaso(sub.get(), ctx));
        }
        Optional<ChatResponse> porElDocumento = documentoNombrado(Verbo.MARCAR, objeto, ctx);
        if (porElDocumento.isPresent()) {
            return porElDocumento;
        }
        Optional<EtapaResponse> paso = pasoNombrado(objeto, ctx.etapas());
        if (paso.isPresent()) {
            return Optional.of(marcarElPaso(paso.get()));
        }
        // «Marca como completado», «márcalo hecho»: el sub-paso por el que va.
        // «Cierra la sesión» no: sin objeto reconocible, la orden no es de aquí.
        if (objeto.estaVacia() || MARCADO.stream().anyMatch(objeto::tienePalabra)) {
            return primerPendiente(ctx.etapas())
                    .map(u -> marcarElSubpaso(u, ctx))
                    .or(() -> Optional.of(ChatResponse.delSistema(
                            "No le queda ningún sub-paso pendiente: todos están completados.")));
        }
        return Optional.empty();
    }

    private Optional<ChatResponse> abrir(PreguntaNormalizada objeto, Contexto ctx) {
        if (CONFIGURACION.stream().anyMatch(objeto::tienePalabra)) {
            return Optional.of(ChatResponse.delSistema("Con el botón de abajo abre Configuración.",
                    AccionesDelCopiloto.irAConfiguracion()));
        }
        if (ALERTAS.stream().anyMatch(objeto::tienePalabra)) {
            return Optional.of(alertas(ctx));
        }
        if (DOCUMENTOS_EN_PLURAL.stream().anyMatch(objeto::tienePalabra)) {
            return Optional.of(ChatResponse.delSistema(
                    "Los documentos del contrato están en la pestaña Documentos; con el botón de abajo la abre.",
                    AccionesDelCopiloto.mostrarDocumentos()));
        }
        if (FOTOS.stream().anyMatch(objeto::tienePalabra)) {
            return Optional.of(fotos(ctx.etapas()));
        }
        Optional<Ubicado> sub = subpasoNombrado(objeto, ctx.etapas());
        if (sub.isPresent()) {
            return Optional.of(abrirElSubpaso(sub.get()));
        }
        Optional<ChatResponse> porElDocumento = documentoNombrado(Verbo.ABRIR, objeto, ctx);
        if (porElDocumento.isPresent()) {
            return porElDocumento;
        }
        Optional<EtapaResponse> paso = pasoNombrado(objeto, ctx.etapas());
        if (paso.isPresent()) {
            return Optional.of(abrirElPaso(paso.get()));
        }
        if (objeto.contieneAlgunaFrase(DOCUMENTO_SIN_NOMBRE)) {
            return Optional.of(elDocumentoQueSigue(Verbo.ABRIR, objeto, ctx));
        }
        if (objeto.contieneAlgunaFrase(LO_ACTUAL)) {
            return Optional.of(primerPendiente(ctx.etapas())
                    .map(this::abrirElSubpaso)
                    .orElseGet(() -> ChatResponse.delSistema(
                            "No le queda ningún sub-paso pendiente: todos están completados.")));
        }
        return Optional.empty();
    }

    private Optional<ChatResponse> descargar(PreguntaNormalizada objeto, Contexto ctx) {
        if (DOCUMENTOS_EN_PLURAL.stream().anyMatch(objeto::tienePalabra)) {
            return Optional.of(ChatResponse.delSistema(
                    "Los documentos del contrato se descargan desde la pestaña Documentos; con el botón de abajo "
                            + "la abre.",
                    AccionesDelCopiloto.mostrarDocumentos()));
        }
        Optional<ChatResponse> porElDocumento = documentoNombrado(Verbo.DESCARGAR, objeto, ctx);
        if (porElDocumento.isPresent()) {
            return porElDocumento;
        }
        if (objeto.contieneAlgunaFrase(DOCUMENTO_SIN_NOMBRE)) {
            return Optional.of(elDocumentoQueSigue(Verbo.DESCARGAR, objeto, ctx));
        }
        return Optional.empty();
    }

    private Optional<ChatResponse> cargar(PreguntaNormalizada objeto, Contexto ctx) {
        if (FOTOS.stream().anyMatch(objeto::tienePalabra)) {
            return Optional.of(fotos(ctx.etapas()));
        }
        if (OTROS_ARCHIVOS.stream().anyMatch(objeto::tienePalabra)) {
            // La misma prohibición que el prompt: fuera de las fotos de 3.1 y
            // 3.2 no hay dónde cargar nada, y decir lo contrario manda al
            // supervisor a buscar un botón que no existe.
            return Optional.of(ChatResponse.delSistema("En SICOT solo se cargan fotos de la entrega, en los "
                    + "sub-pasos 3.1 y 3.2. Lo demás (factura, póliza, RUT, PILA…) lo verifica usted fuera de SICOT "
                    + "y después marca el sub-paso como completado."));
        }
        return Optional.empty();
    }

    private Optional<ChatResponse> cambiar(PreguntaNormalizada objeto) {
        if (CONFIGURACION.stream().anyMatch(objeto::tienePalabra)) {
            return Optional.of(ChatResponse.delSistema("Yo no puedo cambiar los ajustes de la interfaz: el tema, "
                    + "los colores y el servidor se cambian en Configuración. Con el botón de abajo la abre.",
                    AccionesDelCopiloto.irAConfiguracion()));
        }
        if (DATOS_DEL_CONTRATO.stream().anyMatch(objeto::tienePalabra)) {
            return Optional.of(ChatResponse.delSistema("Yo no puedo modificar los datos del contrato: los "
                    + "diligencia Gestión. Si alguno está mal, pídale a Gestión que lo corrija."));
        }
        return Optional.empty();
    }

    // ─────────────────────────────────────────────────────────────────────
    // Respuestas
    // ─────────────────────────────────────────────────────────────────────

    /** Generar, firmar, marcar, abrir o descargar un documento formal concreto. */
    private ChatResponse sobreElDocumento(Verbo verbo, FichaDeDocumentoFormal.Documento d, Contexto ctx) {
        String nombre = "«%s»%s".formatted(d.nombre(), d.codigo() == null ? "" : " (" + d.codigo() + ")");
        if (d.subpaso() == null) {
            // El Oficio de Pago: ni es del supervisor ni lo arma SICOT.
            Optional<Ubicado> certificacion = ubicar("5.3", ctx.etapas());
            return ChatResponse.delSistema("%s lo firma %s %s".formatted(nombre, d.firma(), d.aclaracion()),
                    certificacion.map(u -> AccionesDelCopiloto.abrirSubpaso(u.etapa().numero(), u.sub())).orElse(null));
        }
        Optional<Ubicado> ubicado = ubicar(d.subpaso(), ctx.etapas());
        if (ubicado.isEmpty()) {
            return ChatResponse.delSistema("%s se arma en el sub-paso %s, pero este contrato no lo tiene registrado."
                    .formatted(nombre, d.subpaso()));
        }
        Ubicado u = ubicado.get();
        ChatResponse.Accion abrir = AccionesDelCopiloto.abrirSubpaso(u.etapa().numero(), u.sub());
        List<DocumentoResponse> deEsteSubpaso = verbo == Verbo.DESCARGAR || u.sub().estado() == EstadoSubetapa.COMPLETADA
                ? delSubpaso(u.sub(), ctx.documentos().get())
                : List.of();
        Optional<DocumentoResponse> firmado = deEsteSubpaso.stream()
                .filter(x -> x.fechaFirma() != null)
                .max(Comparator.comparing(DocumentoResponse::fechaFirma));
        if (firmado.isPresent()) {
            return ChatResponse.delSistema("El documento %s ya está firmado en este contrato. Con el botón de abajo "
                    .formatted(nombre) + "lo descarga.", AccionesDelCopiloto.descargar(d, firmado.get()));
        }
        if (verbo == Verbo.DESCARGAR) {
            boolean hayBorrador = !deEsteSubpaso.isEmpty();
            return ChatResponse.delSistema((hayBorrador
                    ? "El documento %s todavía no está firmado: hay un borrador pendiente en el sub-paso %s. Ábralo "
                            + "ahí para revisarlo y firmarlo; firmado, se descarga desde Documentos."
                    : "El documento %s todavía no existe en este contrato: se arma en el sub-paso %s cuando usted "
                            + "pulsa «Firmar documento».").formatted(nombre, d.subpaso()), abrir);
        }
        if (u.sub().estado() == EstadoSubetapa.COMPLETADA) {
            return ChatResponse.delSistema("El sub-paso %s, donde se firma %s, ya está completado."
                    .formatted(d.subpaso(), nombre), abrir);
        }
        String intro = switch (verbo) {
            case GENERAR -> "Yo no genero ni redacto documentos en el chat: SICOT arma %s con los datos exactos del "
                    + "contrato y solo usted lo firma. Se firma en el sub-paso %s (paso %d: %s), que está %s.";
            case FIRMAR -> "Yo no puedo firmar: la firma de %s es suya y solo se aplica cuando usted la confirma. Se "
                    + "firma en el sub-paso %s (paso %d: %s), que está %s.";
            case MARCAR -> "Yo no marco sub-pasos, y este se completa al firmar %s: se firma en el sub-paso %s "
                    + "(paso %d: %s), que está %s.";
            default -> "%s se firma en el sub-paso %s (paso %d: %s), que está %s.";
        };
        return ChatResponse.delSistema(intro.formatted(nombre, d.subpaso(), u.etapa().numero(), u.etapa().nombre(),
                estado(u.sub())) + " Así se hace: " + FlujoDeFirma.COMPLETO + " Con el botón de abajo abre ese sub-paso.",
                abrir);
    }

    /** «El documento», sin decir cuál: el primero del procedimiento que no está completado. */
    private ChatResponse elDocumentoQueSigue(Verbo verbo, PreguntaNormalizada objeto, Contexto ctx) {
        // «Firma el documento del paso 3»: el de ese paso.
        Optional<EtapaResponse> paso = pasoNombrado(objeto, ctx.etapas());
        List<FichaDeDocumentoFormal.Documento> formales = FichaDeDocumentoFormal.DOCUMENTOS.stream()
                .filter(d -> d.subpaso() != null)
                .toList();
        if (paso.isPresent()) {
            String prefijo = paso.get().numero() + ".";
            return formales.stream()
                    .filter(d -> d.subpaso().startsWith(prefijo))
                    .findFirst()
                    .map(d -> sobreElDocumento(verbo, d, ctx))
                    .orElseGet(() -> ChatResponse.delSistema(
                            "En el paso %d no se firma ningún documento formal.".formatted(paso.get().numero())));
        }
        return formales.stream()
                .filter(d -> ubicar(d.subpaso(), ctx.etapas())
                        .map(u -> u.sub().estado() != EstadoSubetapa.COMPLETADA)
                        .orElse(false))
                .findFirst()
                .map(d -> sobreElDocumento(verbo, d, ctx))
                .orElseGet(() -> ChatResponse.delSistema("Los cinco documentos formales de este contrato ya están "
                        + "firmados. Puede descargarlos desde Documentos.", AccionesDelCopiloto.mostrarDocumentos()));
    }

    /**
     * El documento que nombra la orden, por su nombre o su código. «El acta» o
     * «el informe» a secas no se adivinan: se pregunta cuál.
     */
    private Optional<ChatResponse> documentoNombrado(Verbo verbo, PreguntaNormalizada objeto, Contexto ctx) {
        List<FichaDeDocumentoFormal.Documento> nombrados = FichaDeDocumentoFormal.nombrados(objeto);
        if (nombrados.size() == 1) {
            return Optional.of(sobreElDocumento(verbo, nombrados.get(0), ctx));
        }
        List<FichaDeDocumentoFormal.Documento> candidatos = nombrados.isEmpty()
                ? FichaDeDocumentoFormal.candidatosDeUnNombreAbreviado(objeto)
                : nombrados;
        if (candidatos.isEmpty()) {
            return Optional.empty();
        }
        List<String> opciones = new ArrayList<>();
        for (FichaDeDocumentoFormal.Documento d : candidatos) {
            opciones.add("«%s»%s%s".formatted(d.nombre(), d.codigo() == null ? "" : " (" + d.codigo() + ")",
                    d.subpaso() == null ? ", que no arma SICOT" : " va en el sub-paso " + d.subpaso()));
        }
        return Optional.of(ChatResponse.delSistema("¿Cuál de ellos? " + String.join(" y ", opciones)
                + ". Dígame cuál y le dejo el botón para abrirlo."));
    }

    private ChatResponse marcarElSubpaso(Ubicado u, Contexto ctx) {
        Optional<FichaDeDocumentoFormal.Documento> doc = FichaDeDocumentoFormal.delSubpaso(u.sub().codigo());
        if (doc.isPresent()) {
            return sobreElDocumento(Verbo.MARCAR, doc.get(), ctx);
        }
        ChatResponse.Accion abrir = AccionesDelCopiloto.abrirSubpaso(u.etapa().numero(), u.sub());
        if (u.sub().estado() == EstadoSubetapa.COMPLETADA) {
            return ChatResponse.delSistema("El sub-paso %s (%s) ya está completado."
                    .formatted(u.sub().codigo(), u.sub().nombre()), abrir);
        }
        return ChatResponse.delSistema(("Yo no marco sub-pasos: lo hace usted cuando lo haya verificado. Sub-paso "
                + "%s: %s, %s. %s Con el botón de abajo abre ese sub-paso.")
                .formatted(u.sub().codigo(), u.sub().nombre(), estado(u.sub()),
                        GuiaDelPasoActual.comoSeRegistra(u.sub())), abrir);
    }

    private ChatResponse marcarElPaso(EtapaResponse etapa) {
        List<SubetapaResponse> pendientes = pendientes(etapa);
        if (pendientes.isEmpty()) {
            return ChatResponse.delSistema("El paso %d: %s ya está completo.".formatted(etapa.numero(), etapa.nombre()),
                    AccionesDelCopiloto.irAPaso(etapa.numero()));
        }
        return ChatResponse.delSistema(("Yo no marco pasos: un paso se cierra cuando usted completa cada uno de "
                + "sus sub-pasos. Al paso %d: %s le quedan pendientes: %s. Con el botón de abajo abre el paso.")
                .formatted(etapa.numero(), etapa.nombre(), enumerar(pendientes)),
                AccionesDelCopiloto.irAPaso(etapa.numero()));
    }

    private ChatResponse abrirElSubpaso(Ubicado u) {
        return ChatResponse.delSistema("Sub-paso %s: %s, del paso %d, %s. Con el botón de abajo lo abre."
                        .formatted(u.sub().codigo(), u.sub().nombre(), u.etapa().numero(), estado(u.sub())),
                AccionesDelCopiloto.abrirSubpaso(u.etapa().numero(), u.sub()));
    }

    private ChatResponse abrirElPaso(EtapaResponse etapa) {
        List<SubetapaResponse> pendientes = pendientes(etapa);
        String estado = pendientes.isEmpty()
                ? "ya está completo"
                : pendientes.size() == 1 ? "le queda 1 sub-paso pendiente"
                : "le quedan %d sub-pasos pendientes".formatted(pendientes.size());
        return ChatResponse.delSistema("Paso %d: %s; %s. Con el botón de abajo lo abre."
                .formatted(etapa.numero(), etapa.nombre(), estado), AccionesDelCopiloto.irAPaso(etapa.numero()));
    }

    /** La foto de la entrega: el primero de 3.1 y 3.2 que esté pendiente. */
    private ChatResponse fotos(List<EtapaResponse> etapas) {
        List<Ubicado> deFotos = GuiaDelPasoActual.SUBPASOS_CON_EVIDENCIA_FOTOGRAFICA.stream().sorted()
                .map(codigo -> ubicar(codigo, etapas))
                .flatMap(Optional::stream)
                .toList();
        if (deFotos.isEmpty()) {
            return ChatResponse.delSistema("Este contrato no tiene registrados los sub-pasos 3.1 y 3.2, que es "
                    + "donde se cargan las fotos de la entrega.");
        }
        Optional<Ubicado> pendiente = deFotos.stream()
                .filter(u -> u.sub().estado() != EstadoSubetapa.COMPLETADA)
                .findFirst();
        if (pendiente.isEmpty()) {
            Ubicado ultimo = deFotos.get(deFotos.size() - 1);
            return ChatResponse.delSistema("Los sub-pasos de las fotos de la entrega (3.1 y 3.2) ya están "
                            + "completados. Con el botón de abajo puede abrir el %s.".formatted(ultimo.sub().codigo()),
                    AccionesDelCopiloto.abrirSubpaso(ultimo.etapa().numero(), ultimo.sub()));
        }
        Ubicado u = pendiente.get();
        return ChatResponse.delSistema(("Las fotos de la entrega se cargan en los sub-pasos 3.1 y 3.2; le queda "
                + "el %s: %s. Yo no tomo ni cargo fotos. %s Con el botón de abajo abre ese sub-paso.")
                .formatted(u.sub().codigo(), u.sub().nombre(), GuiaDelPasoActual.comoSeRegistra(u.sub())),
                AccionesDelCopiloto.abrirSubpaso(u.etapa().numero(), u.sub()));
    }

    /**
     * La pestaña de alertas, con el cálculo del cronograma si lo hay. No dice
     * si hay o no alertas: no las lee, y afirmar que no hay ninguna fue justo
     * lo que el modelo podía inventar.
     */
    private ChatResponse alertas(Contexto ctx) {
        String cronograma = ctx.cronograma().get().map(m -> " Cronograma: " + m).orElse("");
        return ChatResponse.delSistema("Las alertas de este contrato están en la pestaña Alertas; con el botón de "
                + "abajo la abre." + cronograma, AccionesDelCopiloto.mostrarAlertas());
    }

    // ─────────────────────────────────────────────────────────────────────
    // Apoyos
    // ─────────────────────────────────────────────────────────────────────

    private static Optional<Ubicado> subpasoNombrado(PreguntaNormalizada objeto, List<EtapaResponse> etapas) {
        return objeto.buscar(GuiaDelPasoActual.SUBPASO)
                .flatMap(m -> ubicar(m.group(1) + "." + m.group(2), etapas));
    }

    private static Optional<EtapaResponse> pasoNombrado(PreguntaNormalizada objeto, List<EtapaResponse> etapas) {
        Optional<MatchResult> m = objeto.buscar(GuiaDelPasoActual.PASO);
        if (m.isEmpty()) {
            return Optional.empty();
        }
        String numero = m.get().group(1);
        int n = switch (numero) {
            case "uno" -> 1;
            case "dos" -> 2;
            case "tres" -> 3;
            case "cuatro" -> 4;
            case "cinco" -> 5;
            case "seis" -> 6;
            default -> Integer.parseInt(numero);
        };
        return etapas.stream().filter(e -> e.numero() == n).findFirst();
    }

    private static Optional<Ubicado> ubicar(String codigo, List<EtapaResponse> etapas) {
        for (EtapaResponse e : etapas) {
            if (e.subEtapas() == null) {
                continue;
            }
            for (SubetapaResponse s : e.subEtapas()) {
                if (codigo.equals(s.codigo())) {
                    return Optional.of(new Ubicado(e, s));
                }
            }
        }
        return Optional.empty();
    }

    private static Optional<Ubicado> primerPendiente(List<EtapaResponse> etapas) {
        for (EtapaResponse e : etapas) {
            List<SubetapaResponse> pendientes = pendientes(e);
            if (!pendientes.isEmpty()) {
                return Optional.of(new Ubicado(e, pendientes.get(0)));
            }
        }
        return Optional.empty();
    }

    private static List<SubetapaResponse> pendientes(EtapaResponse etapa) {
        return etapa.subEtapas() == null ? List.of() : etapa.subEtapas().stream()
                .filter(s -> s.estado() != EstadoSubetapa.COMPLETADA)
                .toList();
    }

    /** Los documentos que SICOT armó en ese sub-paso: borradores y firmados. */
    private static List<DocumentoResponse> delSubpaso(SubetapaResponse sub, List<DocumentoResponse> documentos) {
        return documentos == null ? List.of() : documentos.stream()
                .filter(d -> d.generadoPorIa() && Objects.equals(d.subetapaId(), sub.id()))
                .toList();
    }

    private static String estado(SubetapaResponse s) {
        return s.estado() == EstadoSubetapa.EN_CURSO ? "en curso"
                : s.estado() == EstadoSubetapa.COMPLETADA ? "completado" : "pendiente";
    }

    private static String enumerar(List<SubetapaResponse> subs) {
        return subs.stream().map(s -> s.codigo() + " " + s.nombre()).collect(Collectors.joining("; "));
    }

    private static Map<String, Verbo> verbos() {
        Map<String, Verbo> m = new HashMap<>();
        poner(m, Verbo.GENERAR, "genera", "generar", "generame", "generarme", "genere", "haz", "hazme", "haga",
                "hagame", "redacta", "redactar", "redactame", "redacte", "elabora", "elaborar", "elaborame",
                "elabore", "arma", "armar", "armame", "arme", "crea", "crear", "creame", "prepara", "preparar",
                "preparame", "prepare", "diligencia", "diligenciar", "diligencie", "llena", "llenar", "llene");
        poner(m, Verbo.FIRMAR, "firma", "firmar", "firme", "firmame", "firmalo", "firmala");
        poner(m, Verbo.MARCAR, "marca", "marcar", "marque", "marcame", "marcalo", "marcala", "completa",
                "completar", "complete", "cierra", "cerrar", "cierre", "finaliza", "finalizar", "finalice", "aprueba",
                "aprobar", "apruebe", "chulea", "chulear");
        poner(m, Verbo.ABRIR, "abre", "abrir", "abra", "abreme", "abrirme", "llevame", "llevarme", "lleveme",
                "lleva", "llevar", "ve", "vaya", "ir", "vamos", "muestrame", "mostrarme", "muestreme", "muestra",
                "mostrar", "muestre", "ensename", "ensenarme", "ver", "mira", "mirar", "pasame", "pasa");
        poner(m, Verbo.DESCARGAR, "descarga", "descargar", "descargue", "descargame", "descargarme", "baja",
                "bajar", "bajame", "bajarme", "baje", "exporta", "exportar");
        poner(m, Verbo.CARGAR, "sube", "subir", "suba", "subeme", "carga", "cargar", "cargue", "adjunta",
                "adjuntar", "adjunte", "toma", "tomar", "tome");
        poner(m, Verbo.CAMBIAR, "cambia", "cambiar", "cambie", "pon", "poner", "ponga", "ponme", "activa",
                "activar", "active", "desactiva", "desactivar", "quita", "quitar", "modifica", "modificar",
                "modifique", "corrige", "corregir", "corrija", "edita", "editar", "edite");
        return Map.copyOf(m);
    }

    private static void poner(Map<String, Verbo> m, Verbo verbo, String... formas) {
        for (String forma : formas) {
            m.put(forma, verbo);
        }
    }
}
