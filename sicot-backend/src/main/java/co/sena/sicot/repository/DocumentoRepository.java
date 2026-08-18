package co.sena.sicot.repository;

import co.sena.sicot.entity.Documento;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DocumentoRepository extends JpaRepository<Documento, Long> {

    List<Documento> findByContratoIdOrderByFechaSubidaDesc(Long contratoId);
}
