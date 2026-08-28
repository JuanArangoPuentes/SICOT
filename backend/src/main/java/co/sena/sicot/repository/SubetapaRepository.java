package co.sena.sicot.repository;

import co.sena.sicot.entity.Subetapa;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SubetapaRepository extends JpaRepository<Subetapa, Long> {

    List<Subetapa> findByEtapaIdOrderByCodigoAsc(Long etapaId);

    List<Subetapa> findByEtapaContratoIdOrderByCodigoAsc(Long contratoId);

    /**
     * Busca una subetapa exigiendo que pertenezca al contrato indicado.
     *
     * Es la forma correcta de resolver una subetapa recibida desde una petición:
     * el vínculo con el contrato queda dentro de la consulta, así que no se
     * puede olvidar comprobarlo. Antes cada servicio lo verificaba por su
     * cuenta después de un {@code findById}, y las dos copias divergieron: la
     * ruta de generación de documentos con IA se quedó sin la comprobación, y
     * permitía adjuntar un documento de un contrato a la subetapa de otro.
     */
    Optional<Subetapa> findByIdAndEtapaContratoId(Long id, Long contratoId);
}
