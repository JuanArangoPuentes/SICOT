package co.sena.sicot.controller;

import co.sena.sicot.dto.auth.AuthResponse;
import co.sena.sicot.dto.auth.LoginRequest;
import co.sena.sicot.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Autenticación", description = "Inicio de sesión y emisión de token JWT")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @Operation(summary = "Iniciar sesión", description = "Valida credenciales y devuelve un token JWT.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Credenciales válidas, token emitido",
                    content = @Content(schema = @Schema(implementation = AuthResponse.class))),
            @ApiResponse(responseCode = "400", description = "Cuerpo de petición inválido o faltante",
                    content = @Content(schema = @Schema(implementation = co.sena.sicot.exception.ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Credenciales inválidas o cuenta inactiva",
                    content = @Content(schema = @Schema(implementation = co.sena.sicot.exception.ErrorResponse.class))),
            @ApiResponse(responseCode = "429", description = "Demasiados intentos fallidos; reintente más tarde "
                    + "(la cabecera Retry-After indica cuántos segundos)",
                    content = @Content(schema = @Schema(implementation = co.sena.sicot.exception.ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Error interno del servidor",
                    content = @Content(schema = @Schema(implementation = co.sena.sicot.exception.ErrorResponse.class)))
    })
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request,
                                              HttpServletRequest peticion) {
        // getRemoteAddr y NO la cabecera X-Forwarded-For: esa cabecera la pone
        // el cliente y es trivial de falsear, así que confiar en ella
        // convertiría el límite por IP en decorativo (basta variarla en cada
        // intento). Si algún día el backend queda detrás de un proxy inverso,
        // la forma correcta de recuperar la IP real es
        // `server.forward-headers-strategy=framework` en application.properties,
        // que hace que Spring procese esas cabeceras solo donde corresponde.
        return ResponseEntity.ok(authService.login(request, peticion.getRemoteAddr()));
    }
}
