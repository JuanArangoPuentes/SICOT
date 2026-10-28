package co.sena.sicot.service;

import co.sena.sicot.dto.secop.VerificacionSecopResponse.Documento;
import co.sena.sicot.dto.secop.VerificacionSecopResponse.Ficha;
import co.sena.sicot.dto.secop.VerificacionSecopResponse.Garantia;
import co.sena.sicot.dto.secop.VerificacionSecopResponse.Proveedor;
import tools.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Única puerta a los datos abiertos del SECOP II en datos.gov.co.
 *
 * <p>Por qué esta vía y no el enlace del proceso: el sitio
 * community.secop.gov.co responde con un reCAPTCHA antes de mostrar nada, así
 * que no se puede leer desde un programa —ni se debe intentar—. Colombia Compra
 * Eficiente publica la misma información como datos abiertos, con una API
 * pública, gratuita y sin registro. Cumple la regla de no depender de nada
 * pagado ni limitado, y devuelve JSON: no necesita IA para leerse.
 *
 * <p>Los datos abiertos son una fuente sucia y se tratan como tal: repiten
 * filas idénticas (la misma póliza sale dos veces) y escriben «No definido»
 * donde no hay dato. Aquí se deduplica y esos marcadores se vuelven
 * {@code null}, para que nadie compare contra el texto «No definido».
 */
@Component
public class DatosAbiertosSecop {

    /** «SECOP II - Contratos Electrónicos». La fuente fiable para valor y contratista. */
    static final String CONTRATOS = "jbjy-vk9h";

    /**
     * «SECOP II - Archivos Descarga Desde 2025»: el índice de documentos del
     * expediente. Se consulta por el proceso de compra, que es la clave con que
     * está armado, y además por el número de contrato, que solo trae poblado en
     * parte de sus filas.
     *
     * <p><b>Lo que de verdad publica.</b> El 16-10-2026 no traía nada del Lote
     * 4 (ninguna fila cargada después del 30-12-2021, pese a llamarse «desde
     * 2025»). El 23-10-2026, para el mismo proceso CO1.BDOS.10711126, trae 110
     * archivos —los de los 9 lotes, con «6. ESTUDIO PREVIO.pdf» y «8. ECONOMICA
     * LOTE 4 AJUSTADA.pdf»—: la lista se publica, pero días después que la
     * ficha. Cuando vuelve vacía hay que decir por qué, porque mientras tanto el
     * expediente se abre con el enlace del proceso.
     *
     * <p>El enlace de descarga ({@code RetrieveFile/Index?DocumentId=…})
     * responde 403 a un programa: el SECOP filtra lo que no es un navegador.
     * Hacerse pasar por uno sería saltarse ese filtro, y SICOT no lo hace: el
     * supervisor abre el documento en su navegador y, si SICOT tiene que
     * leerlo (el estudio previo del paso 2.2), lo carga.
     */
    static final String DOCUMENTOS = "dmgg-8hin";

    /** «SECOP II - Garantías». */
    static final String GARANTIAS = "gjp9-cutm";

    /**
     * «SECOP II - Proveedores Registrados»: el teléfono, el correo y la
     * dirección con que la empresa se inscribió en el SECOP II.
     *
     * <p>Es de donde sale el teléfono que pide el paso 5 de la lista de chequeo
     * de Alex («en el acta de inicio debe reposar un correo de contacto +
     * número de teléfono»): el acta del Lote 4 trae el correo y no el teléfono.
     * Medido el 22-10-2026 con el NIT 800154801: una fila, teléfono 4489948.
     *
     * <p>Trae también el correo y el teléfono personales del representante
     * legal, y la dirección. Nada de eso se lee: la hoja pide correo y
     * teléfono, y cuando el contratista es una persona natural (unas 585.000
     * filas del registro tienen una cédula por NIT) la dirección es su
     * domicilio. Un dato personal que ningún paso usa no tiene por qué viajar.
     *
     * <p>Es texto libre: hay teléfonos como «604.440.9977», «2120777 ext 1»,
     * «3747265/6», «0» o «xxxxxx», y correos sin arroba. Se normalizan o se
     * descartan aquí ({@link #telefonoUtilizable}, {@link #correoUtilizable}),
     * para que lo que llegue al contrato cumpla lo mismo que exige el
     * formulario de Gestión.
     */
    static final String PROVEEDORES = "qmzu-gj57";

