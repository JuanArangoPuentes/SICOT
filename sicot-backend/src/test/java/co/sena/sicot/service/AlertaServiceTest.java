package co.sena.sicot.service;

import co.sena.sicot.entity.Alerta;
import co.sena.sicot.entity.enums.PrioridadAlerta;
import co.sena.sicot.entity.enums.TipoAlerta;
import co.sena.sicot.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AlertaServiceTest {

    @Mock
    private co.sena.sicot.repository.AlertaRepository alertaRepository;

    @Mock
    private ContratoService contratoService;

    @InjectMocks
    private AlertaService alertaService;

    @Test
    void marcarAlertaInexistenteLanzaResourceNotFoundException() {
        when(alertaRepository.findById(777L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> alertaService.marcarLeida(777L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void marcarAlertaExistenteComoLeida() {
        Alerta alerta = new Alerta();
        alerta.setId(1L);
        alerta.setTipo(TipoAlerta.VENCIMIENTO);
        alerta.setPrioridad(PrioridadAlerta.ALTA);
        alerta.setMensaje("El contrato vence pronto.");
        when(alertaRepository.findById(1L)).thenReturn(Optional.of(alerta));
        when(alertaRepository.save(alerta)).thenAnswer(inv -> inv.getArgument(0));

        var response = alertaService.marcarLeida(1L);

        assertThat(response.leida()).isTrue();
        assertThat(response.tipo()).isEqualTo(TipoAlerta.VENCIMIENTO);
    }
}
