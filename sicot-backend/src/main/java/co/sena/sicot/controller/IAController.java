package co.sena.sicot.controller;

import co.sena.sicot.dto.ia.ExtraccionContratoResponse;
import co.sena.sicot.ia.ExtraccionContratoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/ia")
@Tag(name = "IA (Ollama local)", description = "Copiloto IA: extracción de datos de documentos y redacción asistida — sin costo de licencia, corre localmente")
public class IAController {

    private final ExtraccionContratoService extraccionContratoService;

    public IAController(ExtraccionContratoService extraccionContratoService) {
        this.extraccionContratoService = extraccionContratoService;
    }

    @Operation(summary = "Extraer datos propuestos de uno o varios documentos de contrato (Gestión revisa y confirma)",
            description = "No crea el contrato — solo propone valores para el formulario, combinando lo encontrado en todos los archivos. La confirmación es manual vía POST /api/contratos.")
    @PostMapping(value = "/extraer-contrato", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('GESTION', 'ADMINISTRADOR')")
    public ResponseEntity<ExtraccionContratoResponse> extraerContrato(@RequestParam("archivos") List<MultipartFile> archivos) {
        return ResponseEntity.ok(extraccionContratoService.extraer(archivos));
    }
}
