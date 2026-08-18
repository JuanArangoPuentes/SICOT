package co.sena.sicot.dto.usuario;

public record EnviarCredencialesResponse(
        boolean enviado,
        String error
) {
}
