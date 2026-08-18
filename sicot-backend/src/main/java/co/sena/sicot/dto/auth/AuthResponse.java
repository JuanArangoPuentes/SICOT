package co.sena.sicot.dto.auth;

import co.sena.sicot.entity.enums.Rol;

public record AuthResponse(
        String token,
        Long usuarioId,
        String nombre,
        String email,
        Rol rol
) {
}
