package co.sena.sicot.repository;

import co.sena.sicot.entity.Etapa;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EtapaRepository extends JpaRepository<Etapa, Long> {

    List<Etapa> findByContratoIdOrderByNumeroAsc(Long contratoId);
}
