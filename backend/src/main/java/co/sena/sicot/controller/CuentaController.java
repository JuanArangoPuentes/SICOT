package co.sena.sicot.controller;

import co.sena.sicot.dto.auth.AuthResponse;
import co.sena.sicot.dto.auth.CambiarContrasenaRequest;
import co.sena.sicot.entity.Usuario;
import co.sena.sicot.exception.CredencialesInvalidasException;
import co.sena.sicot.security.SecurityUtils;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Lo que cada persona hace con su propia cuenta, sea cual sea su rol.
 *
 * <p>No cuelga de {@code /api/auth}: esa ruta es pública en
 * {@code SecurityConfig} (es donde se inicia sesión), y esto exige una sesión.
 * Tampoco de {@code /api/usuarios}, que es solo del ADMINISTRADOR. Aquí entra
 * cualquier rol autenticado por la regla general {@code anyRequest().authenticated()},
 * y la cuenta que se toca es siempre la de la sesión: no hay un {@code id} en
 * la ruta que alguien pueda cambiar por el de otra persona.
 *
 * <p>Antes de esto no existía forma de cambiar la contraseña propia: el
 * administrador solo podía asignar una al azar desde «Resetear clave», y un
 * supervisor se quedaba con la temporal que le llegó por correo.
 */
@RestController
@RequestMapping("/api/cuenta")
@Tag(name = "Cuenta", description = "La cuenta de quien hace la petición")
public class CuentaController {

    private final AuthService authService;

    public CuentaController(AuthService authService) {
        this.authService = authService;
    }

    @Operation(summary = "Cambiar mi contraseña",
            description = "Exige la contraseña actual, cierra todas las demás sesiones de la cuenta y devuelve "
                    + "un token nuevo para seguir trabajando en este equipo.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Contraseña cambiada; token nuevo",
                    content = @Content(schema = @Schema(implementation = AuthResponse.class))),
            @ApiResponse(responseCode = "400", description = "La actual no es correcta, la nueva no cumple las "
                    + "reglas o es igual a la actual",
                    content = @Content(schema = @Schema(implementation = co.sena.sicot.exception.ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Sin sesión válida",
                    content = @Content(schema = @Schema(implementation = co.sena.sicot.exception.ErrorResponse.class))),
            @ApiResponse(responseCode = "429", description = "Demasiados intentos fallidos con la contraseña actual "
                    + "(los mismos que cuenta el inicio de sesión); la cabecera Retry-After indica cuántos segundos",
                    content = @Content(schema = @Schema(implementation = co.sena.sicot.exception.ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Error interno del servidor",
                    content = @Content(schema = @Schema(implementation = co.sena.sicot.exception.ErrorResponse.class)))
    })
    @PutMapping("/contrasena")
    public ResponseEntity<AuthResponse> cambiarContrasena(@Valid @RequestBody CambiarContrasenaRequest request,
                                                          HttpServletRequest peticion) {
        Usuario actual = SecurityUtils.currentUsuario();
        if (actual == null) {
            // No debería pasar: SecurityConfig ya exige sesión para esta ruta.
            throw new CredencialesInvalidasException("Sesión no válida.");
        }
        // getRemoteAddr por lo mismo que en AuthController.login: detrás del
        // proxy ya trae la IP del cliente, y la cabecera a mano se puede inventar.
        return ResponseEntity.ok(authService.cambiarContrasena(actual.getId(), request, peticion.getRemoteAddr()));
    }
}
