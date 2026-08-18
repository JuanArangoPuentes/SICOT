package co.sena.sicot.security;

import co.sena.sicot.entity.Usuario;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public final class SecurityUtils {

    private SecurityUtils() {
    }

    public static Usuario currentUsuario() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Usuario usuario) {
            return usuario;
        }
        return null;
    }
}