    /**
     * Solo se consulta con un número de contrato bien formado. Lo que llega
     * aquí salió de un PDF, y un valor arbitrario dentro de una consulta a un
     * servicio externo es justo lo que no se quiere.
     */
    private static final Pattern ID_CONTRATO = Pattern.compile("CO1\\.PCCNTR\\.\\d{4,12}");

    /**
     * El expediente se archiva bajo el proceso de compra, que es otro
     * identificador: el contrato CO1.PCCNTR.10057820 cuelga del proceso
     * CO1.BDOS.10711126, y no se deduce uno del otro.
     */
    private static final Pattern ID_PROCESO = Pattern.compile("CO1\\.BDOS\\.\\d{4,12}");

    /** Un NIT sin dígito de verificación, como lo publica el SECOP: «800154801». */
    private static final Pattern NIT = Pattern.compile("\\d{5,12}");

    /** La misma forma que exigen CrearContratoRequest y ActualizarContratoRequest. */
    private static final Pattern TELEFONO = Pattern.compile("[0-9+() -]{7,40}");

    /** Lo mínimo de un correo, el mismo criterio que la pantalla; @Email lo vuelve a mirar al guardar. */
    private static final Pattern CORREO = Pattern.compile("[^\\s@]{1,64}@[^\\s@]+\\.[^\\s@]{2,}");

    private static final Set<String> SIN_DATO = Set.of(
            "no definido", "sin descripcion", "sin descripción", "no aplica", "no provisto", "");

    private final RestClient restClient;

    public DatosAbiertosSecop(
            @Value("${sicot.secop.datos-abiertos-url:https://www.datos.gov.co/resource}") String baseUrl,
            @Value("${sicot.secop.timeout-seconds:10}") int timeoutSeconds) {
        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(Duration.ofSeconds(timeoutSeconds));
        fabrica.setReadTimeout(Duration.ofSeconds(timeoutSeconds));
        this.restClient = RestClient.builder().baseUrl(baseUrl).requestFactory(fabrica).build();
    }

    public static boolean esNumeroDeContratoValido(String idContrato) {
        return idContrato != null && ID_CONTRATO.matcher(idContrato).matches();
    }

    public static boolean esNumeroDeProcesoValido(String idProceso) {
        return idProceso != null && ID_PROCESO.matcher(idProceso).matches();
    }

    /**
     * El NIT como lo publica el SECOP: solo los dígitos de antes del de
     * verificación. «800154801-6» y «800.154.801» dan «800154801».
     *
     * @return {@code null} si no queda un NIT con forma de NIT
     */
    public static String nitSinVerificacion(String nit) {
        if (nit == null) return null;
        String base = nit.contains("-") ? nit.substring(0, nit.indexOf('-')) : nit;
        String digitos = base.replaceAll("[^0-9]", "");
        return NIT.matcher(digitos).matches() ? digitos : null;
    }

    /** La ficha del contrato, o vacío si el SECOP todavía no lo publica. */
    public Optional<Ficha> contrato(String idContrato) {
        JsonNode filas = consultar(CONTRATOS, "id_contrato", idContrato);
        if (filas == null || !filas.isArray() || filas.isEmpty()) {
            return Optional.empty();
        }
        JsonNode f = filas.get(0);
        return Optional.of(new Ficha(
                texto(f, "id_contrato"),
                texto(f, "objeto_del_contrato"),
                texto(f, "estado_contrato"),
                texto(f, "proveedor_adjudicado"),
                texto(f, "documento_proveedor"),
                texto(f, "nombre_representante_legal"),
                numero(f, "valor_del_contrato"),
                texto(f, "duraci_n_del_contrato"),
                fecha(f, "fecha_de_firma"),
                fecha(f, "fecha_de_fin_del_contrato"),
                fecha(f, "fecha_inicio_liquidacion"),
                fecha(f, "fecha_fin_liquidacion"),
                texto(f, "proceso_de_compra"),
                texto(f, "nombre_supervisor"),
                texto(f, "nombre_ordenador_del_gasto"),
                texto(f, "modalidad_de_contratacion"),
                texto(f, "tipo_de_contrato"),
                siNo(f, "obligaci_n_ambiental"),
                numero(f, "valor_facturado"),
                numero(f, "valor_pagado"),
                numero(f, "valor_pendiente_de_pago"),
                url(f, "urlproceso")));
    }

