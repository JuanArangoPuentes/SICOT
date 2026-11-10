package co.sena.sicot.controller;

import co.sena.sicot.dto.contrato.ActualizarContratoRequest;
import co.sena.sicot.dto.contrato.AsignarSupervisorRequest;
import co.sena.sicot.dto.contrato.InvitacionDeSupervisorResponse;
import co.sena.sicot.dto.contrato.InvitarSupervisorRequest;
import co.sena.sicot.dto.contrato.CambiarEstadoContratoRequest;
import co.sena.sicot.dto.contrato.ContratoResponse;
import co.sena.sicot.dto.contrato.CrearContratoRequest;
import co.sena.sicot.entity.enums.EstadoContrato;
import co.sena.sicot.dto.cronograma.CronogramaResponse;
import co.sena.sicot.dto.pedido.GuardarSolicitudDePedidoRequest;
import co.sena.sicot.dto.pedido.SolicitudDePedidoResponse;
import co.sena.sicot.dto.secop.VerificacionSecopResponse;
import co.sena.sicot.service.ContratoService;
import co.sena.sicot.service.CronogramaService;
import co.sena.sicot.service.NombreDeDescarga;
import co.sena.sicot.service.SolicitudDePedidoService;
import co.sena.sicot.service.VerificacionSecopService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/contratos")
@Tag(name = "Contratos", description = "Gestión de contratos: consulta, creación y trámites")
public class ContratoController {

    private final ContratoService contratoService;
    private final CronogramaService cronogramaService;
    private final VerificacionSecopService verificacionSecop;
    private final SolicitudDePedidoService solicitudDePedido;

    public ContratoController(ContratoService contratoService, CronogramaService cronogramaService,
                              VerificacionSecopService verificacionSecop,
                              SolicitudDePedidoService solicitudDePedido) {
        this.cronogramaService = cronogramaService;
        this.contratoService = contratoService;
        this.verificacionSecop = verificacionSecop;
        this.solicitudDePedido = solicitudDePedido;
    }

    @Operation(summary = "Listar contratos", description = "Filtros opcionales por supervisor y estado.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lista de contratos",
                    content = @Content(schema = @Schema(implementation = ContratoResponse.class))),
            @ApiResponse(responseCode = "401", description = "No autenticado",
                    content = @Content(schema = @Schema(implementation = co.sena.sicot.exception.ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Error interno del servidor",
                    content = @Content(schema = @Schema(implementation = co.sena.sicot.exception.ErrorResponse.class)))
    })
    @GetMapping
    public ResponseEntity<List<ContratoResponse>> listar(
            @Parameter(description = "Id del supervisor asignado")
            @RequestParam(required = false) Long supervisorId,
            @Parameter(description = "Estado del contrato")
            @RequestParam(required = false) EstadoContrato estado) {
        return ResponseEntity.ok(contratoService.listar(supervisorId, estado));
    }

