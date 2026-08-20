package co.sena.sicot.controller;

import co.sena.sicot.dto.etapa.EtapaResponse;
import co.sena.sicot.service.EtapaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/contratos/{contratoId}/etapas")
@Tag(name = "Etapas", description = "Etapas del flujo GCCON-P-010 de un contrato")
public class EtapaController {

    private final EtapaService etapaService;

    public EtapaController(EtapaService etapaService) {
        this.etapaService = etapaService;
    }

    @Operation(summary = "Listar etapas de un contrato")
    @GetMapping
    public ResponseEntity<List<EtapaResponse>> listar(@PathVariable Long contratoId) {
        return ResponseEntity.ok(etapaService.listarPorContrato(contratoId));
    }

    @Operation(summary = "Obtener una etapa de un contrato")
    @GetMapping("/{etapaId}")
    public ResponseEntity<EtapaResponse> obtener(@PathVariable Long contratoId,
                                                 @PathVariable Long etapaId) {
        return ResponseEntity.ok(etapaService.obtenerEtapaDeContrato(contratoId, etapaId));
    }
}
