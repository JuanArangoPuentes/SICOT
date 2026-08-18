package co.sena.sicot.controller;

import co.sena.sicot.dto.etapa.ActualizarEstadoSubetapaRequest;
import co.sena.sicot.dto.etapa.SubetapaResponse;
import co.sena.sicot.service.EtapaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@Tag(name = "Subetapas", description = "Subetapas del flujo GCCON-P-010")
public class SubetapaController {

    private final EtapaService etapaService;

    public SubetapaController(EtapaService etapaService) {
        this.etapaService = etapaService;
    }

    @Operation(summary = "Listar subetapas de una etapa")
    @GetMapping("/etapas/{etapaId}/subetapas")
    public ResponseEntity<List<SubetapaResponse>> listar(@PathVariable Long etapaId) {
        return ResponseEntity.ok(etapaService.listarSubetapas(etapaId));
    }

    @Operation(summary = "Cambiar estado de una subetapa",
            description = "Al completar subetapas se recalcula el porcentaje y estado de la etapa.")
    @PatchMapping("/subetapas/{id}/estado")
    @PreAuthorize("hasAnyRole('SUPERVISOR', 'GESTION', 'ADMINISTRADOR')")
    public ResponseEntity<SubetapaResponse> cambiarEstado(@PathVariable Long id,
                                                          @Valid @RequestBody ActualizarEstadoSubetapaRequest request) {
        return ResponseEntity.ok(etapaService.cambiarEstadoSubetapa(id, request.estado()));
    }
}
