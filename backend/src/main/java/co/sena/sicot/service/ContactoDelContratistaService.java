package co.sena.sicot.service;

import co.sena.sicot.dto.contrato.ContratoResponse;
import co.sena.sicot.dto.secop.VerificacionSecopResponse.Proveedor;
import co.sena.sicot.entity.Contrato;
import co.sena.sicot.exception.BusinessException;
import co.sena.sicot.mapper.ContratoMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Completa el teléfono y el correo del contratista con su inscripción en el
 * registro de proveedores del SECOP II.
 *
 * <h2>Por qué existe</h2>
 * El paso 5 de la lista de chequeo de Alex Zapata dice que en el acta de inicio
 * «debe reposar un correo de contacto + número de teléfono». El acta del Lote 4
 * trae el correo y no el teléfono; el registro de proveedores sí lo publica. A
 * los contratos nuevos se les completa al registrarlos (la extracción de
 * Gestión); esto es para los que ya estaban registrados, y lo pide el
 * supervisor desde el paso 2.4.
 *
 * <h2>Qué no hace</h2>
 * No pisa nada: solo llena lo que está vacío. El correo del acta es el que
 * Contratación le dio al supervisor, y si difiere del registro, manda el acta,
 * igual que en la extracción («lo leído del documento siempre gana»).
 */
@Service
public class ContactoDelContratistaService {

    private static final Logger log = LoggerFactory.getLogger(ContactoDelContratistaService.class);

    private final ContratoService contratoService;
    private final DatosAbiertosSecop datosAbiertos;
    private final RegistroService registroService;

    public ContactoDelContratistaService(ContratoService contratoService, DatosAbiertosSecop datosAbiertos,
                                         RegistroService registroService) {
        this.contratoService = contratoService;
        this.datosAbiertos = datosAbiertos;
        this.registroService = registroService;
    }

    /**
     * El acceso lo restringe {@code ContratoService.buscar}: el supervisor solo
     * completa sus contratos.
     */
    @Transactional
    public ContratoResponse completarDesdeSecop(Long contratoId) {
        Contrato contrato = contratoService.buscar(contratoId);
        String nit = contrato.getContratistaNit();
        if (DatosAbiertosSecop.nitSinVerificacion(nit) == null) {
            throw new BusinessException("El contrato no tiene registrado el NIT del contratista, y es con el NIT "
                    + "como se busca en el registro de proveedores del SECOP II. Pídale a Gestión que lo complete.");
        }
        Proveedor proveedor;
        try {
            proveedor = datosAbiertos.proveedor(nit).orElseThrow(() -> new BusinessException(
                    "El NIT " + nit + " no aparece en el registro de proveedores del SECOP II."));
        } catch (DatosAbiertosSecop.NoDisponible e) {
            log.warn("Registro de proveedores no consultado para el contrato {}: {}", contratoId, e.getMessage());
            throw new BusinessException("No hay conexión con datos.gov.co en este momento. Intente más tarde, o "
                    + "pídale el teléfono al contratista en el primer correo.");
        }

        List<String> completados = new ArrayList<>();
        // 40 es el ancho de la columna: un teléfono más largo no es un teléfono.
        if (vacio(contrato.getTelefonoContratista()) && proveedor.telefono() != null
                && proveedor.telefono().length() <= 40) {
            contrato.setTelefonoContratista(proveedor.telefono());
            completados.add("el teléfono (" + proveedor.telefono() + ")");
        }
        if (vacio(contrato.getCorreoContratista()) && proveedor.correo() != null) {
            contrato.setCorreoContratista(proveedor.correo());
            completados.add("el correo (" + proveedor.correo() + ")");
        }
        if (completados.isEmpty()) {
            if (proveedor.telefono() == null && vacio(contrato.getTelefonoContratista())) {
                throw new BusinessException("El registro de proveedores del SECOP II no publica el teléfono de "
                        + "este contratista. Pídaselo en el primer correo.");
            }
            return ContratoMapper.toResponse(contrato);
        }
        registroService.registrar(contrato, "CONTACTO_CONTRATISTA_COMPLETADO",
                "Se completó " + String.join(" y ", completados) + " del contratista desde el registro de "
                        + "proveedores del SECOP II.");
        return ContratoMapper.toResponse(contrato);
    }

    private static boolean vacio(String valor) {
        return valor == null || valor.isBlank();
    }
}
