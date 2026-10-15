package co.sena.sicot.service;

import co.sena.sicot.dto.secop.VerificacionSecopResponse.Documento;
import co.sena.sicot.dto.secop.VerificacionSecopResponse.Ficha;
import co.sena.sicot.dto.secop.VerificacionSecopResponse.Garantia;
import com.fasterxml.jackson.databind.JsonNode;
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

    /** «SECOP II - Archivos Descarga Desde 2025»: el índice de documentos del expediente. */
    static final String DOCUMENTOS = "dmgg-8hin";

    /** «SECOP II - Garantías». */
    static final String GARANTIAS = "gjp9-cutm";

    /**
     * Solo se consulta con un número de contrato bien formado. Lo que llega
     * aquí salió de un PDF, y un valor arbitrario dentro de una consulta a un
     * servicio externo es justo lo que no se quiere.
     */
    private static final Pattern ID_CONTRATO = Pattern.compile("CO1\\.PCCNTR\\.\\d{4,12}");

    private static final Set<String> SIN_DATO = Set.of(
            "no definido", "sin descripcion", "sin descripción", "no aplica", "");

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

    /** El índice de documentos del expediente, sin repetidos y en orden de nombre. */
    public List<Documento> documentos(String idContrato) {
        JsonNode filas = consultar(DOCUMENTOS, "n_mero_de_contrato", idContrato);
        Map<String, Documento> unicos = new LinkedHashMap<>();
        if (filas != null && filas.isArray()) {
            for (JsonNode d : filas) {
                String nombre = texto(d, "nombre_archivo");
                if (nombre == null) continue;
                String clave = Optional.ofNullable(texto(d, "id_documento")).orElse(nombre);
                BigDecimal tamano = numero(d, "tamanno_archivo");
                unicos.putIfAbsent(clave, new Documento(nombre, tamano == null ? null : tamano.longValue(),
                        fecha(d, "fecha_carga"), url(d, "url_descarga_documento")));
            }
        }
        List<Documento> lista = new ArrayList<>(unicos.values());
        lista.sort(Comparator.comparing(Documento::nombre, String.CASE_INSENSITIVE_ORDER));
        return lista;
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

    private JsonNode consultar(String dataset, String campo, String idContrato) {
        if (!esNumeroDeContratoValido(idContrato)) {
            throw new IllegalArgumentException("Número de contrato no válido para consultar el SECOP: " + idContrato);
        }
        try {
            return restClient.get()
                    .uri(u -> u.path("/{dataset}.json")
                            .queryParam(campo, idContrato)
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
        if (v == null || v.isNull() || v.isContainerNode()) {
            return null;
        }
        String t = v.asText().strip();
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
     */
    static String url(JsonNode fila, String campo) {
        JsonNode v = fila.get(campo);
        if (v == null || v.isNull()) return null;
        JsonNode u = v.isObject() ? v.get("url") : v;
        if (u == null || u.isNull()) return null;
        String enlace = u.asText().strip();
        return enlace.startsWith("https://community.secop.gov.co/") ? enlace : null;
    }

    /** datos.gov.co no respondió: sin conexión, caído o lento. */
    public static class NoDisponible extends RuntimeException {
        public NoDisponible(String mensaje, Throwable causa) {
            super(mensaje, causa);
        }
    }
}
