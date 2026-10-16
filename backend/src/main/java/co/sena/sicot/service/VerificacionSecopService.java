package co.sena.sicot.service;

import co.sena.sicot.dto.secop.VerificacionSecopResponse;
import co.sena.sicot.dto.secop.VerificacionSecopResponse.Diferencia;
import co.sena.sicot.dto.secop.VerificacionSecopResponse.Ficha;
import co.sena.sicot.entity.Contrato;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.Normalizer;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Paso 2.3 del proceso real: verificar el contrato contra lo que publica el
 * SECOP II.
 *
 * <p>El supervisor tenía que entrar al SECOP, buscar el contrato y comparar a
 * ojo cifra por cifra. Aquí se le trae lo publicado y se le señalan las
 * diferencias con lo registrado en SICOT. La verificación sigue siendo suya:
 * una diferencia puede ser un error de digitación en SICOT o un dato que el
 * SECOP todavía no actualiza, y eso lo decide él.
 *
 * <p>Nunca falla por falta de red: el supervisor trabaja con una instalación
 * local que puede estar sin conexión. Entonces responde «no disponible» con el
 * motivo, y el enlace del proceso sigue ahí para hacerlo a mano.
 */
@Service
public class VerificacionSecopService {

    private static final Logger log = LoggerFactory.getLogger(VerificacionSecopService.class);

    private static final DateTimeFormatter DIA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private static final String SIN_DATO = "(sin dato)";

    private final ContratoService contratoService;
    private final DatosAbiertosSecop datosAbiertos;
    private final Clock reloj;

    public VerificacionSecopService(ContratoService contratoService, DatosAbiertosSecop datosAbiertos, Clock reloj) {
        this.contratoService = contratoService;
        this.datosAbiertos = datosAbiertos;
        this.reloj = reloj;
    }

    @Transactional(readOnly = true)
    public VerificacionSecopResponse verificar(Long contratoId) {
        // buscar() aplica verificarAccesoAlContrato: un SUPERVISOR solo
        // alcanza el contrato que tiene asignado.
        Contrato contrato = contratoService.buscar(contratoId);
        Instant ahora = reloj.instant();
        String enlace = contrato.getEnlaceSecop();
        String numero = contrato.getNumeroContrato();

        if (!DatosAbiertosSecop.esNumeroDeContratoValido(numero)) {
            return VerificacionSecopResponse.noDisponible("El número «" + numero + "» no tiene la forma de un "
                    + "contrato de SECOP II (CO1.PCCNTR.…), así que no se puede buscar en los datos abiertos.",
                    ahora, enlace);
        }
        try {
            Optional<Ficha> encontrada = datosAbiertos.contrato(numero);
            if (encontrada.isEmpty()) {
                return VerificacionSecopResponse.noDisponible("El SECOP II todavía no publica el contrato " + numero
                        + " en sus datos abiertos. Suele aparecer unos días después de la firma; mientras tanto "
                        + "la verificación se hace con el enlace del proceso.", ahora, enlace);
            }
            Ficha ficha = encontrada.get();
            return new VerificacionSecopResponse(true, null, ahora,
                    enlace != null ? enlace : ficha.enlaceProceso(),
                    ficha, comparar(contrato, ficha),
                    datosAbiertos.documentos(numero), datosAbiertos.garantias(numero));
        } catch (DatosAbiertosSecop.NoDisponible e) {
            log.warn("Verificación del contrato {} contra el SECOP sin conexión: {}", numero, e.getMessage());
            return VerificacionSecopResponse.noDisponible("No hay conexión con datos.gov.co en este momento. "
                    + "La verificación se puede hacer a mano con el enlace del proceso.", ahora, enlace);
        }
    }

