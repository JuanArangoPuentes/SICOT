package co.sena.sicot.service;

import co.sena.sicot.entity.Contrato;
import co.sena.sicot.entity.Etapa;
import co.sena.sicot.entity.Subetapa;
import co.sena.sicot.entity.enums.EstadoEtapa;
import co.sena.sicot.entity.enums.EstadoSubetapa;
import co.sena.sicot.exception.ResourceNotFoundException;
import co.sena.sicot.repository.ContratoRepository;
import co.sena.sicot.repository.EtapaRepository;
import co.sena.sicot.repository.RegistroRepository;
import co.sena.sicot.repository.SubetapaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@ActiveProfiles("test")
class EtapaServiceTest {

    @Autowired
    private ContratoRepository contratoRepository;

    @Autowired
    private EtapaRepository etapaRepository;

    @Autowired
    private SubetapaRepository subetapaRepository;

    @Autowired
    private RegistroRepository registroRepository;

    private EtapaService etapaService;
    private Contrato contrato;
    private Etapa etapa;

    @BeforeEach
    void setUp() {
        RegistroService registroService = new RegistroService(registroRepository, contratoRepository);
        ContratoService contratoService = new ContratoService(contratoRepository,
                null, registroService, etapaRepository);
        etapaService = new EtapaService(etapaRepository, subetapaRepository,
                contratoService, registroService);

        contrato = new Contrato();
        contrato.setNumeroContrato("CO1.PCCNTR.ETAPA");
        contrato.setObjeto("Objeto de prueba de etapas");
        contrato.setValor(new BigDecimal("100000"));
        contrato = contratoRepository.save(contrato);

        etapa = new Etapa();
        etapa.setContrato(contrato);
        etapa.setNombre("INSPECCIÓN — Monitoreo y Ejecución");
        etapa.setNumero(3);
        etapa.setEstado(EstadoEtapa.PENDIENTE);
        etapa.setPorcentaje(0);
        etapa = etapaRepository.save(etapa);

        Subetapa s1 = subetapa("3.1", "Verificación física de la entrega");
        Subetapa s2 = subetapa("3.2", "Carga de evidencia fotográfica");
        Subetapa s3 = subetapa("3.3", "Comparación cantidad/calidad");
        subetapaRepository.saveAll(List.of(s1, s2, s3));
    }

    private Subetapa subetapa(String codigo, String nombre) {
        Subetapa s = new Subetapa();
        s.setEtapa(etapa);
        s.setCodigo(codigo);
        s.setNombre(nombre);
        s.setEstado(EstadoSubetapa.PENDIENTE);
        s.setResponsable("Supervisor");
        return s;
    }

    @Test
    void completarTodasLasSubetapasMarcaLaEtapaCompletadaAl100() {
        List<Subetapa> subetapas = subetapaRepository.findByEtapaIdOrderByCodigoAsc(etapa.getId());
        for (Subetapa s : subetapas) {
            etapaService.cambiarEstadoSubetapa(s.getId(), EstadoSubetapa.COMPLETADA);
        }

        Etapa recargada = etapaRepository.findById(etapa.getId()).orElseThrow();
        assertThat(recargada.getEstado()).isEqualTo(EstadoEtapa.COMPLETADA);
        assertThat(recargada.getPorcentaje()).isEqualTo(100);

        // 3 subetapas completadas -> 3 SUBETAPA_AVANZADA + 3 recálculos de etapa
        // (0%->33%->67%->100%) con ETAPA_ACTUALIZADA. Ningún retroceso.
        var registros = registroRepository.findByContratoIdOrderByFechaDesc(contrato.getId());
        assertThat(registros).hasSize(6);
        assertThat(registros).filteredOn(r -> r.getAccion().equals("SUBETAPA_AVANZADA")).hasSize(3);
        assertThat(registros).filteredOn(r -> r.getAccion().equals("ETAPA_ACTUALIZADA")).hasSize(3);
        assertThat(registros).noneMatch(r -> r.getAccion().equals("SUBETAPA_REVERTIDA")
                || r.getAccion().equals("ETAPA_RETROCEDIDA"));
    }

    @Test
    void revertirUnaSubetapaCompletadaDejaTrazaDeRetrocesoYBajaElPorcentaje() {
        List<Subetapa> subetapas = subetapaRepository.findByEtapaIdOrderByCodigoAsc(etapa.getId());
        for (Subetapa s : subetapas) {
            etapaService.cambiarEstadoSubetapa(s.getId(), EstadoSubetapa.COMPLETADA);
        }
        // Etapa en COMPLETADA al 100%. Se reabre una subetapa: corrección.
        Long subetapaId = subetapas.getFirst().getId();

        etapaService.cambiarEstadoSubetapa(subetapaId, EstadoSubetapa.PENDIENTE);

        Etapa recargada = etapaRepository.findById(etapa.getId()).orElseThrow();
        assertThat(recargada.getEstado()).isEqualTo(EstadoEtapa.EN_CURSO);
        assertThat(recargada.getPorcentaje()).isEqualTo(67);

        var registros = registroRepository.findByContratoIdOrderByFechaDesc(contrato.getId());
        assertThat(registros).filteredOn(r -> r.getAccion().equals("SUBETAPA_REVERTIDA")).hasSize(1);
        assertThat(registros).filteredOn(r -> r.getAccion().equals("ETAPA_RETROCEDIDA")).hasSize(1);
        assertThat(registros).filteredOn(r -> r.getAccion().equals("SUBETAPA_REVERTIDA"))
                .allMatch(r -> r.getDescripcion().contains("COMPLETADA a PENDIENTE"));
    }

    @Test
    void reenviarElMismoEstadoNoCambiaNadaNiGeneraTraza() {
        Subetapa primera = subetapaRepository.findByEtapaIdOrderByCodigoAsc(etapa.getId()).getFirst();
        etapaService.cambiarEstadoSubetapa(primera.getId(), EstadoSubetapa.COMPLETADA);
        int registrosTrasCompletar = registroRepository.findByContratoIdOrderByFechaDesc(contrato.getId()).size();

        // Reenviar COMPLETADA sobre una subetapa ya COMPLETADA: no-op silencioso.
        etapaService.cambiarEstadoSubetapa(primera.getId(), EstadoSubetapa.COMPLETADA);

        assertThat(registroRepository.findByContratoIdOrderByFechaDesc(contrato.getId()))
                .hasSize(registrosTrasCompletar);
    }

    @Test
    void marcarUnaSubetapaEnCursoPoneLaEtapaEnCurso() {
        Subetapa primera = subetapaRepository.findByEtapaIdOrderByCodigoAsc(etapa.getId()).getFirst();

        etapaService.cambiarEstadoSubetapa(primera.getId(), EstadoSubetapa.EN_CURSO);

        Etapa recargada = etapaRepository.findById(etapa.getId()).orElseThrow();
        assertThat(recargada.getEstado()).isEqualTo(EstadoEtapa.EN_CURSO);
        assertThat(recargada.getPorcentaje()).isEqualTo(0);
    }

    @Test
    void subetapaInexistenteLanzaResourceNotFoundException() {
        assertThatThrownBy(() -> etapaService.cambiarEstadoSubetapa(99999L,
                EstadoSubetapa.COMPLETADA))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
