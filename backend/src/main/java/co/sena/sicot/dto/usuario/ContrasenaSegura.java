package co.sena.sicot.dto.usuario;

import co.sena.sicot.security.PoliticaDeContrasena;
import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Aplica {@link PoliticaDeContrasena} a un campo de contraseña.
 *
 * <h2>Por qué una anotación y no una comprobación en el servicio</h2>
 * Porque así el rechazo viaja por el <b>mismo</b> camino que el resto de las
 * validaciones del cuerpo: un 400 con el mapa de campos que el frontend ya sabe
 * pintar debajo del campo que falla. Comprobarlo en el servicio habría dado un
 * {@code BusinessException} —también un 400, pero sin el nombre del campo— y la
 * pantalla tendría que mostrarlo como un aviso general, lejos de donde se
 * escribe.
 *
 * <p>La parte de la política que necesita <i>otro</i> campo (que la contraseña
 * no contenga el correo de la persona) no se puede comprobar aquí: una
 * anotación de campo solo ve su propio valor. Esa queda en el servicio, donde
 * los dos datos están juntos. Es la única división y está a propósito: una
 * anotación de clase vería ambos, pero entonces el error no podría señalar un
 * campo, que es justo lo que se gana con esto.
 */
@Documented
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ContrasenaSegura.Validador.class)
public @interface ContrasenaSegura {

    String message() default "La contraseña no cumple la política de SICOT.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validador implements ConstraintValidator<ContrasenaSegura, String> {

        @Override
        public boolean isValid(String valor, ConstraintValidatorContext contexto) {
            // Nulo o vacío no es asunto de esta anotación: lo dice @NotBlank, y
            // devolver false aquí produciría DOS mensajes para el mismo campo.
            if (valor == null || valor.isBlank()) {
                return true;
            }
            return PoliticaDeContrasena.porQueNoSirve(valor, null)
                    .map(motivo -> {
                        // El mensaje concreto en vez del genérico: «no cumple la
                        // política» obliga a adivinar qué falla, y quien adivina
                        // prueba otra variante de lo mismo.
                        contexto.disableDefaultConstraintViolation();
                        contexto.buildConstraintViolationWithTemplate(motivo).addConstraintViolation();
                        return false;
                    })
                    .orElse(true);
        }
    }
}
