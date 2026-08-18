package co.sena.sicot.controller;

import co.sena.sicot.dto.firma.CambiarEstadoFirmaRequest;
import co.sena.sicot.dto.firma.CrearFirmaRequest;
import co.sena.sicot.dto.firma.FirmaResponse;
import co.sena.sicot.service.FirmaElectronicaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/firmas")
@Tag(name = "Firmas Electrónicas", description = "Asigna una firma electrónica de referencia a una cuenta real (solo ADMINISTRADOR)")
@PreAuthorize("hasRole('ADMINISTRADOR')")
public class FirmaElectronicaController {

    private final FirmaElectronicaService firmaService;

    public FirmaElectronicaController(FirmaElectronicaService firmaService) {
        this.firmaService = firmaService;
    }

    @Operation(summary = "Listar firmas electrónicas asignadas")
    @GetMapping
    public ResponseEntity<List<FirmaResponse>> listar() {
        return ResponseEntity.ok(firmaService.listar());
    }

    @Operation(summary = "Asignar firma electrónica a una cuenta")
    @PostMapping
    public ResponseEntity<FirmaResponse> crear(@Valid @RequestBody CrearFirmaRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(firmaService.crear(request));
    }

    @Operation(summary = "Revocar o restaurar una firma electrónica")
    @PatchMapping("/{id}/estado")
    public ResponseEntity<FirmaResponse> cambiarEstado(@PathVariable Long id,
                                                       @Valid @RequestBody CambiarEstadoFirmaRequest request) {
        return ResponseEntity.ok(firmaService.cambiarEstado(id, request));
    }
}