    @Operation(summary = "Obtener contrato por id")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Contrato encontrado",
                    content = @Content(schema = @Schema(implementation = ContratoResponse.class))),
            @ApiResponse(responseCode = "401", description = "No autenticado",
                    content = @Content(schema = @Schema(implementation = co.sena.sicot.exception.ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Sin acceso a este contrato",
                    content = @Content(schema = @Schema(implementation = co.sena.sicot.exception.ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Contrato no encontrado",
                    content = @Content(schema = @Schema(implementation = co.sena.sicot.exception.ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Error interno del servidor",
                    content = @Content(schema = @Schema(implementation = co.sena.sicot.exception.ErrorResponse.class)))
    })
    @GetMapping("/{id}")
    public ResponseEntity<ContratoResponse> obtener(@PathVariable Long id) {
        return ResponseEntity.ok(contratoService.obtener(id));
    }

    @Operation(summary = "Crear contrato")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Contrato creado",
                    content = @Content(schema = @Schema(implementation = ContratoResponse.class))),
            @ApiResponse(responseCode = "400", description = "Datos inválidos (validación o número de contrato duplicado)",
                    content = @Content(schema = @Schema(implementation = co.sena.sicot.exception.ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "No autenticado",
                    content = @Content(schema = @Schema(implementation = co.sena.sicot.exception.ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Sin rol GESTION ni ADMINISTRADOR",
                    content = @Content(schema = @Schema(implementation = co.sena.sicot.exception.ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Error interno del servidor",
                    content = @Content(schema = @Schema(implementation = co.sena.sicot.exception.ErrorResponse.class)))
    })
    @PostMapping
    @PreAuthorize("hasAnyRole('GESTION', 'ADMINISTRADOR')")
    public ResponseEntity<ContratoResponse> crear(@Valid @RequestBody CrearContratoRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(contratoService.crear(request));
    }

    @Operation(summary = "Actualizar contrato")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Contrato actualizado",
                    content = @Content(schema = @Schema(implementation = ContratoResponse.class))),
            @ApiResponse(responseCode = "400", description = "Datos inválidos",
                    content = @Content(schema = @Schema(implementation = co.sena.sicot.exception.ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "No autenticado",
                    content = @Content(schema = @Schema(implementation = co.sena.sicot.exception.ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Sin rol GESTION/ADMINISTRADOR o sin acceso al contrato",
                    content = @Content(schema = @Schema(implementation = co.sena.sicot.exception.ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Contrato no encontrado",
                    content = @Content(schema = @Schema(implementation = co.sena.sicot.exception.ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Error interno del servidor",
                    content = @Content(schema = @Schema(implementation = co.sena.sicot.exception.ErrorResponse.class)))
    })
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('GESTION', 'ADMINISTRADOR')")
    public ResponseEntity<ContratoResponse> actualizar(@PathVariable Long id,
                                                       @Valid @RequestBody ActualizarContratoRequest request) {
        return ResponseEntity.ok(contratoService.actualizar(id, request));
    }

    @Operation(summary = "Asignar supervisor al contrato")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Supervisor asignado",
                    content = @Content(schema = @Schema(implementation = ContratoResponse.class))),
            @ApiResponse(responseCode = "400", description = "Datos inválidos (usuario no es SUPERVISOR)",
                    content = @Content(schema = @Schema(implementation = co.sena.sicot.exception.ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "No autenticado",
                    content = @Content(schema = @Schema(implementation = co.sena.sicot.exception.ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Sin rol GESTION/ADMINISTRADOR",
                    content = @Content(schema = @Schema(implementation = co.sena.sicot.exception.ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Contrato o supervisor no encontrado",
                    content = @Content(schema = @Schema(implementation = co.sena.sicot.exception.ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Error interno del servidor",
                    content = @Content(schema = @Schema(implementation = co.sena.sicot.exception.ErrorResponse.class)))
    })
    @PatchMapping("/{id}/supervisor")
    @PreAuthorize("hasAnyRole('GESTION', 'ADMINISTRADOR')")
    public ResponseEntity<ContratoResponse> asignarSupervisor(@PathVariable Long id,
                                                              @Valid @RequestBody AsignarSupervisorRequest request) {
        return ResponseEntity.ok(contratoService.asignarSupervisor(id, request));
    }

    @Operation(summary = "Poner un supervisor en el contrato por su correo, creándole la cuenta si no la tiene",
            description = "Para cuando el supervisor todavía no está en SICOT. Si el correo no existe, se le crea "
                    + "la cuenta —siempre con rol SUPERVISOR, nunca otro— y se le envían por correo el usuario y "
                    + "la clave con los que entra después de instalar SICOT. Si el correo ya es de un supervisor, "
                    + "solo se le asigna el contrato y conserva su clave. La respuesta dice si el correo salió; "
                    + "cuando no sale, trae la clave una sola vez para que Gestión se la entregue.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Supervisor asignado, con qué pasó con su cuenta",
                    content = @Content(schema = @Schema(implementation = InvitacionDeSupervisorResponse.class))),
            @ApiResponse(responseCode = "400", description = "Datos inválidos, o el correo es de una cuenta que no "
                    + "es de supervisor o está inactiva",
                    content = @Content(schema = @Schema(implementation = co.sena.sicot.exception.ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "No autenticado",
                    content = @Content(schema = @Schema(implementation = co.sena.sicot.exception.ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Sin rol GESTION/ADMINISTRADOR",
                    content = @Content(schema = @Schema(implementation = co.sena.sicot.exception.ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Contrato no encontrado",
                    content = @Content(schema = @Schema(implementation = co.sena.sicot.exception.ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Error interno del servidor",
                    content = @Content(schema = @Schema(implementation = co.sena.sicot.exception.ErrorResponse.class)))
    })
    @PostMapping("/{id}/supervisor/invitacion")
    // Mismo permiso que asignar: no se le abre a Gestión la administración de
    // usuarios, se le da una sola capacidad acotada —crear al supervisor que
    // está poniendo en un contrato—. El rol lo fija el servicio, no quien
    // llama, así que por aquí no sale un administrador nuevo.
    @PreAuthorize("hasAnyRole('GESTION', 'ADMINISTRADOR')")
    public ResponseEntity<InvitacionDeSupervisorResponse> invitarSupervisor(
            @PathVariable Long id, @Valid @RequestBody InvitarSupervisorRequest request) {
        return ResponseEntity.ok(contratoService.invitarYAsignarSupervisor(id, request));
    }

    @Operation(summary = "Cambiar estado del contrato")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Estado cambiado",
                    content = @Content(schema = @Schema(implementation = ContratoResponse.class))),
            @ApiResponse(responseCode = "400", description = "Datos inválidos",
                    content = @Content(schema = @Schema(implementation = co.sena.sicot.exception.ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "No autenticado",
                    content = @Content(schema = @Schema(implementation = co.sena.sicot.exception.ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Sin rol GESTION/ADMINISTRADOR",
                    content = @Content(schema = @Schema(implementation = co.sena.sicot.exception.ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Contrato no encontrado",
                    content = @Content(schema = @Schema(implementation = co.sena.sicot.exception.ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Error interno del servidor",
                    content = @Content(schema = @Schema(implementation = co.sena.sicot.exception.ErrorResponse.class)))
    })
    @PatchMapping("/{id}/estado")
    @PreAuthorize("hasAnyRole('GESTION', 'ADMINISTRADOR')")
    public ResponseEntity<ContratoResponse> cambiarEstado(@PathVariable Long id,
                                                          @Valid @RequestBody CambiarEstadoContratoRequest request) {
        return ResponseEntity.ok(contratoService.cambiarEstado(id, request));
    }

    /**
     * Cómo va el contrato respecto a su plazo.
     *
     * <p>No lleva {@code @PreAuthorize}: la autorización real la aplica
     * {@code CronogramaService} llamando a {@code ContratoService.buscar}, que
     * ejecuta {@code verificarAccesoAlContrato}. Un SUPERVISOR solo alcanza el
     * cronograma del contrato que tiene asignado. Mismo patrón que las alertas y
     * los registros por contrato.
     */
    @Operation(summary = "Estado de cronograma del contrato (semáforo, brecha y etapa en curso)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Estado de cronograma",
                    content = @Content(schema = @Schema(implementation = CronogramaResponse.class))),
            @ApiResponse(responseCode = "401", description = "No autenticado",
                    content = @Content(schema = @Schema(implementation = co.sena.sicot.exception.ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Contrato no encontrado o sin acceso",
                    content = @Content(schema = @Schema(implementation = co.sena.sicot.exception.ErrorResponse.class)))
    })
    @GetMapping("/{id}/cronograma")
    public ResponseEntity<CronogramaResponse> cronograma(@PathVariable Long id) {
        return ResponseEntity.ok(CronogramaResponse.de(cronogramaService.de(id)));
    }

    /**
     * Lo que publica el SECOP II del contrato, con las diferencias frente a lo
     * registrado en SICOT (paso 2.3 del proceso real del supervisor).
     *
     * <p>Mismo patrón de autorización que el cronograma: sin {@code @PreAuthorize},
     * porque {@code ContratoService.buscar} ya restringe al SUPERVISOR a su
     * contrato. Responde 200 también cuando datos.gov.co no contesta, con
     * {@code disponible=false} y el motivo: no tener red no es un error del
     * supervisor ni del sistema.
     */
    @Operation(summary = "Verificación del contrato contra los datos abiertos del SECOP II")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Verificación (o motivo por el que no se pudo hacer)",
                    content = @Content(schema = @Schema(implementation = VerificacionSecopResponse.class))),
            @ApiResponse(responseCode = "401", description = "No autenticado",
                    content = @Content(schema = @Schema(implementation = co.sena.sicot.exception.ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Contrato no encontrado o sin acceso",
                    content = @Content(schema = @Schema(implementation = co.sena.sicot.exception.ErrorResponse.class)))
    })
    @GetMapping("/{id}/secop")
    public ResponseEntity<VerificacionSecopResponse> verificarEnSecop(@PathVariable Long id) {
        return ResponseEntity.ok(verificacionSecop.verificar(id));
    }

    /**
     * La solicitud de pedido del contrato: los bienes y cantidades que el
     * supervisor le pide al contratista en el paso 2.4 del proceso real.
     *
     * <p>Sin {@code @PreAuthorize}, igual que el cronograma y la verificación
     * en SECOP: {@code ContratoService.buscar} ya restringe al SUPERVISOR a sus
     * contratos, y Gestión y Administración ven todos. Responde 200 con la
     * lista vacía cuando el supervisor todavía no la ha cargado: no tener
     * pedido no es un error.
     */
    @Operation(summary = "Solicitud de pedido al contratista (paso 2.4), con el control de presupuesto")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "La solicitud de pedido del contrato",
                    content = @Content(schema = @Schema(implementation = SolicitudDePedidoResponse.class))),
            @ApiResponse(responseCode = "401", description = "No autenticado",
                    content = @Content(schema = @Schema(implementation = co.sena.sicot.exception.ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Contrato no encontrado o sin acceso",
                    content = @Content(schema = @Schema(implementation = co.sena.sicot.exception.ErrorResponse.class)))
    })
    @GetMapping("/{id}/solicitud-pedido")
    public ResponseEntity<SolicitudDePedidoResponse> obtenerSolicitudDePedido(@PathVariable Long id) {
        return ResponseEntity.ok(solicitudDePedido.obtener(id));
    }

    /**
     * Reemplaza la solicitud de pedido del contrato. Una lista vacía la borra,
     * que es cómo se deshace un pegado equivocado.
     */
    @Operation(summary = "Guardar la solicitud de pedido al contratista (reemplaza la anterior)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "La solicitud guardada, con el presupuesto recalculado",
                    content = @Content(schema = @Schema(implementation = SolicitudDePedidoResponse.class))),
            @ApiResponse(responseCode = "400", description = "Ítems inválidos o con el número repetido",
                    content = @Content(schema = @Schema(implementation = co.sena.sicot.exception.ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "No autenticado",
                    content = @Content(schema = @Schema(implementation = co.sena.sicot.exception.ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Contrato no encontrado o sin acceso",
                    content = @Content(schema = @Schema(implementation = co.sena.sicot.exception.ErrorResponse.class)))
    })
    @PutMapping("/{id}/solicitud-pedido")
    public ResponseEntity<SolicitudDePedidoResponse> guardarSolicitudDePedido(
            @PathVariable Long id, @Valid @RequestBody GuardarSolicitudDePedidoRequest request) {
        return ResponseEntity.ok(solicitudDePedido.guardar(id, request));
    }

    /**
     * El Excel que se adjunta al correo del contratista, con el formato que el
     * supervisor usa hoy. Se genera al pedirlo y no se guarda: ver
     * {@code SolicitudDePedidoService.generarArchivo}.
     */
    @Operation(summary = "Descargar la solicitud de pedido en Excel, para adjuntarla al correo")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "El archivo XLSX",
                    content = @Content(schema = @Schema(type = "string", format = "binary"))),
            @ApiResponse(responseCode = "400", description = "La solicitud de pedido está vacía",
                    content = @Content(schema = @Schema(implementation = co.sena.sicot.exception.ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "No autenticado",
                    content = @Content(schema = @Schema(implementation = co.sena.sicot.exception.ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Contrato no encontrado o sin acceso",
                    content = @Content(schema = @Schema(implementation = co.sena.sicot.exception.ErrorResponse.class)))
    })
    @GetMapping("/{id}/solicitud-pedido/archivo")
    public ResponseEntity<byte[]> descargarSolicitudDePedido(@PathVariable Long id) {
        byte[] archivo = solicitudDePedido.generarArchivo(id);
        String nombre = solicitudDePedido.nombreDelArchivo(id);
        String tipo = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, NombreDeDescarga.cabecera(nombre, tipo))
                .contentType(MediaType.parseMediaType(tipo))
                .body(archivo);
    }
}
