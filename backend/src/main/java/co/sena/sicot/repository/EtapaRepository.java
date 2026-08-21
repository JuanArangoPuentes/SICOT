package co.sena.sicot.repository;

import co.sena.sicot.entity.Etapa;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EtapaRepository extends JpaRepository<Etapa, Long> {

    // Trae las subetapas en el mismo SELECT: EtapaService.listarPorContrato
    // siempre las recorre para armar el DTO (y se llama en cada mensaje del
    // chat del Copiloto), así que sin esto son 1+6 consultas cada vez.
    @EntityGraph(attributePaths = "subEtapas")
    List<Etapa> findByContratoIdOrderByNumeroAsc(Long contratoId);
}
