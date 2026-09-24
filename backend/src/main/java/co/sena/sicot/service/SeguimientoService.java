package co.sena.sicot.service;

import co.sena.sicot.automatizacion.FotoDelContrato;
import co.sena.sicot.automatizacion.LectorDeContratos;
import co.sena.sicot.dto.cronograma.CronogramaResponse;
import co.sena.sicot.dto.etapa.EtapaResponse;
import co.sena.sicot.dto.etapa.SubetapaResponse;
import co.sena.sicot.dto.seguimiento.ConteoPorId;
import co.sena.sicot.dto.seguimiento.ContratoSeguimiento;
import co.sena.sicot.dto.seguimiento.DocumentoResumen;
import co.sena.sicot.dto.seguimiento.SeguimientoResponse;
import co.sena.sicot.dto.seguimiento.SupervisorSeguimiento;
import co.sena.sicot.dto.seguimiento.UltimaActividad;
import co.sena.sicot.entity.Contrato;
import co.sena.sicot.entity.Etapa;
import co.sena.sicot.entity.FirmaElectronica;
import co.sena.sicot.entity.Usuario;
import co.sena.sicot.entity.enums.EstadoContrato;
import co.sena.sicot.entity.enums.EstadoEtapa;
import co.sena.sicot.entity.enums.EstadoSubetapa;
import co.sena.sicot.entity.enums.Rol;
import co.sena.sicot.mapper.EtapaMapper;
import co.sena.sicot.repository.AlertaRepository;
import co.sena.sicot.repository.ContratoRepository;
import co.sena.sicot.repository.DocumentoRepository;
import co.sena.sicot.repository.EtapaRepository;
import co.sena.sicot.repository.FirmaElectronicaRepository;
import co.sena.sicot.repository.RegistroRepository;
import co.sena.sicot.repository.UsuarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * En qué parte del proceso va cada supervisor, para el panel del Administrador.
 *
 * <h2>Por qué una sola consulta agregada y no llamadas por contrato</h2>
 * El panel podría armarse desde el navegador con los endpoints que ya existen
 * (contratos, etapas, cronograma, documentos, alertas, registros). Serían seis
 * peticiones por contrato: con treinta contratos abiertos, ciento ochenta viajes
 * cada vez que el Administrador abre la pestaña. Aquí son siete consultas en
 * total, agrupadas por conjunto de contratos, sin importar cuántos haya.
 *
 * <h2>Por qué el semáforo sale de {@link LectorDeContratos}</h2>
 * Es el mismo camino que usan el panel del supervisor y el motor de
 * automatizaciones. Si el Administrador viera un semáforo calculado de otra
 * forma, SICOT volvería a decir dos cosas distintas del mismo contrato el mismo
 * día, que es el problema que {@code CronogramaService} ya resolvió una vez.
 *
 * <p>Solo lee: no crea etapas en contratos que no las tengan (eso lo hace
 * {@code EtapaService.listarPorContrato} cuando el supervisor abre su panel).
 * Un contrato sin etapas se muestra con cero subetapas, que es la verdad.
 */
@Service
public class SeguimientoService {

    /** Los estados en los que un contrato todavía está en manos de su supervisor. */
    static final Set<EstadoContrato> ABIERTOS =
            EnumSet.of(EstadoContrato.BORRADOR, EstadoContrato.ACTIVO, EstadoContrato.SUSPENDIDO);

    private final UsuarioRepository usuarioRepository;
    private final ContratoRepository contratoRepository;
    private final EtapaRepository etapaRepository;
    private final DocumentoRepository documentoRepository;
    private final AlertaRepository alertaRepository;
    private final RegistroRepository registroRepository;
    private final FirmaElectronicaRepository firmaRepository;
    private final LectorDeContratos lectorDeContratos;
    private final Clock reloj;

    public SeguimientoService(UsuarioRepository usuarioRepository, ContratoRepository contratoRepository,
                              EtapaRepository etapaRepository, DocumentoRepository documentoRepository,
                              AlertaRepository alertaRepository, RegistroRepository registroRepository,
                              FirmaElectronicaRepository firmaRepository, LectorDeContratos lectorDeContratos,
                              Clock reloj) {
        this.usuarioRepository = usuarioRepository;
        this.contratoRepository = contratoRepository;
        this.etapaRepository = etapaRepository;
        this.documentoRepository = documentoRepository;
        this.alertaRepository = alertaRepository;
        this.registroRepository = registroRepository;
        this.firmaRepository = firmaRepository;
        this.lectorDeContratos = lectorDeContratos;
        this.reloj = reloj;
    }

