package co.sena.sicot.repository;

import co.sena.sicot.entity.FirmaElectronica;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FirmaElectronicaRepository extends JpaRepository<FirmaElectronica, Long> {

    List<FirmaElectronica> findAllByOrderByFechaAsignacionDesc();
}
