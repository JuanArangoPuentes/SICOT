package co.sena.sicot.repository;

import co.sena.sicot.entity.ContactoDelContrato;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ContactoDelContratoRepository extends JpaRepository<ContactoDelContrato, Long> {

    /**
     * Los profesionales de SST y ambiental del contrato, en el orden en que se
     * guardaron. El orden por rol lo pone el servicio: la base ordenaría el rol
     * como texto y dejaría AMBIENTAL antes que SST.
     */
    List<ContactoDelContrato> findByContratoIdOrderByIdAsc(Long contratoId);

    /** Guardar reemplaza la lista completa: ver {@link EntregaAcordadaRepository#borrarDelContrato}. */
    @Modifying
    @Query("delete from ContactoDelContrato c where c.contrato.id = :contratoId")
    void borrarDelContrato(@Param("contratoId") Long contratoId);
}
