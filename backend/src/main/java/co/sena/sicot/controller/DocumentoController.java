package co.sena.sicot.controller;

import co.sena.sicot.dto.documento.DocumentoResponse;
import co.sena.sicot.dto.ia.GenerarDocumentoRequest;
import co.sena.sicot.entity.Documento;
import co.sena.sicot.ia.GeneracionDocumentoService;
import co.sena.sicot.service.DocumentoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/contratos/{contratoId}/documentos")
@Validated
@Tag(name = "Documentos", description = "Documentos y evidencias de un contrato")
public class DocumentoController {

    private final DocumentoService documentoService;
    private final GeneracionDocumentoService generacionDocumentoService;

    public DocumentoController(DocumentoService documentoService, GeneracionDocumentoService generacionDocumentoService) {
        this.documentoService = documentoService;
        this.generacionDocumentoService = generacionDocumentoService;
    }

    @Operation(summary = "Listar documentos de un contrato")
    @GetMapping
    public ResponseEntity<List<DocumentoResponse>> listar(@PathVariable Long contratoId) {
        return ResponseEntity.ok(documentoService.listarPorContrato(contratoId));
    }

    @Operation(summary = "Cargar un documento real al contrato (carga real de archivos)")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('GESTION', 'ADMINISTRADOR')")
    public ResponseEntity<DocumentoResponse> subir(
            @PathVariable Long contratoId,
            @RequestParam(value = "subetapaId", required = false)
            @Positive(message = "El identificador de la subetapa debe ser un número positivo.")
            Long subetapaId,
            @RequestParam(value = "nombre", required = false)
            @Size(max = 255, message = "El nombre del documento no puede superar 255 caracteres.")
            String nombre,
            @RequestParam("archivo") MultipartFile archivo) {
        return ResponseEntity.ok(documentoService.subir(contratoId, subetapaId, nombre, archivo));
    }

    @Operation(summary = "Descargar el archivo de un documento")
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
    @PostMapping("/generar")
    @PreAuthorize("hasAnyRole('SUPERVISOR', 'ADMINISTRADOR')")
    public ResponseEntity<DocumentoResponse> generar(@PathVariable Long contratoId,
                                                       @Valid @RequestBody GenerarDocumentoRequest request) {
        return ResponseEntity.ok(generacionDocumentoService.generar(contratoId, request.subetapaId(), request.tipo()));
    }

    @Operation(summary = "Firmar un documento con la firma electrónica de la cuenta actual")
    @PostMapping("/{id}/firmar")
    @PreAuthorize("hasAnyRole('SUPERVISOR', 'ADMINISTRADOR')")
    public ResponseEntity<DocumentoResponse> firmar(@PathVariable Long contratoId, @PathVariable Long id) {
        return ResponseEntity.ok(documentoService.firmar(id));
    }
}
