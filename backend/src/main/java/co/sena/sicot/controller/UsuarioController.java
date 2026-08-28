package co.sena.sicot.controller;

import co.sena.sicot.dto.usuario.ActualizarUsuarioRequest;
import co.sena.sicot.dto.usuario.CambiarEstadoUsuarioRequest;
import co.sena.sicot.dto.usuario.CrearUsuarioRequest;
import co.sena.sicot.dto.usuario.EnviarCredencialesRequest;
import co.sena.sicot.dto.usuario.EnviarCredencialesResponse;
import co.sena.sicot.dto.usuario.UsuarioResponse;
import co.sena.sicot.service.UsuarioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/usuarios")
@Tag(name = "Usuarios", description = "Administración de usuarios del sistema (solo ADMINISTRADOR)")
@PreAuthorize("hasRole('ADMINISTRADOR')")
public class UsuarioController {

    private final UsuarioService usuarioService;

    public UsuarioController(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    @Operation(summary = "Listar usuarios",
            description = "GESTION también puede listar (solo lectura) para poder asignar supervisores a un contrato.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lista de usuarios",
                    content = @Content(schema = @Schema(implementation = UsuarioResponse.class))),
            @ApiResponse(responseCode = "401", description = "No autenticado",
                    content = @Content(schema = @Schema(implementation = co.sena.sicot.exception.ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Sin rol ADMINISTRADOR ni GESTION",
                    content = @Content(schema = @Schema(implementation = co.sena.sicot.exception.ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Error interno del servidor",
                    content = @Content(schema = @Schema(implementation = co.sena.sicot.exception.ErrorResponse.class)))
    })
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMINISTRADOR', 'GESTION')")
    public ResponseEntity<List<UsuarioResponse>> listar() {
        return ResponseEntity.ok(usuarioService.listar());
    }

    @Operation(summary = "Obtener usuario por id")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Usuario encontrado",
                    content = @Content(schema = @Schema(implementation = UsuarioResponse.class))),
            @ApiResponse(responseCode = "401", description = "No autenticado",
                    content = @Content(schema = @Schema(implementation = co.sena.sicot.exception.ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Sin rol ADMINISTRADOR",
                    content = @Content(schema = @Schema(implementation = co.sena.sicot.exception.ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Usuario no encontrado",
                    content = @Content(schema = @Schema(implementation = co.sena.sicot.exception.ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Error interno del servidor",
                    content = @Content(schema = @Schema(implementation = co.sena.sicot.exception.ErrorResponse.class)))
    })
    @GetMapping("/{id}")
    public ResponseEntity<UsuarioResponse> obtener(@PathVariable Long id) {
        return ResponseEntity.ok(usuarioService.obtener(id));
    }

    @Operation(summary = "Crear usuario")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Usuario creado",
                    content = @Content(schema = @Schema(implementation = UsuarioResponse.class))),
            @ApiResponse(responseCode = "400", description = "Datos inválidos (validación o email duplicado)",
                    content = @Content(schema = @Schema(implementation = co.sena.sicot.exception.ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "No autenticado",
                    content = @Content(schema = @Schema(implementation = co.sena.sicot.exception.ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Sin rol ADMINISTRADOR",
                    content = @Content(schema = @Schema(implementation = co.sena.sicot.exception.ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Error interno del servidor",
                    content = @Content(schema = @Schema(implementation = co.sena.sicot.exception.ErrorResponse.class)))
    })
    @PostMapping
    public ResponseEntity<UsuarioResponse> crear(@Valid @RequestBody CrearUsuarioRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(usuarioService.crear(request));
    }

    @Operation(summary = "Actualizar usuario")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Usuario actualizado",
                    content = @Content(schema = @Schema(implementation = UsuarioResponse.class))),
            @ApiResponse(responseCode = "400", description = "Datos inválidos",
                    content = @Content(schema = @Schema(implementation = co.sena.sicot.exception.ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "No autenticado",
                    content = @Content(schema = @Schema(implementation = co.sena.sicot.exception.ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Sin rol ADMINISTRADOR",
                    content = @Content(schema = @Schema(implementation = co.sena.sicot.exception.ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Usuario no encontrado",
                    content = @Content(schema = @Schema(implementation = co.sena.sicot.exception.ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Error interno del servidor",
                    content = @Content(schema = @Schema(implementation = co.sena.sicot.exception.ErrorResponse.class)))
    })
    @PutMapping("/{id}")
    public ResponseEntity<UsuarioResponse> actualizar(@PathVariable Long id,
                                                      @Valid @RequestBody ActualizarUsuarioRequest request) {
        return ResponseEntity.ok(usuarioService.actualizar(id, request));
    }

    @Operation(summary = "Activar o desactivar usuario")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Estado cambiado",
                    content = @Content(schema = @Schema(implementation = UsuarioResponse.class))),
            @ApiResponse(responseCode = "400", description = "Datos inválidos",
                    content = @Content(schema = @Schema(implementation = co.sena.sicot.exception.ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "No autenticado",
                    content = @Content(schema = @Schema(implementation = co.sena.sicot.exception.ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Sin rol ADMINISTRADOR",
                    content = @Content(schema = @Schema(implementation = co.sena.sicot.exception.ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Usuario no encontrado",
                    content = @Content(schema = @Schema(implementation = co.sena.sicot.exception.ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Error interno del servidor",
                    content = @Content(schema = @Schema(implementation = co.sena.sicot.exception.ErrorResponse.class)))
    })
    @PatchMapping("/{id}/estado")
    public ResponseEntity<UsuarioResponse> cambiarEstado(@PathVariable Long id,
                                                         @Valid @RequestBody CambiarEstadoUsuarioRequest request) {
        return ResponseEntity.ok(usuarioService.cambiarEstado(id, request));
    }

    @Operation(summary = "Enviar credenciales por correo",
            description = "Envío real vía SMTP. Si no está configurado o falla, responde enviado=false con el error real — nunca finge éxito.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Resultado del envío",
                    content = @Content(schema = @Schema(implementation = EnviarCredencialesResponse.class))),
            @ApiResponse(responseCode = "400", description = "Datos inválidos",
                    content = @Content(schema = @Schema(implementation = co.sena.sicot.exception.ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "No autenticado",
                    content = @Content(schema = @Schema(implementation = co.sena.sicot.exception.ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Sin rol ADMINISTRADOR",
                    content = @Content(schema = @Schema(implementation = co.sena.sicot.exception.ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Usuario no encontrado",
                    content = @Content(schema = @Schema(implementation = co.sena.sicot.exception.ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Error interno del servidor",
                    content = @Content(schema = @Schema(implementation = co.sena.sicot.exception.ErrorResponse.class)))
    })
    @PostMapping("/{id}/enviar-credenciales")
    public ResponseEntity<EnviarCredencialesResponse> enviarCredenciales(@PathVariable Long id,
                                                                         @Valid @RequestBody EnviarCredencialesRequest request) {
        return ResponseEntity.ok(usuarioService.enviarCredenciales(id, request));
    }
}
