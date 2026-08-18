package co.sena.sicot.controller;

import co.sena.sicot.dto.documento.DocumentoResponse;
import co.sena.sicot.service.DocumentoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/contratos/{contratoId}/documentos")
@Tag(name = "Documentos", description = "Documentos y evidencias de un contrato")
public class DocumentoController {

    private final DocumentoService documentoService;

    public DocumentoController(DocumentoService documentoService) {
        this.documentoService = documentoService;
    }

    @Operation(summary = "Listar documentos de un contrato",
            description = "Por ahora solo consulta; la carga real de archivos se habilita en una fase posterior.")
    @GetMapping
    public ResponseEntity<List<DocumentoResponse>> listar(@PathVariable Long contratoId) {
        return ResponseEntity.ok(documentoService.listarPorContrato(contratoId));
    }
}
