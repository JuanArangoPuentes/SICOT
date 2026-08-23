package co.sena.sicot.controller;

import co.sena.sicot.dto.alerta.AlertaResponse;
import co.sena.sicot.service.AlertaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@Tag(name = "Alertas", description = "Alertas y recordatorios del sistema")
public class AlertaController {

    private final AlertaService alertaService;

    public AlertaController(AlertaService alertaService) {
        this.alertaService = alertaService;
    }

    @Operation(summary = "Listar alertas de un contrato")
    @GetMapping("/contratos/{contratoId}/alertas")
    public ResponseEntity<List<AlertaResponse>> listarPorContrato(@PathVariable Long contratoId) {
        return ResponseEntity.ok(alertaService.listarPorContrato(contratoId));
    }

    @Operation(summary = "Listar todas las alertas")
    @GetMapping("/alertas")
    @PreAuthorize("hasAnyRole('GESTION', 'ADMINISTRADOR')")
    public ResponseEntity<List<AlertaResponse>> listarTodas() {
        return ResponseEntity.ok(alertaService.listarTodas());
    }

    @Operation(summary = "Marcar alerta como leída")
    @PatchMapping("/alertas/{id}/leida")
    @PreAuthorize("hasAnyRole('SUPERVISOR', 'GESTION', 'ADMINISTRADOR')")
    public ResponseEntity<AlertaResponse> marcarLeida(@PathVariable Long id) {
        return ResponseEntity.ok(alertaService.marcarLeida(id));
    }
}
