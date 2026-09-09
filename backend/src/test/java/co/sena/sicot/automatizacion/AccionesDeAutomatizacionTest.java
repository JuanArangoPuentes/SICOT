package co.sena.sicot.automatizacion;

import co.sena.sicot.automatizacion.acciones.EnvioDeCorreo;
import co.sena.sicot.automatizacion.acciones.RedaccionDeResumenIa;
import co.sena.sicot.entity.Registro;
import co.sena.sicot.entity.enums.EstadoContrato;
import co.sena.sicot.entity.enums.PrioridadAlerta;
import co.sena.sicot.entity.enums.TipoAlerta;
import co.sena.sicot.entity.enums.TipoTareaAutomatizada;
import co.sena.sicot.ia.IaNoDisponibleException;
import co.sena.sicot.ia.OllamaClient;
import co.sena.sicot.repository.RegistroRepository;
import co.sena.sicot.service.AlertaService;
import co.sena.sicot.service.EmailService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Las acciones del motor: lo que de verdad produce el efecto.
 *
 * <h2>Por qué existe esta clase</h2>
 * La auditoría del 8 de septiembre midió el paquete
 * {@code automatizacion.acciones} al 35 % de cobertura, el punto más bajo del
 * proyecto. Las reglas —que solo deciden— estaban bien probadas; las acciones
 * —que envían correos y llaman al modelo— apenas.
 *
 * <p>Es justo al revés de lo que conviene: una regla equivocada produce una
 * alerta de más, y una acción equivocada manda un correo a la persona
 * equivocada o deja una tarea reintentándose contra un servicio caído.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AccionesDeAutomatizacionTest {

    private final PayloadJson payloadJson = new PayloadJson(new ObjectMapper());

    @Mock
    private EmailService emailService;

    @Mock
    private OllamaClient ollamaClient;

    @Mock
    private LectorDeContratos lectorDeContratos;

    @Mock
    private RegistroRepository registroRepository;

    @Mock
    private AlertaService alertaService;

    private EnvioDeCorreo envioDeCorreo;
    private RedaccionDeResumenIa redaccionDeResumen;

    @BeforeEach
    void prepararAcciones() {
        envioDeCorreo = new EnvioDeCorreo(emailService, payloadJson);
        redaccionDeResumen = new RedaccionDeResumenIa(
                ollamaClient, lectorDeContratos, registroRepository, alertaService, payloadJson);
    }

    // ── EnvioDeCorreo ──────────────────────────────────────────────────────

    @Test
    void envíaElCorreoConLosDatosDelPayload() {
        when(emailService.estaConfigurado()).thenReturn(true);

        ResultadoDeAccion resultado = envioDeCorreo.ejecutar(tareaDeCorreo());

        assertThat(resultado.ejecutada()).isTrue();
        verify(emailService).enviar("ana@soy.sena.edu.co", "SICOT — Asignación", "Cuerpo del mensaje.");
    }

    /**
     * Sin SMTP configurado la tarea se DESCARTA, no falla. Es la diferencia
     * entre «no hay nada roto, falta configurar» y «algo se rompió»: tratarlo
     * como fallo llenaría la pantalla de operación de errores rojos permanentes,
     * y el primer fallo real quedaría enterrado entre ellos.
     */
    @Test
    void sinCorreoConfiguradoDescartaEnVezDeFallar() {
        when(emailService.estaConfigurado()).thenReturn(false);

        ResultadoDeAccion resultado = envioDeCorreo.ejecutar(tareaDeCorreo());

        assertThat(resultado.ejecutada()).isFalse();
        assertThat(resultado.motivoDeDescarte()).contains("no está configurado");
        verify(emailService, never()).enviar(anyString(), anyString(), anyString());
    }

    /**
     * Un fallo del servidor de correo debe PROPAGARSE para que el ejecutor lo
     * reintente. Capturarlo aquí para devolver «descartada» convertiría un mal
     * minuto del SMTP en un aviso al supervisor que nadie volverá a intentar.
     */
    @Test
    void unFalloDelServidorDeCorreoSePropagaParaQueSeReintente() {
        when(emailService.estaConfigurado()).thenReturn(true);
        org.mockito.Mockito.doThrow(new IllegalStateException("Connection refused"))
                .when(emailService).enviar(anyString(), anyString(), anyString());

        assertThatThrownBy(() -> envioDeCorreo.ejecutar(tareaDeCorreo()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Connection refused");
    }

    // ── RedaccionDeResumenIa ───────────────────────────────────────────────

    @Test
    void redactaElResumenYLoDejaComoAlertaDeTipoIa() {
        when(lectorDeContratos.porId(7L)).thenReturn(Optional.of(contrato()));
        when(registroRepository.findByContratoIdOrderByFechaDesc(eq(7L), any(Pageable.class)))
                .thenReturn(List.of(registro("SUBETAPA_AVANZADA", "Subetapa 2.3 avanzó a COMPLETADA.")));
        when(ollamaClient.generar(anyString(), eq(false)))
                .thenReturn("Durante la última semana se cerró la subetapa 2.3 del contrato.");

        ResultadoDeAccion resultado = redaccionDeResumen.ejecutar(tareaDeResumen());

        assertThat(resultado.ejecutada()).isTrue();
        verify(alertaService).crearDelSistema(eq(7L), eq(TipoAlerta.IA), eq(PrioridadAlerta.BAJA), anyString());
    }

    /**
     * El prompt lleva los hechos ya resueltos y prohíbe explícitamente añadir
     * nada. No es una precaución teórica: un modelo al que se le pide «resume el
     * estado de este contrato» rellena con recomendaciones procedimentales
     * plausibles y falsas, y en contratación pública una recomendación falsa con
     * aspecto oficial es peor que no decir nada.
     */
    @Test
    void elPromptLlevaLosHechosResueltosYProhibeInventar() {
        when(lectorDeContratos.porId(7L)).thenReturn(Optional.of(contrato()));
        when(registroRepository.findByContratoIdOrderByFechaDesc(eq(7L), any(Pageable.class)))
                .thenReturn(List.of(registro("DOCUMENTO_FIRMADO", "Acta de Inicio firmada.")));
        when(ollamaClient.generar(anyString(), eq(false))).thenReturn("Resumen.");

        redaccionDeResumen.ejecutar(tareaDeResumen());

        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(ollamaClient).generar(prompt.capture(), eq(false));

        assertThat(prompt.getValue())
                .contains("No agregues hechos")
                .contains("No des recomendaciones")
                .as("los hechos entran ya calculados, el modelo solo redacta")
                .contains("CT-2026-001")
                .contains("13 de 27 subetapas completadas");
    }

    /**
     * Un periodo sin actividad no produce resumen. Generarlo sería gastar un
     * cupo del modelo en una frase vacía y ocupar un renglón de la bandeja del
     * supervisor con nada.
     */
    @Test
    void sinMovimientosEnElPeriodoNoGastaElModelo() {
        when(lectorDeContratos.porId(7L)).thenReturn(Optional.of(contrato()));
        when(registroRepository.findByContratoIdOrderByFechaDesc(eq(7L), any(Pageable.class)))
                .thenReturn(List.of());

        ResultadoDeAccion resultado = redaccionDeResumen.ejecutar(tareaDeResumen());

        assertThat(resultado.ejecutada()).isFalse();
        assertThat(resultado.motivoDeDescarte()).contains("Sin movimientos");
        verify(ollamaClient, never()).generar(anyString(), org.mockito.ArgumentMatchers.anyBoolean());
    }

    /** Solo se consideran los movimientos dentro del periodo, no todo el histórico. */
    @Test
    void ignoraLosMovimientosAnterioresAlPeriodo() {
        when(lectorDeContratos.porId(7L)).thenReturn(Optional.of(contrato()));
        when(registroRepository.findByContratoIdOrderByFechaDesc(eq(7L), any(Pageable.class)))
                .thenReturn(List.of(registroDeHace(60, "CONTRATO_CREADO", "Contrato creado.")));

        ResultadoDeAccion resultado = redaccionDeResumen.ejecutar(tareaDeResumen());

        assertThat(resultado.ejecutada()).isFalse();
        assertThat(resultado.motivoDeDescarte()).contains("Sin movimientos");
    }

    @Test
    void siElContratoDesaparecioDescartaEnVezDeFallar() {
        when(lectorDeContratos.porId(7L)).thenReturn(Optional.empty());

        ResultadoDeAccion resultado = redaccionDeResumen.ejecutar(tareaDeResumen());

        assertThat(resultado.ejecutada()).isFalse();
        assertThat(resultado.motivoDeDescarte()).contains("ya no existe");
    }

    /**
     * Si Ollama está caído, la excepción se propaga y el ejecutor reintenta. Un
     * modelo apagado es la situación transitoria por excelencia.
     */
    @Test
    void siElModeloNoRespondeSePropagaParaQueSeReintente() {
        when(lectorDeContratos.porId(7L)).thenReturn(Optional.of(contrato()));
        when(registroRepository.findByContratoIdOrderByFechaDesc(eq(7L), any(Pageable.class)))
                .thenReturn(List.of(registro("SUBETAPA_AVANZADA", "Avance.")));
        when(ollamaClient.generar(anyString(), eq(false)))
                .thenThrow(new IaNoDisponibleException("Ollama no disponible."));

        assertThatThrownBy(() -> redaccionDeResumen.ejecutar(tareaDeResumen()))
                .isInstanceOf(IaNoDisponibleException.class);
        verify(alertaService, never()).crearDelSistema(anyLong(), any(), any(), anyString());
    }

    // ── Utilidades ─────────────────────────────────────────────────────────

    private TareaEnEjecucion tareaDeCorreo() {
        return new TareaEnEjecucion(1L, "supervisor-asignado", TipoTareaAutomatizada.ENVIAR_CORREO, 7L,
                payloadJson.escribir(new PayloadDeTarea.EnviarCorreo(
                        "ana@soy.sena.edu.co", "SICOT — Asignación", "Cuerpo del mensaje.")));
    }

    private TareaEnEjecucion tareaDeResumen() {
        return new TareaEnEjecucion(2L, "resumen-semanal-ia", TipoTareaAutomatizada.REDACTAR_RESUMEN_IA, 7L,
                payloadJson.escribir(new PayloadDeTarea.RedactarResumenIa(7)));
    }

    private FotoDelContrato contrato() {
        return new FotoDelContrato(7L, "CT-2026-001", "Suministro de mobiliario",
                LocalDate.now().minusDays(50), LocalDate.now().plusDays(50),
                EstadoContrato.ACTIVO, "Ana Gómez", "ana@soy.sena.edu.co", 27, 13, 3, 6);
    }

    private Registro registro(String accion, String descripcion) {
        return registroDeHace(1, accion, descripcion);
    }

    private Registro registroDeHace(int dias, String accion, String descripcion) {
        Registro r = new Registro();
        r.setAccion(accion);
        r.setDescripcion(descripcion);
        // `fecha` la gestiona @CreationTimestamp y no tiene setter: en una
        // prueba unitaria sin JPA hay que ponerla por reflexión.
        try {
            var campo = Registro.class.getDeclaredField("fecha");
            campo.setAccessible(true);
            campo.set(r, Instant.now().minusSeconds(dias * 86400L));
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("No se pudo fijar la fecha del registro de prueba.", e);
        }
        return r;
    }
}
