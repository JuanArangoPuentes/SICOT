package co.sena.sicot.mapper;

import co.sena.sicot.dto.usuario.UsuarioResponse;
import co.sena.sicot.entity.Usuario;

public final class UsuarioMapper {

    private UsuarioMapper() {
    }

    public static UsuarioResponse toResponse(Usuario u) {
        return new UsuarioResponse(u.getId(), u.getNombre(), u.getEmail(), u.getRol(),
                u.isActivo(), u.getFechaCreacion());
    }
}
