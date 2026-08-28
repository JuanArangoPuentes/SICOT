package co.sena.sicot.controller;

import co.sena.sicot.dto.documento.DocumentoResponse;
import co.sena.sicot.dto.ia.GenerarDocumentoRequest;
import co.sena.sicot.entity.Documento;
import co.sena.sicot.ia.GeneracionDocumentoService;
import co.sena.sicot.service.DocumentoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/contratos/{contratoId}/documentos")
@Tag(name = "Documentos", description = "Documentos y evidencias de un contrato")
public class DocumentoController {

    private final DocumentoService documentoService;
    private final GeneracionDocumentoService generacionDocumentoService;

    public DocumentoController(DocumentoService documentoService, GeneracionDocumentoService generacionDocumentoService) {
        this.documentoService = documentoService;
        this.generacionDocumentoService = generacionDocumentoService;
    }

    @Operation(summary = "Listar documentos de un contrato")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lista de documentos",
                    content = @Content(schema = @Schema(implementation = DocumentoResponse.class))),
            @ApiResponse(responseCode = "401", description = "No autenticado",
                    content = @Content(schema = @Schema(implementation = co.sena.sicot.exception.ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Sin acceso a este contrato",
                    content = @Content(schema = @Schema(implementation = co.sena.sicot.exception.ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Contrato no encontrado",
                    content = @Content(schema = @Schema(implementation = co.sena.sicot.exception.ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Error interno del servidor",
                    content = @Content(schema = @Schema(implementation = co.sena.sicot.exception.ErrorResponse.class)))
    })
    @GetMapping
    public ResponseEntity<List<DocumentoResponse>> listar(@PathVariable Long contratoId) {
        return ResponseEntity.ok(documentoService.listarPorContrato(contratoId));
    }

    @Operation(summary = "Cargar un documento real al contrato (carga real de archivos)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Documento subido",
                    content = @Content(schema = @Schema(implementation = DocumentoResponse.class))),
            @ApiResponse(responseCode = "400", description = "Archivo inválido o faltante",
                    content = @Content(schema = @Schema(implementation = co.sena.sicot.exception.ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "No autenticado",
                    content = @Content(schema = @Schema(implementation = co.sena.sicot.exception.ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Sin rol GESTION/ADMINISTRADOR o sin acceso al contrato",
                    content = @Content(schema = @Schema(implementation = co.sena.sicot.exception.ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Contrato no encontrado",
                    content = @Content(schema = @Schema(implementation = co.sena.sicot.exception.ErrorResponse.class))),
            @ApiResponse(responseCode = "415", description = "Tipo de contenido no soportado",
                    content = @Content(schema = @Schema(implementation = co.sena.sicot.exception.ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Error interno del servidor",
                    content = @Content(schema = @Schema(implementation = co.sena.sicot.exception.ErrorResponse.class)))
    })
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('GESTION', 'ADMINISTRADOR')")
    public ResponseEntity<DocumentoResponse> subir(
            @PathVariable Long contratoId,
            @RequestParam(value = "subetapaId", required = false) Long subetapaId,
            @RequestParam(value = "nombre", required = false) String nombre,
            @RequestParam("archivo") MultipartFile archivo) {
        return ResponseEntity.ok(documentoService.subir(contratoId, subetapaId, nombre, archivo));
    }

    @Operation(summary = "Descargar el archivo de un documento")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Archivo binario",
                    content = @Content(schema = @Schema(type = "string", format = "binary"))),
            @ApiResponse(responseCode = "401", description = "No autenticado",
                    content = @Content(schema = @Schema(implementation = co.sena.sicot.exception.ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Sin acceso a este contrato",
                    content = @Content(schema = @Schema(implementation = co.sena.sicot.exception.ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Documento no encontrado",
                    content = @Content(schema = @Schema(implementation = co.sena.sicot.exception.ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Error interno del servidor",
                    content = @Content(schema = @Schema(implementation = co.sena.sicot.exception.ErrorResponse.class)))
    })
    @GetMapping("/{id}/archivo")
    public ResponseEntity<byte[]> descargar(@PathVariable Long contratoId, @PathVariable Long id) {
        Documento documento = documentoService.buscarConContenido(id);
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(documento.getNombre(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(MediaType.parseMediaType(
                        documento.getContentType() != null ? documento.getContentType() : "application/octet-stream"))
                .body(documento.getContenido());
    }

    @Operation(summary = "Generar (redactar) un documento formal con el Copiloto IA a partir de los datos reales del contrato",
            description = "Crea el documento en estado PENDIENTE — el supervisor lo revisa y firma después con POST /{id}/firmar.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Documento generado",
                    content = @Content(schema = @Schema(implementation = DocumentoResponse.class))),
            @ApiResponse(responseCode = "400", description = "Datos inválidos",
                    content = @Content(schema = @Schema(implementation = co.sena.sicot.exception.ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "No autenticado",
                    content = @Content(schema = @Schema(implementation = co.sena.sicot.exception.ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Sin rol SUPERVISOR/ADMINISTRADOR o no es el supervisor del contrato",
                    content = @Content(schema = @Schema(implementation = co.sena.sicot.exception.ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Contrato o subetapa no encontrados",
                    content = @Content(schema = @Schema(implementation = co.sena.sicot.exception.ErrorResponse.class))),
            @ApiResponse(responseCode = "503", description = "Servicio de IA (Ollama) no disponible",
                    content = @Content(schema = @Schema(implementation = co.sena.sicot.exception.ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Error interno del servidor",
                    content = @Content(schema = @Schema(implementation = co.sena.sicot.exception.ErrorResponse.class)))
    })
    @PostMapping("/generar")
    @PreAuthorize("hasAnyRole('SUPERVISOR', 'ADMINISTRADOR')")
    public ResponseEntity<DocumentoResponse> generar(@PathVariable Long contratoId,
                                                     @Valid @RequestBody GenerarDocumentoRequest request) {
        return ResponseEntity.ok(generacionDocumentoService.generar(contratoId, request.subetapaId(), request.tipo()));
    }

    @Operation(summary = "Firmar un documento con la firma electrónica de la cuenta actual")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Documento firmado",
                    content = @Content(schema = @Schema(implementation = DocumentoResponse.class))),
            @ApiResponse(responseCode = "401", description = "No autenticado",
                    content = @Content(schema = @Schema(implementation = co.sena.sicot.exception.ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Sin rol SUPERVISOR/ADMINISTRADOR o no es el supervisor del contrato o sin firma asignada",
                    content = @Content(schema = @Schema(implementation = co.sena.sicot.exception.ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Documento no encontrado",
                    content = @Content(schema = @Schema(implementation = co.sena.sicot.exception.ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Error interno del servidor",
                    content = @Content(schema = @Schema(implementation = co.sena.sicot.exception.ErrorResponse.class)))
    })
    @PostMapping("/{id}/firmar")
    @PreAuthorize("hasAnyRole('SUPERVISOR', 'ADMINISTRADOR')")
    public ResponseEntity<DocumentoResponse> firmar(@PathVariable Long contratoId, @PathVariable Long id) {
        return ResponseEntity.ok(documentoService.firmar(id));
    }
}
