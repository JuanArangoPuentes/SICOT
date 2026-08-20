package co.sena.sicot.service;

import co.sena.sicot.dto.contrato.AsignarSupervisorRequest;
import co.sena.sicot.dto.contrato.CrearContratoRequest;
import co.sena.sicot.exception.BusinessException;
import co.sena.sicot.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContratoServiceTest {

    @Mock
    private co.sena.sicot.repository.ContratoRepository contratoRepository;

    @Mock
    private co.sena.sicot.repository.UsuarioRepository usuarioRepository;

    @Mock
    private RegistroService registroService;

    @Mock
    private co.sena.sicot.repository.EtapaRepository etapaRepository;

    @InjectMocks
    private ContratoService contratoService;

    @Test
    void crearContratoConNumeroDuplicadoLanzaBusinessException() {
        when(contratoRepository.existsByNumeroContrato("CO1.PCCNTR.DUP")).thenReturn(true);

        CrearContratoRequest request = new CrearContratoRequest(
                "CO1.PCCNTR.DUP", "Objeto duplicado", new BigDecimal("100000"), null, null, null,
                null, null, null, null, null, null, null, null);

        assertThatThrownBy(() -> contratoService.crear(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Ya existe un contrato");

        verify(registroService, never()).registrar(any(), any(), any());
    }

    @Test
    void asignarSupervisorInexistenteLanzaResourceNotFoundException() {
        when(contratoRepository.findById(1L))
                .thenReturn(java.util.Optional.of(contratoExistente()));
        when(usuarioRepository.findById(999L)).thenReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> contratoService.asignarSupervisor(1L,
                new AsignarSupervisorRequest(999L)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void crearContratoValidoRegistraAuditoria() {
        when(contratoRepository.existsByNumeroContrato("CO1.PCCNTR.NUEVO")).thenReturn(false);
        co.sena.sicot.entity.Contrato nuevo = new co.sena.sicot.entity.Contrato();
        nuevo.setId(5L);
        nuevo.setNumeroContrato("CO1.PCCNTR.NUEVO");
        when(contratoRepository.save(any(co.sena.sicot.entity.Contrato.class))).thenReturn(nuevo);

        CrearContratoRequest request = new CrearContratoRequest(
                "CO1.PCCNTR.NUEVO", "Objeto nuevo", new BigDecimal("250000"),
                java.time.LocalDate.of(2026, 1, 1), java.time.LocalDate.of(2026, 6, 30), null,
                null, null, null, null, null, null, null, null);

        contratoService.crear(request);

        verify(registroService).registrar(nuevo, "CONTRATO_CREADO",
                "Contrato CO1.PCCNTR.NUEVO creado en estado BORRADOR.");
    }

    @Test
    void asignarSupervisorSinRolDeSupervisorLanzaBusinessException() {
        when(contratoRepository.findById(1L))
                .thenReturn(java.util.Optional.of(contratoExistente()));
        co.sena.sicot.entity.Usuario gestion = new co.sena.sicot.entity.Usuario();
        gestion.setId(2L);
        gestion.setRol(co.sena.sicot.entity.enums.Rol.GESTION);
        when(usuarioRepository.findById(2L)).thenReturn(java.util.Optional.of(gestion));

        assertThatThrownBy(() -> contratoService.asignarSupervisor(1L,
                new AsignarSupervisorRequest(2L)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("no tiene rol de SUPERVISOR");

        verify(contratoRepository, never()).save(any());
        verify(registroService, never()).registrar(any(), any(), any());
    }

    private co.sena.sicot.entity.Contrato contratoExistente() {
        co.sena.sicot.entity.Contrato c = new co.sena.sicot.entity.Contrato();
        c.setId(1L);
        c.setNumeroContrato("CO1.PCCNTR.EXISTENTE");
        c.setObjeto("Objeto existente");
        c.setValor(new BigDecimal("50000"));
        c.setEstado(co.sena.sicot.entity.enums.EstadoContrato.BORRADOR);
        return c;
    }
}
