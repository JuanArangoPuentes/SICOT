package co.sena.sicot.automatizacion;

import co.sena.sicot.entity.Contrato;
import co.sena.sicot.entity.Usuario;
import co.sena.sicot.entity.enums.EstadoContrato;
import co.sena.sicot.repository.ContratoRepository;
import co.sena.sicot.repository.SubetapaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * Convierte contratos de la base en {@link FotoDelContrato}, que es lo único que
 * ven las reglas.
 *
 * <p>Es la frontera del módulo: aquí dentro hay entidades y sesión de Hibernate;
 * a partir de aquí, hacia las reglas, solo viajan records inmutables. Concentrar
 * la lectura en un sitio es lo que permite resolver los conteos con una sola
 * consulta agrupada en vez de con una navegación perezosa por contrato.
 */
@Service
public class LectorDeContratos {

    private final ContratoRepository contratoRepository;
    private final SubetapaRepository subetapaRepository;

    public LectorDeContratos(ContratoRepository contratoRepository, SubetapaRepository subetapaRepository) {
        this.contratoRepository = contratoRepository;
        this.subetapaRepository = subetapaRepository;
    }

    /**
     * Los contratos sobre los que tiene sentido evaluar reglas de calendario.
     *
     * <p>Solo los {@code ACTIVO}. Un contrato en BORRADOR todavía no tiene
     * plazos que vigilar, y uno FINALIZADO o CANCELADO ya no los tiene: avisar
     * de que «vence en 15 días» un contrato cancelado hace tres meses es
     * exactamente el tipo de aviso incorrecto que enseña a la gente a ignorar
     * las alertas.
     */
    @Transactional(readOnly = true)
    public List<FotoDelContrato> vigentes() {
        List<Contrato> contratos = contratoRepository.findByEstado(EstadoContrato.ACTIVO);
        if (contratos.isEmpty()) {
            return List.of();
        }
        return conConteos(contratos);
    }

    /** Un contrato concreto, para las reglas de evento. Vacío si ya no existe. */
    @Transactional(readOnly = true)
    public Optional<FotoDelContrato> porId(Long contratoId) {
        return contratoRepository.findById(contratoId)
                .map(contrato -> conConteos(List.of(contrato)).getFirst());
    }

    private List<FotoDelContrato> conConteos(List<Contrato> contratos) {
        List<Long> ids = contratos.stream().map(Contrato::getId).toList();
        Map<Long, ConteoDeSubetapas> conteos = subetapaRepository.contarPorContrato(ids).stream()
                .collect(java.util.stream.Collectors.toMap(ConteoDeSubetapas::contratoId, Function.identity()));

        return contratos.stream().map(contrato -> {
            // Un contrato sin subetapas no aparece en el GROUP BY. No es un caso
            // teórico: ContratoService tiene auto-sanación precisamente porque
            // hubo contratos que quedaron sin sembrar. Cero y cero es la lectura
            // honesta; las reglas ya saben que con cero totales no pueden
            // afirmar nada sobre el avance.
            ConteoDeSubetapas conteo = conteos.getOrDefault(
                    contrato.getId(), new ConteoDeSubetapas(contrato.getId(), 0L, 0L));
            Usuario supervisor = contrato.getSupervisor();
            return new FotoDelContrato(
                    contrato.getId(),
                    contrato.getNumeroContrato(),
                    contrato.getObjeto(),
                    contrato.getFechaInicio(),
                    contrato.getFechaFin(),
                    contrato.getEstado(),
                    supervisor != null ? supervisor.getNombre() : null,
                    supervisor != null ? supervisor.getEmail() : null,
                    conteo.totales(),
                    conteo.completadas());
        }).toList();
    }
}
