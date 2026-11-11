package co.sena.sicot.controller;

import co.sena.sicot.dto.salud.AvisoDeServicioResponse;
import co.sena.sicot.salud.SaludDelSistema;
import co.sena.sicot.salud.Servicio;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Lo que la interfaz pregunta para saber si tiene que avisarle al usuario de
 * que algo no va a funcionarle.
 *
 * <h2>Por qué no vale {@code /actuator/health} para esto</h2>
 * Porque dice otra cosa. El actuator responde si la aplicación está sana —y la
 * aplicación está perfectamente sana con Ollama caído: por diseño, SICOT
 * funciona sin el modelo—. Si la IA tirara el health, el despliegue automático
 * daría el arranque por fallido y volvería a la versión anterior sin motivo, y
 * el túnel de Cloudflare dejaría de mandar tráfico a un servidor que funciona.
 *
 * <p>Esto es lo contrario: la aplicación está bien y aun así hay algo que
 * contarle a quien la usa.
 *
 * <h2>Devuelve lo que falta, no el estado de todo</h2>
 * Con todo en orden es una lista vacía. Así la interfaz no tiene que interpretar
 * nada —si hay algo, se pinta; si no, no— y la respuesta no se convierte en un
 * inventario de la infraestructura del Centro para cualquiera que tenga sesión.
 */
@RestController
@RequestMapping("/api/salud")
@Tag(name = "Salud", description = "Qué partes del sistema no están funcionando ahora mismo")
public class SaludController {

    private final SaludDelSistema salud;

    public SaludController(SaludDelSistema salud) {
        this.salud = salud;
    }

    @GetMapping
    @Operation(summary = "Servicios caídos que afectan a quien está usando SICOT",
            description = "Lista vacía cuando todo funciona. Cada elemento trae el mensaje ya "
                    + "redactado para mostrarlo tal cual.")
    public ResponseEntity<List<AvisoDeServicioResponse>> loQueNoFunciona() {
        List<AvisoDeServicioResponse> avisos = salud.loQueDebeSaberElUsuario().stream()
                .map(this::comoAviso)
                .toList();
        return ResponseEntity.ok(avisos);
    }

    private AvisoDeServicioResponse comoAviso(Servicio servicio) {
        long minutos = salud.incidencia(servicio)
                .map(i -> i.duracion().toMinutes())
                .orElse(0L);
        return new AvisoDeServicioResponse(
                servicio.name().toLowerCase(),
                servicio.mensajeParaElUsuario(),
                minutos);
    }
}
