package co.sena.sicot.repository;

import co.sena.sicot.entity.Documento;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DocumentoRepository extends JpaRepository<Documento, Long> {

    // Trae subidoPor en el mismo SELECT — sin esto, listar N documentos
    // dispara 1+N consultas solo para poder mostrar quién subió cada uno.
    @EntityGraph(attributePaths = "subidoPor")
    List<Documento> findByContratoIdOrderByFechaSubidaDesc(Long contratoId);
}
