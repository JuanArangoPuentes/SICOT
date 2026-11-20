package co.sena.sicot.repository;

import co.sena.sicot.entity.SesionCerrada;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface SesionCerradaRepository extends JpaRepository<SesionCerrada, String> {

    /**
     * Borra las filas que ya no protegen de nada: su token habría caducado por
     * su cuenta.
     *
     * <p>{@code @Query} y no {@code deleteByExpiraEnBefore}: el método derivado
     * carga las entidades y las borra una por una, que es exactamente lo que no
     * hay que hacer en una limpieza periódica. Esto es un solo DELETE.
     *
     * @return cuántas filas se borraron, para poder anotarlo en el log
     */
    @Modifying
    @Query("DELETE FROM SesionCerrada s WHERE s.expiraEn < :limite")
    int borrarCaducadas(@Param("limite") Instant limite);
}
