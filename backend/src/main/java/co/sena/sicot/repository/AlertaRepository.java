package co.sena.sicot.repository;

import co.sena.sicot.entity.Alerta;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AlertaRepository extends JpaRepository<Alerta, Long> {

    /**
     * Alertas de un contrato, acotadas.
     *
     * <p>Llevaba {@code Pageable} desde que el motor de automatizaciones empezó
     * a escribir en esta tabla. Antes daba igual: nada creaba alertas nunca, así
     * que la lista por contrato estaba siempre vacía. Ahora crece sola, y un
     * contrato de varios años acumularía cientos de filas que se cargan enteras
     * en cada apertura del panel.
     */
    List<Alerta> findByContratoIdOrderByFechaCreacionDesc(Long contratoId, Pageable limite);

    List<Alerta> findByContratoIsNullOrderByFechaCreacionDesc();

    // Pageable (no un List sin límite): las alertas se acumulan con el uso
    // normal del sistema y sin tope terminarían cargando toda la tabla.
    List<Alerta> findAllByOrderByFechaCreacionDesc(Pageable pageable);
}