    @Transactional(readOnly = true)
    public SeguimientoResponse supervisores() {
        LocalDate hoy = LocalDate.now(reloj);
        List<Contrato> contratos = contratoRepository.findByEstadoInOrderByFechaCreacionDesc(ABIERTOS);
        List<Long> ids = contratos.stream().map(Contrato::getId).toList();

        Map<Long, FotoDelContrato> fotos = lectorDeContratos.de(contratos).stream()
                .collect(Collectors.toMap(FotoDelContrato::contratoId, Function.identity()));
        // Un IN con la lista vacía no es válido en todas las bases: sin
        // contratos abiertos no hay nada que consultar.
        Map<Long, List<Etapa>> etapas = ids.isEmpty() ? Map.of()
                : etapaRepository.findByContratoIdInOrderByNumeroAsc(ids).stream()
                        .collect(Collectors.groupingBy(e -> e.getContrato().getId()));
        Map<Long, List<DocumentoResumen>> documentos = ids.isEmpty() ? Map.of()
                : documentoRepository.resumenPorContratos(ids).stream()
                        .collect(Collectors.groupingBy(DocumentoResumen::contratoId));
        Map<Long, Long> alertas = ids.isEmpty() ? Map.of()
                : alertaRepository.contarSinLeerPorContrato(ids).stream()
                        .collect(Collectors.toMap(ConteoPorId::id, ConteoPorId::total));
        Map<Long, UltimaActividad> ultima = ids.isEmpty() ? Map.of()
                : registroRepository.ultimaPorContrato(ids).stream()
                        .collect(Collectors.toMap(UltimaActividad::contratoId, Function.identity(), (a, b) -> a));
        Map<Long, Long> finalizados = contratoRepository.contarFinalizadosPorSupervisor().stream()
                .collect(Collectors.toMap(ConteoPorId::id, ConteoPorId::total));
        Set<Long> conFirma = firmaRepository.findAllByOrderByFechaAsignacionDesc().stream()
                .filter(FirmaElectronica::isActiva)
                .map(f -> f.getUsuario().getId())
                .collect(Collectors.toSet());

        Map<Long, List<ContratoSeguimiento>> porSupervisor = new LinkedHashMap<>();
        List<ContratoSeguimiento> sinSupervisor = new ArrayList<>();
        for (Contrato contrato : contratos) {
            ContratoSeguimiento fila = fila(contrato, fotos.get(contrato.getId()),
                    etapas.getOrDefault(contrato.getId(), List.of()),
                    documentos.getOrDefault(contrato.getId(), List.of()),
                    alertas.getOrDefault(contrato.getId(), 0L),
                    ultima.get(contrato.getId()), hoy);
            if (contrato.getSupervisor() == null) {
                sinSupervisor.add(fila);
            } else {
                porSupervisor.computeIfAbsent(contrato.getSupervisor().getId(), k -> new ArrayList<>()).add(fila);
            }
        }

        List<SupervisorSeguimiento> supervisores = new ArrayList<>();
        for (Usuario u : usuarioRepository.findByRolOrderByNombreAsc(Rol.SUPERVISOR)) {
            List<ContratoSeguimiento> suyos = porSupervisor.getOrDefault(u.getId(), List.of());
            // Una cuenta desactivada sin contratos abiertos ya no es nadie a
            // quien hacer seguimiento. Con contratos abiertos sí se muestra:
            // es justamente el caso que el Administrador tiene que reasignar.
            if (!u.isActivo() && suyos.isEmpty()) {
                continue;
            }
            supervisores.add(new SupervisorSeguimiento(u.getId(), u.getNombre(), u.getEmail(), u.isActivo(),
                    conFirma.contains(u.getId()), finalizados.getOrDefault(u.getId(), 0L), suyos));
        }
        return new SeguimientoResponse(supervisores, sinSupervisor, Instant.now(reloj));
    }

    private ContratoSeguimiento fila(Contrato c, FotoDelContrato foto, List<Etapa> etapas,
                                     List<DocumentoResumen> documentos, long alertasSinLeer,
                                     UltimaActividad ultima, LocalDate hoy) {
        List<EtapaResponse> etapasDto = etapas.stream().map(EtapaMapper::toResponse).toList();
        Etapa enCurso = etapas.stream().filter(e -> e.getEstado() != EstadoEtapa.COMPLETADA).findFirst().orElse(null);
        SubetapaResponse subEnCurso = etapasDto.stream()
                .flatMap(e -> e.subEtapas().stream())
                .filter(s -> s.estado() == EstadoSubetapa.EN_CURSO)
                .findFirst()
                .orElse(null);
        long total = foto != null ? foto.subetapasTotales() : 0;
        long completadas = foto != null ? foto.subetapasCompletadas() : 0;
        CronogramaResponse cronograma = foto != null ? CronogramaResponse.de(foto.cronograma(hoy)) : null;
        return new ContratoSeguimiento(
                c.getId(), c.getNumeroContrato(), c.getObjeto(), c.getContratista(), c.getEstado(),
                c.getValor(), c.getFechaInicio(), c.getFechaFin(), cronograma,
                completadas, total,
                enCurso != null ? enCurso.getNumero() : null,
                enCurso != null ? enCurso.getNombre() : null,
                subEnCurso, etapasDto,
                documentos,
                alertasSinLeer, ultima);
    }
}
