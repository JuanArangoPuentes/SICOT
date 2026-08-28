package co.sena.sicot.security;

import co.sena.sicot.entity.Contrato;
import co.sena.sicot.entity.Usuario;
import co.sena.sicot.entity.enums.Rol;
import co.sena.sicot.exception.AccesoDenegadoException;
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

    /**
     * Único punto de la aplicación que decide si el usuario autenticado puede
     * ver/tocar los datos de un contrato. Un SUPERVISOR solo puede acceder al
     * contrato que tiene asignado — GESTION y ADMINISTRADOR nunca están
     * restringidos por esta regla. Se llama desde {@code ContratoService.buscar}
     * (lo que protege en cascada casi todo lo que cuelga de un contrato:
     * etapas, documentos, alertas, registros) y desde los puntos que resuelven
     * el contrato por otra vía (p. ej. a través de un documento o una alerta).
     * `contrato == null` no restringe nada (dato sin dueño, p. ej. una alerta
     * huérfana).
     * <p>
     * Lanza {@link AccesoDenegadoException} (HTTP 404) en lugar de
     * {@link BusinessException} (HTTP 400) para evitar el oráculo de enumeración:
     * un supervisor no debe poder distinguir entre "el contrato no existe" y
     * "el contrato existe pero no es suyo".
     */
    public static void verificarAccesoAlContrato(Contrato contrato) {
        if (contrato == null) {
            return;
        }
        Usuario usuario = currentUsuario();
        if (usuario == null || usuario.getRol() != Rol.SUPERVISOR) {
            return;
        }
        boolean esSupervisorDelContrato = contrato.getSupervisor() != null
                && contrato.getSupervisor().getId().equals(usuario.getId());
        if (!esSupervisorDelContrato) {
            throw new AccesoDenegadoException("El recurso solicitado no existe o no tiene acceso a él.");
        }
    }
}
