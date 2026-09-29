package co.sena.sicot.controller;

import co.sena.sicot.dto.ia.ExtraccionContratoResponse;
import co.sena.sicot.dto.ia.PlantillaDocumentoResponse;
import co.sena.sicot.ia.ExtraccionContratoService;
import co.sena.sicot.ia.PlantillaDocumentoIA;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Comparator;
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
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Datos extraídos",
                    content = @Content(schema = @Schema(implementation = ExtraccionContratoResponse.class))),
            @ApiResponse(responseCode = "400", description = "Archivos inválidos, vacíos o sin texto extraíble",
                    content = @Content(schema = @Schema(implementation = co.sena.sicot.exception.ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "No autenticado",
                    content = @Content(schema = @Schema(implementation = co.sena.sicot.exception.ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Sin rol GESTION/ADMINISTRADOR",
                    content = @Content(schema = @Schema(implementation = co.sena.sicot.exception.ErrorResponse.class))),
            @ApiResponse(responseCode = "415", description = "Tipo de contenido no soportado (no multipart)",
                    content = @Content(schema = @Schema(implementation = co.sena.sicot.exception.ErrorResponse.class))),
            @ApiResponse(responseCode = "503", description = "Servicio de IA (Ollama) no disponible",
                    content = @Content(schema = @Schema(implementation = co.sena.sicot.exception.ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Error interno del servidor",
                    content = @Content(schema = @Schema(implementation = co.sena.sicot.exception.ErrorResponse.class)))
    })
    @PostMapping(value = "/extraer-contrato", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('GESTION', 'ADMINISTRADOR')")
    public ResponseEntity<ExtraccionContratoResponse> extraerContrato(@RequestParam("archivos") List<MultipartFile> archivos) {
        return ResponseEntity.ok(extraccionContratoService.extraer(archivos));
    }

    @Operation(summary = "Documentos formales que SICOT arma y los datos que pide cada uno",
            description = "Los datos listados no están en el contrato (factura, póliza, cédulas…). El supervisor "
                    + "los envía en POST /api/contratos/{id}/documentos/generar, campo «datos»; los que falten "
                    + "salen en el PDF como «[dato pendiente…]».")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Catálogo de documentos"),
            @ApiResponse(responseCode = "401", description = "No autenticado",
                    content = @Content(schema = @Schema(implementation = co.sena.sicot.exception.ErrorResponse.class)))
    })
    @GetMapping("/plantillas")
    public List<PlantillaDocumentoResponse> plantillas() {
        return PlantillaDocumentoIA.CATALOGO.values().stream()
                .sorted(Comparator.comparing(PlantillaDocumentoIA::clave))
                .map(p -> new PlantillaDocumentoResponse(p.clave(), p.codigo(), p.nombre(), p.llevaObservaciones(),
                        p.campos().stream()
                                .map(c -> new PlantillaDocumentoResponse.Campo(c.clave(), c.etiqueta(), c.ejemplo(),
                                        c.opcional(), PlantillaDocumentoIA.dependeDe(c.clave()),
                                        PlantillaDocumentoIA.esPorDocumento(c.clave())))
                                .toList()))
                .toList();
    }
}
