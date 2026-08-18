package co.sena.sicot.dto.usuario;

import co.sena.sicot.entity.enums.MetodoEntregaCredenciales;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record EnviarCredencialesRequest(
        @NotBlank(message = "La contraseña es obligatoria.")
        String password,

        @NotNull(message = "El método de entrega es obligatorio.")
        MetodoEntregaCredenciales metodo
) {
}