    /**
     * El índice de documentos del expediente, sin repetidos y en orden de
     * nombre. Que venga vacío no dice que el contrato no tenga expediente: casi
     * siempre dice que estos datos abiertos no lo cubren (ver
     * {@link #DOCUMENTOS}).
     *
     * @param procesoDeCompra identificador del proceso con que está armado el
     *                        conjunto de datos; si no viene, o no tiene esa
     *                        forma, se busca solo por contrato
     */
    public List<Documento> documentos(String idContrato, String procesoDeCompra) {
        Map<String, Documento> unicos = new LinkedHashMap<>();
        acumular(unicos, consultar(DOCUMENTOS, "n_mero_de_contrato", idContrato));
        if (esNumeroDeProcesoValido(procesoDeCompra)) {
            acumular(unicos, consultar(DOCUMENTOS, "proceso", procesoDeCompra));
        }
        List<Documento> lista = new ArrayList<>(unicos.values());
        lista.sort(Comparator.comparing(Documento::nombre, String.CASE_INSENSITIVE_ORDER));
        return lista;
    }

    private void acumular(Map<String, Documento> unicos, JsonNode filas) {
        if (filas == null || !filas.isArray()) {
            return;
        }
        for (JsonNode d : filas) {
            String nombre = texto(d, "nombre_archivo");
            if (nombre == null) continue;
            String clave = Optional.ofNullable(texto(d, "id_documento")).orElse(nombre);
            BigDecimal tamano = numero(d, "tamanno_archivo");
            unicos.putIfAbsent(clave, new Documento(nombre, tamano == null ? null : tamano.longValue(),
                    fecha(d, "fecha_carga"), url(d, "url_descarga_documento")));
        }
    }

    /**
     * Las pólizas, sin las filas repetidas que publica el dataset. El subtipo
     * viene casi siempre como «No definido», así que los amparos individuales
     * (cumplimiento, calidad…) no se pueden desglosar desde aquí.
     */
    public List<Garantia> garantias(String idContrato) {
        JsonNode filas = consultar(GARANTIAS, "id_contrato", idContrato);
        Map<String, Garantia> unicas = new LinkedHashMap<>();
        if (filas != null && filas.isArray()) {
            for (JsonNode g : filas) {
                Garantia garantia = new Garantia(texto(g, "aseguradora"), texto(g, "numeropoliza"),
                        Optional.ofNullable(texto(g, "subtipopoliza")).orElse(texto(g, "tipopoliza")),
                        numero(g, "valor"), fecha(g, "fechafinpoliza"), texto(g, "estado"));
                unicas.putIfAbsent(garantia.numero() + "|" + garantia.valor() + "|" + garantia.vigenteHasta(), garantia);
            }
        }
        return new ArrayList<>(unicas.values());
    }

    /**
     * Cómo contactar a la empresa según su inscripción en el SECOP II, o vacío
     * si no se encuentra con ese NIT. Si el registro trae más de una fila,
     * gana la de la cuenta activa.
     *
     * <p>Se busca el NIT a secas y también con el dígito de verificación
     * pegado: unas 7.500 filas del registro lo guardan así («7838601-0»), y
     * por igualdad exacta esos contratistas parecían no estar inscritos.
     */
    public Optional<Proveedor> proveedor(String nit) {
        String limpio = nitSinVerificacion(nit);
        if (limpio == null) {
            return Optional.empty();
        }
        // El NIT ya es solo dígitos (nitSinVerificacion): no puede cerrar la
        // comilla ni meter otra condición en la consulta.
        JsonNode filas = consultarDonde(PROVEEDORES,
                "nit='%s' OR starts_with(nit,'%s-')".formatted(limpio, limpio));
        if (filas == null || !filas.isArray() || filas.isEmpty()) {
            return Optional.empty();
        }
        JsonNode elegida = filas.get(0);
        for (JsonNode f : filas) {
            if (Boolean.TRUE.equals(siNo(f, "esta_activa"))) {
                elegida = f;
                break;
            }
        }
        return Optional.of(new Proveedor(
                texto(elegida, "nombre"),
                texto(elegida, "nit"),
                telefonoUtilizable(texto(elegida, "telefono")),
                correoUtilizable(texto(elegida, "correo"))));
    }

    /**
     * El teléfono del registro, si se puede usar tal cual en el contrato.
     * Se cortan la extensión y lo que va después de una barra (el registro
     * escribe «3747265/6» o «8353513 / 3183309330»: se toma el primero), los
     * puntos de «604.440.9977» se vuelven espacios y se quita el punto final.
     * Si no quedan al menos 7 dígitos, o son todos ceros («0», «0000000»), no
     * es un teléfono: {@code null}, y quien pregunta dice que no lo hay.
     */
    static String telefonoUtilizable(String crudo) {
        if (crudo == null) return null;
        String t = crudo.strip();
        int ext = t.toLowerCase(Locale.ROOT).indexOf("ext");
        if (ext >= 0) t = t.substring(0, ext);
        int barra = t.indexOf('/');
        if (barra >= 0) t = t.substring(0, barra);
        t = t.replace('.', ' ').replaceAll("\\s+", " ").strip();
        String digitos = t.replaceAll("[^0-9]", "");
        if (!TELEFONO.matcher(t).matches() || digitos.length() < 7 || digitos.matches("0+")) {
            return null;
        }
        return t;
    }

