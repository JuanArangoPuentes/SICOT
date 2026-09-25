package co.sena.sicot.repository;

import co.sena.sicot.entity.Usuario;
import co.sena.sicot.entity.enums.Rol;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    Optional<Usuario> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByEmailAndIdNot(String email, Long id);

    long countByRolAndActivoTrue(Rol rol);

    // Sin tope: son las cuentas de un Centro, decenas y no miles, y el
    // seguimiento del Administrador tiene que listarlas todas.
    List<Usuario> findByRolOrderByNombreAsc(Rol rol);
}