    /**
     * Los campos en que SICOT y el SECOP no coinciden. Si el SECOP no publica un
     * dato no se compara: «no publicado» no es una diferencia. Si lo publica y
     * SICOT no lo tiene, sí se señala, porque es un dato que falta registrar.
     */
    static List<Diferencia> comparar(Contrato c, Ficha f) {
        List<Diferencia> diferencias = new ArrayList<>();
        if (f.valor() != null && (c.getValor() == null || c.getValor().compareTo(f.valor()) != 0)) {
            diferencias.add(new Diferencia("Valor", pesos(c.getValor()), pesos(f.valor())));
        }
        if (f.fechaFin() != null && !f.fechaFin().equals(c.getFechaFin())) {
            diferencias.add(new Diferencia("Fecha de terminación", dia(c.getFechaFin()), dia(f.fechaFin())));
        }
        if (f.proveedor() != null && !compacto(f.proveedor()).equals(compacto(c.getContratista()))) {
            diferencias.add(new Diferencia("Contratista", texto(c.getContratista()), f.proveedor()));
        }
        if (f.documentoProveedor() != null && !mismoNit(c.getContratistaNit(), f.documentoProveedor())) {
            diferencias.add(new Diferencia("NIT del contratista", texto(c.getContratistaNit()), f.documentoProveedor()));
        }
        if (f.representanteLegal() != null && !mismaPersona(c.getRepresentanteLegal(), f.representanteLegal())) {
            diferencias.add(new Diferencia("Representante legal", texto(c.getRepresentanteLegal()),
                    f.representanteLegal()));
        }
        String supervisor = c.getSupervisor() == null ? null : c.getSupervisor().getNombre();
        if (f.supervisor() != null && !mismaPersona(supervisor, f.supervisor())) {
            diferencias.add(new Diferencia("Supervisor", texto(supervisor), f.supervisor()));
        }
        return diferencias;
    }

    /**
     * Mismo contratista aunque uno escriba «S.A.S» y el otro «SAS»: se comparan
     * solo letras y dígitos, sin tildes ni mayúsculas.
     */
    static String compacto(String v) {
        if (v == null) return "";
        return sinTildes(v).toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
    }

    /**
     * El SECOP publica el NIT sin dígito de verificación («800154801») y el acta
     * lo trae con él («800154801-6»): son el mismo NIT.
     */
    static boolean mismoNit(String enSicot, String enSecop) {
        if (enSicot == null) return false;
        String base = enSicot.contains("-") ? enSicot.substring(0, enSicot.indexOf('-')) : enSicot;
        return base.replaceAll("[^0-9]", "").equals(enSecop.replaceAll("[^0-9]", ""));
    }

    /**
     * Misma persona si todos los nombres del más corto están en el más largo:
     * el usuario de SICOT puede llamarse «Alex Zapata» y el SECOP publicar
     * «Alex Fernando Zapata Rios». Señalar eso como diferencia sería ruido que
     * enseña al supervisor a ignorar las diferencias de verdad.
     */
    static boolean mismaPersona(String a, String b) {
        if (a == null || b == null) return false;
        Set<String> uno = palabras(a);
        Set<String> otro = palabras(b);
        // Con un solo nombre en común no basta: un usuario registrado solo
        // como «Alex» no es por eso la misma persona que «Alex Fernando Zapata
        // Rios», y darlos por iguales escondería justo la diferencia que hay
        // que ver.
        if (Math.min(uno.size(), otro.size()) < 2) return false;
        return uno.size() <= otro.size() ? otro.containsAll(uno) : uno.containsAll(otro);
    }

    private static Set<String> palabras(String v) {
        return new HashSet<>(Arrays.asList(
                sinTildes(v).toUpperCase(Locale.ROOT).replaceAll("[^A-Z ]", " ").trim().split("\\s+")));
    }

    private static String sinTildes(String v) {
        return Normalizer.normalize(v, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    }

    static String pesos(BigDecimal v) {
        if (v == null) return SIN_DATO;
        DecimalFormatSymbols simbolos = new DecimalFormatSymbols(Locale.ROOT);
        simbolos.setGroupingSeparator('.');
        simbolos.setDecimalSeparator(',');
        return "$" + new DecimalFormat("#,##0", simbolos).format(v);
    }

    private static String dia(LocalDate v) {
        return v == null ? SIN_DATO : DIA.format(v);
    }

    private static String texto(String v) {
        return v == null || v.isBlank() ? SIN_DATO : v;
    }
}
