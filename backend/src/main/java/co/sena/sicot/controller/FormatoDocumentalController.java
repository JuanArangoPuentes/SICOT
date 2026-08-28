package co.sena.sicot.controller;

import co.sena.sicot.dto.formato.FormatoDocumentalResponse;
import co.sena.sicot.entity.FormatoDocumental;
import co.sena.sicot.service.FormatoDocumentalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@RequestMapping("/api/formatos")
@Tag(name = "Formatos documentales", description = "Catálogo de formatos oficiales (GCCON, GIL, ESUCON, etc.) que administra el Administrador")
public class FormatoDocumentalController {

    private final FormatoDocumentalService formatoService;

    public FormatoDocumentalController(FormatoDocumentalService formatoService) {
        this.formatoService = formatoService;
    }

    @Operation(summary = "Listar el catálogo de formatos documentales")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Catálogo de formatos",
                    content = @Content(schema = @Schema(implementation = FormatoDocumentalResponse.class))),
            @ApiResponse(responseCode = "401", description = "No autenticado",
                    content = @Content(schema = @Schema(implementation = co.sena.sicot.exception.ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Error interno del servidor",
                    content = @Content(schema = @Schema(implementation = co.sena.sicot.exception.ErrorResponse.class)))
    })
    @GetMapping
    public ResponseEntity<List<FormatoDocumentalResponse>> listar() {
        return ResponseEntity.ok(formatoService.listar());
    }

    @Operation(summary = "Cargar un formato documental (nuevo o nueva versión de uno existente)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Formato subido",
                    content = @Content(schema = @Schema(implementation = FormatoDocumentalResponse.class))),
            @ApiResponse(responseCode = "400", description = "Datos inválidos (código duplicado, archivo faltante)",
                    content = @Content(schema = @Schema(implementation = co.sena.sicot.exception.ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "No autenticado",
                    content = @Content(schema = @Schema(implementation = co.sena.sicot.exception.ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Sin rol ADMINISTRADOR",
                    content = @Content(schema = @Schema(implementation = co.sena.sicot.exception.ErrorResponse.class))),
            @ApiResponse(responseCode = "415", description = "Tipo de contenido no soportado",
                    content = @Content(schema = @Schema(implementation = co.sena.sicot.exception.ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Error interno del servidor",
                    content = @Content(schema = @Schema(implementation = co.sena.sicot.exception.ErrorResponse.class)))
    })
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<FormatoDocumentalResponse> subir(
            @RequestParam("codigo") String codigo,
            @RequestParam("nombre") String nombre,
            @RequestParam("archivo") MultipartFile archivo) {
        return ResponseEntity.ok(formatoService.subir(codigo, nombre, archivo));
    }

    @Operation(summary = "Descargar el archivo de un formato documental")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Archivo binario",
                    content = @Content(schema = @Schema(type = "string", format = "binary"))),
            @ApiResponse(responseCode = "401", description = "No autenticado",
                    content = @Content(schema = @Schema(implementation = co.sena.sicot.exception.ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Formato no encontrado",
                    content = @Content(schema = @Schema(implementation = co.sena.sicot.exception.ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Error interno del servidor",
                    content = @Content(schema = @Schema(implementation = co.sena.sicot.exception.ErrorResponse.class)))
    })
    @GetMapping("/{id}/archivo")
    public ResponseEntity<byte[]> descargar(@PathVariable Long id) {
        FormatoDocumental formato = formatoService.buscarConContenido(id);
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(formato.getNombreArchivo(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(MediaType.parseMediaType(formato.getContentType()))
                .body(formato.getContenido());
    }

    @Operation(summary = "Eliminar un formato documental del catálogo")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Formato eliminado"),
            @ApiResponse(responseCode = "401", description = "No autenticado",
                    content = @Content(schema = @Schema(implementation = co.sena.sicot.exception.ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Sin rol ADMINISTRADOR",
                    content = @Content(schema = @Schema(implementation = co.sena.sicot.exception.ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Formato no encontrado",
                    content = @Content(schema = @Schema(implementation = co.sena.sicot.exception.ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Error interno del servidor",
                    content = @Content(schema = @Schema(implementation = co.sena.sicot.exception.ErrorResponse.class)))
    })
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        formatoService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
