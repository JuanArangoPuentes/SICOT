package co.sena.sicot.repository;

import co.sena.sicot.entity.FormatoDocumental;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FormatoDocumentalRepository extends JpaRepository<FormatoDocumental, Long> {

    Optional<FormatoDocumental> findByCodigoIgnoreCase(String codigo);

    List<FormatoDocumental> findAllByOrderByCodigoAsc();
}
