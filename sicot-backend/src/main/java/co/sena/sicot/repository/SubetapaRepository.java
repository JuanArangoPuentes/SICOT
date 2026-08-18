package co.sena.sicot.repository;

import co.sena.sicot.entity.Subetapa;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SubetapaRepository extends JpaRepository<Subetapa, Long> {

    List<Subetapa> findByEtapaIdOrderByCodigoAsc(Long etapaId);

    List<Subetapa> findByEtapaContratoIdOrderByCodigoAsc(Long contratoId);
}
