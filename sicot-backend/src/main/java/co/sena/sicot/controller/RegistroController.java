package co.sena.sicot.controller;

import co.sena.sicot.dto.registro.RegistroResponse;
import co.sena.sicot.service.RegistroService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
@Tag(name = "Registros", description = "Auditoría de acciones sobre contratos")
public class RegistroController {

    private final RegistroService registroService;

    public RegistroController(RegistroService registroService) {
        this.registroService = registroService;
    }

    @Operation(summary = "Listar registros de auditoría de un contrato")
    @GetMapping("/contratos/{contratoId}/registros")
    public ResponseEntity<List<RegistroResponse>> listarPorContrato(@PathVariable Long contratoId) {
        return ResponseEntity.ok(registroService.listarPorContrato(contratoId));
    }

    @Operation(summary = "Listar todos los registros de auditoría")
    @GetMapping("/registros")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<List<RegistroResponse>> listarTodas() {
        return ResponseEntity.ok(registroService.listarTodos());
    }
}
