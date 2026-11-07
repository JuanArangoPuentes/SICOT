package co.sena.sicot.controller;

import co.sena.sicot.dto.contacto.ContactosDelContratoResponse;
import co.sena.sicot.dto.contacto.GuardarContactosRequest;
import co.sena.sicot.dto.contacto.LecturaDeContactosResponse;
import co.sena.sicot.dto.contrato.ContratoResponse;
import co.sena.sicot.dto.entrega.AcuerdoDeEntregasResponse;
import co.sena.sicot.dto.entrega.GuardarAcuerdoDeEntregasRequest;
import co.sena.sicot.exception.ErrorResponse;
import co.sena.sicot.service.AcuerdoDeEntregasService;
import co.sena.sicot.service.ContactoDelContratistaService;
import co.sena.sicot.service.ContactosDelContratoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Lo que el supervisor registra al arrancar el contrato, en los pasos 2.2 y 2.4
 * del proceso real: los profesionales de SST y ambiental, las fechas de entrega
 * acordadas con el contratista y el teléfono del contratista.
 *
 * <p>Sale de la lista de chequeo de Alex Zapata (pasos 3 y 5 de su hoja). Va en
 * un controlador aparte de {@link ContratoController} porque no es la ficha del
 * contrato, que edita Gestión, sino lo que el supervisor acuerda al empezar.
 *
 * <p>Ninguna ruta lleva {@code @PreAuthorize}, igual que la solicitud de pedido:
 * cada servicio llama a {@code ContratoService.buscar}, que restringe al
 * SUPERVISOR a sus contratos; Gestión y Administración ven todos.
 */
@RestController
@RequestMapping("/api/contratos/{id}")
@Tag(name = "Arranque del contrato", description = "Pasos 2.2 y 2.4: contactos de SST y ambiental, acuerdo de entregas")
public class ArranqueDelContratoController {

    private final AcuerdoDeEntregasService acuerdoDeEntregas;
    private final ContactosDelContratoService contactos;
    private final ContactoDelContratistaService contactoDelContratista;

    public ArranqueDelContratoController(AcuerdoDeEntregasService acuerdoDeEntregas,
                                         ContactosDelContratoService contactos,
                                         ContactoDelContratistaService contactoDelContratista) {
        this.acuerdoDeEntregas = acuerdoDeEntregas;
        this.contactos = contactos;
        this.contactoDelContratista = contactoDelContratista;
    }

    @Operation(summary = "Fechas de entrega acordadas con el contratista (paso 2.4), con el plazo del contrato")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "El acuerdo; vacío si todavía no hay fechas",
                    content = @Content(schema = @Schema(implementation = AcuerdoDeEntregasResponse.class))),
            @ApiResponse(responseCode = "404", description = "Contrato no encontrado o sin acceso",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/entregas-acordadas")
    public ResponseEntity<AcuerdoDeEntregasResponse> obtenerEntregas(@PathVariable Long id) {
        return ResponseEntity.ok(acuerdoDeEntregas.obtener(id));
    }

    @Operation(summary = "Guardar las fechas de entrega acordadas (reemplaza las anteriores)",
            description = "Las fechas tienen que caer dentro del plazo del contrato; SICOT las numera por fecha.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "El acuerdo guardado",
                    content = @Content(schema = @Schema(implementation = AcuerdoDeEntregasResponse.class))),
            @ApiResponse(responseCode = "400", description = "Una fecha fuera del plazo, o una entrega sin descripción",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Contrato no encontrado o sin acceso",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PutMapping("/entregas-acordadas")
    public ResponseEntity<AcuerdoDeEntregasResponse> guardarEntregas(
            @PathVariable Long id, @Valid @RequestBody GuardarAcuerdoDeEntregasRequest request) {
        return ResponseEntity.ok(acuerdoDeEntregas.guardar(id, request));
    }

    @Operation(summary = "Profesionales de SST y ambiental del contrato (paso 2.2)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Los contactos; vacío si no se han registrado",
                    content = @Content(schema = @Schema(implementation = ContactosDelContratoResponse.class))),
            @ApiResponse(responseCode = "404", description = "Contrato no encontrado o sin acceso",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/contactos-sst-ambiental")
    public ResponseEntity<ContactosDelContratoResponse> obtenerContactos(@PathVariable Long id) {
        return ResponseEntity.ok(contactos.obtener(id));
    }

    @Operation(summary = "Guardar los profesionales de SST y ambiental (reemplaza los anteriores)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Los contactos guardados",
                    content = @Content(schema = @Schema(implementation = ContactosDelContratoResponse.class))),
            @ApiResponse(responseCode = "400", description = "Un correo inválido o sin rol",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Contrato no encontrado o sin acceso",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PutMapping("/contactos-sst-ambiental")
    public ResponseEntity<ContactosDelContratoResponse> guardarContactos(
            @PathVariable Long id, @Valid @RequestBody GuardarContactosRequest request) {
        return ResponseEntity.ok(contactos.guardar(id, request));
    }

    @Operation(summary = "Proponer los correos de SST y ambiental que trae el estudio previo",
            description = "Lee el PDF o Word que carga el supervisor y devuelve los correos que encuentra, con el rol"
                    + " que sugiere el texto cercano. No guarda el archivo ni los contactos.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Los correos encontrados, o por qué no hay ninguno",
                    content = @Content(schema = @Schema(implementation = LecturaDeContactosResponse.class))),
            @ApiResponse(responseCode = "400", description = "Archivo vacío, de otro tipo o de más de 20 MB",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Contrato no encontrado o sin acceso",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping(value = "/contactos-sst-ambiental/lectura", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<LecturaDeContactosResponse> leerContactos(
            @PathVariable Long id, @RequestParam("archivo") MultipartFile archivo) {
        return ResponseEntity.ok(contactos.leerDelDocumento(id, archivo));
    }

    @Operation(summary = "Completar el teléfono y el correo del contratista desde el registro de proveedores del SECOP II",
            description = "Solo llena lo que está vacío: el correo del acta de inicio nunca se reemplaza.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "El contrato, con lo que se pudo completar",
                    content = @Content(schema = @Schema(implementation = ContratoResponse.class))),
            @ApiResponse(responseCode = "400", description = "Sin NIT, NIT no registrado, sin teléfono publicado o sin "
                    + "conexión con datos.gov.co",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Contrato no encontrado o sin acceso",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/contacto-contratista/desde-secop")
    public ResponseEntity<ContratoResponse> completarContactoDelContratista(@PathVariable Long id) {
        return ResponseEntity.ok(contactoDelContratista.completarDesdeSecop(id));
    }
}
