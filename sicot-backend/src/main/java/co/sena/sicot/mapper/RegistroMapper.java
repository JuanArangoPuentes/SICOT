package co.sena.sicot.mapper;

import co.sena.sicot.dto.registro.RegistroResponse;
import co.sena.sicot.entity.Registro;
import co.sena.sicot.entity.Usuario;

public final class RegistroMapper {

    private RegistroMapper() {
    }

    public static RegistroResponse toResponse(Registro r) {
        Usuario u = r.getUsuario();
        return new RegistroResponse(
                r.getId(),
                r.getContrato() != null ? r.getContrato().getId() : null,
                u != null ? u.getId() : null,
                u != null ? u.getNombre() : null,
                r.getAccion(),
                r.getDescripcion(),
                r.getFecha());
    }
}
