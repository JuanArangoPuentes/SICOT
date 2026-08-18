package co.sena.sicot.controller;

import co.sena.sicot.dto.formato.FormatoDocumentalResponse;
import co.sena.sicot.entity.FormatoDocumental;
import co.sena.sicot.service.FormatoDocumentalService;
import io.swagger.v3.oas.annotations.Operation;
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
    @GetMapping
    public ResponseEntity<List<FormatoDocumentalResponse>> listar() {
        return ResponseEntity.ok(formatoService.listar());
    }

    @Operation(summary = "Cargar un formato documental (nuevo o nueva versión de uno existente)")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<FormatoDocumentalResponse> subir(
            @RequestParam("codigo") String codigo,
            @RequestParam("nombre") String nombre,
            @RequestParam("archivo") MultipartFile archivo) {
        return ResponseEntity.ok(formatoService.subir(codigo, nombre, archivo));
    }

    @Operation(summary = "Descargar el archivo de un formato documental")
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
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        formatoService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