    /** El correo del registro en minúsculas, o {@code null} si no tiene forma de correo («x2018gmail.com»). */
    static String correoUtilizable(String crudo) {
        if (crudo == null) return null;
        String c = crudo.strip().toLowerCase(Locale.ROOT);
        return c.length() <= 255 && CORREO.matcher(c).matches() ? c : null;
    }

    /**
     * Una consulta con condición SoQL armada aquí, para lo que no es una
     * igualdad. Quien llama arma la condición solo con valores ya validados
     * (dígitos): nada que venga de un PDF entra sin pasar por un patrón.
     */
    private JsonNode consultarDonde(String dataset, String condicion) {
        try {
            return restClient.get()
                    .uri(u -> u.path("/{dataset}.json")
                            .queryParam("$where", "{condicion}")
                            .queryParam("$limit", 10)
                            .build(dataset, condicion))
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientException e) {
            throw new NoDisponible("No se pudo consultar datos.gov.co: " + e.getMessage(), e);
        }
    }

    private JsonNode consultar(String dataset, String campo, String identificador) {
        if (!esNumeroDeContratoValido(identificador) && !esNumeroDeProcesoValido(identificador)) {
            throw new IllegalArgumentException(
                    "Identificador no válido para consultar el SECOP: " + identificador);
        }
        try {
            return restClient.get()
                    .uri(u -> u.path("/{dataset}.json")
                            .queryParam(campo, identificador)
                            .queryParam("$limit", 100)
                            .build(dataset))
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientException e) {
            throw new NoDisponible("No se pudo consultar datos.gov.co: " + e.getMessage(), e);
        }
    }

    static String texto(JsonNode fila, String campo) {
        JsonNode v = fila.get(campo);
        if (v == null || v.isNull() || v.isContainer()) {
            return null;
        }
        String t = v.asString().strip();
        return SIN_DATO.contains(t.toLowerCase()) ? null : t;
    }

    static BigDecimal numero(JsonNode fila, String campo) {
        String t = texto(fila, campo);
        if (t == null) return null;
        try {
            return new BigDecimal(t);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Las fechas llegan como «2026-12-15T00:00:00.000»; interesa solo el día. */
    static LocalDate fecha(JsonNode fila, String campo) {
        String t = texto(fila, campo);
        if (t == null || t.length() < 10) return null;
        try {
            return LocalDate.parse(t.substring(0, 10));
        } catch (RuntimeException e) {
            return null;
        }
    }

    static Boolean siNo(JsonNode fila, String campo) {
        String t = texto(fila, campo);
        if (t == null) return null;
        if (t.equalsIgnoreCase("si") || t.equalsIgnoreCase("sí")) return true;
        if (t.equalsIgnoreCase("no")) return false;
        return null;
    }

    /**
     * Los enlaces vienen envueltos: {@code {"url": "https://…"}}. Solo se
     * aceptan los del SECOP: terminan como enlaces que el supervisor abre desde
     * el panel, y un dato externo no debe poder convertirse en un enlace a otro
     * sitio —o en un {@code javascript:}— por mucho que la fuente sea oficial.
     *
     * <p>Ojo con los de descarga de documentos
     * ({@code /Public/Archive/RetrieveFile/Index?DocumentId=…}): responden 403 a
     * un programa. Valen para que el supervisor los abra en su navegador, no
     * para que SICOT baje el archivo.
     */
    static String url(JsonNode fila, String campo) {
        JsonNode v = fila.get(campo);
        if (v == null || v.isNull()) return null;
        JsonNode u = v.isObject() ? v.get("url") : v;
        if (u == null || u.isNull()) return null;
        String enlace = u.asString().strip();
        return enlace.startsWith("https://community.secop.gov.co/") ? enlace : null;
    }

    /** datos.gov.co no respondió: sin conexión, caído o lento. */
    public static class NoDisponible extends RuntimeException {
        public NoDisponible(String mensaje, Throwable causa) {
            super(mensaje, causa);
        }
    }
}
