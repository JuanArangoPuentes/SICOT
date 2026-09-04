package co.sena.sicot;

import jakarta.validation.constraints.Size;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Cubre el hueco que dejó la tarea de validaciones de entrada: una restricción
 * sobre un {@code @RequestParam} en un controlador que NO declara
 * {@code @Validated} a nivel de clase.
 *
 * <p>En ese caso Spring MVC valida el método por su cuenta y lanza
 * {@code HandlerMethodValidationException}, que no es la
 * {@code ConstraintViolationException} de la vía AOP. Sin manejador propio caía
 * en el catch-all de {@code GlobalExceptionHandler} y salía como <b>500</b>: un
 * error del usuario presentado como fallo del servidor.
 *
 * <p>El controlador de abajo existe solo para esta prueba y es deliberadamente
 * el caso peligroso —restricción sin {@code @Validated}—, porque ningún
 * controlador real de SICOT está hoy en esa situación. Es una red para el día en
 * que alguien añada una restricción y olvide la anotación de clase.
 */
class ValidacionDeParametrosSinValidatedIntegrationTest extends PruebaDeIntegracion {

    @Autowired
    private MockMvc mockMvc;

    @TestConfiguration
    @RestController
    static class ControladorSinValidated {
        @GetMapping("/api/prueba-validacion-parametros")
        String buscar(@RequestParam @Size(max = 5, message = "El código no puede superar 5 caracteres.") String codigo) {
            return codigo;
        }
    }

    @Test
    @WithMockUser
    void unParametroQueIncumpleSuRestriccionDevuelve400YNo500() throws Exception {
        mockMvc.perform(get("/api/prueba-validacion-parametros").param("codigo", "demasiado-largo"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Error de validación en los datos enviados."))
                .andExpect(jsonPath("$.fieldErrors").exists());
    }

    @Test
    @WithMockUser
    void unParametroValidoSigueRespondiendo200() throws Exception {
        mockMvc.perform(get("/api/prueba-validacion-parametros").param("codigo", "ok"))
                .andExpect(status().isOk());
    }
}
