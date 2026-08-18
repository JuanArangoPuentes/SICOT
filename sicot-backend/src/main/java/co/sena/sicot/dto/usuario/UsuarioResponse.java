package co.sena.sicot.dto.usuario;

import co.sena.sicot.entity.enums.Rol;

import java.time.Instant;

public record UsuarioResponse(
        Long id,
        String nombre,
        String email,
        String telefono,
        Rol rol,
        boolean activo,
        Instant fechaCreacion
) {
}
