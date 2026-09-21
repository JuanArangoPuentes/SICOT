package co.sena.sicot.automatizacion;

import co.sena.sicot.automatizacion.acciones.EnvioDeCorreo;
import co.sena.sicot.automatizacion.acciones.RedaccionDeResumen;
import co.sena.sicot.entity.Registro;
import co.sena.sicot.entity.enums.EstadoContrato;
import co.sena.sicot.entity.enums.PrioridadAlerta;
import co.sena.sicot.entity.enums.TipoAlerta;
import co.sena.sicot.entity.enums.TipoTareaAutomatizada;
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
 * —que envían correos y componen textos— apenas.
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
    private LectorDeContratos lectorDeContratos;

    @Mock
    private RegistroRepository registroRepository;

    @Mock
    private AlertaService alertaService;

    private EnvioDeCorreo envioDeCorreo;
    private RedaccionDeResumen redaccionDeResumen;

    @BeforeEach
    void prepararAcciones() {
        envioDeCorreo = new EnvioDeCorreo(emailService, payloadJson);
        redaccionDeResumen = new RedaccionDeResumen(
                lectorDeContratos, registroRepository, alertaService, payloadJson);
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

    // -- RedaccionDeResumen -------------------------------------------------

    /**
     * <h2>Por qué estas pruebas afirman el texto exacto</h2>
     * Antes no podían. El resumen lo escribía el modelo local, así que lo único
     * comprobable era que el prompt pidiera las cosas correctas — no que la
     * salida las respetara. Y no las respetaba: ver el encabezado de
     * {@code V16__resumen_semanal_sin_modelo.sql}. Con el texto compuesto por
     * plantillas, lo que llega a la bandeja del supervisor es exactamente lo que
     * se afirma aquí, y una regresión rompe una prueba en lugar de aparecer
     * meses después en un contrato real.
     */
    @Test
    void componeElResumenYLoDejaComoRecordatorioEnLaBandeja() {
        when(lectorDeContratos.porId(7L)).thenReturn(Optional.of(contrato()));
        when(registroRepository.findByContratoIdOrderByFechaDesc(eq(7L), any(Pageable.class)))
                .thenReturn(List.of(registro("SUBETAPA_AVANZADA",
                        "Subetapa 2.3 (Acta) avanzó de EN_CURSO a COMPLETADA.")));

        ResultadoDeAccion resultado = redaccionDeResumen.ejecutar(tareaDeResumen());

        assertThat(resultado.ejecutada()).isTrue();
        ArgumentCaptor<String> texto = ArgumentCaptor.forClass(String.class);
        verify(alertaService).crearDelSistema(eq(7L), eq(TipoAlerta.RECORDATORIO), eq(PrioridadAlerta.BAJA), texto.capture());
        assertThat(texto.getValue())
                .contains("CT-2026-001")
                .contains("se completó la subetapa 2.3")
                .contains("13 de 27 subetapas (48%)");
    }

    /**
     * Las cifras salen de la consulta, no de una interpretación. Es la propiedad
     * que la redacción por modelo no podía dar: en las mediciones del 10 de
     * septiembre el modelo escribió «12 subetapas (44%)» donde el propio prompt
     * decía 11 y 41%.
     */
    @Test
    void elAvanceQueSeAfirmaEsElQueDiceLaBase() {
        when(lectorDeContratos.porId(7L)).thenReturn(Optional.of(contrato()));
        when(registroRepository.findByContratoIdOrderByFechaDesc(eq(7L), any(Pageable.class)))
                .thenReturn(List.of(registro("DOCUMENTO_FIRMADO", "Acta de Inicio firmada.")));

        redaccionDeResumen.ejecutar(tareaDeResumen());

        ArgumentCaptor<String> texto = ArgumentCaptor.forClass(String.class);
        verify(alertaService).crearDelSistema(anyLong(), any(), any(), texto.capture());
        assertThat(texto.getValue())
                .contains("se firmó un documento")
                .contains("13 de 27 subetapas (48%)")
                .as("no aparece ninguna otra cifra de avance")
                .doesNotContain("12 de 27")
                .doesNotContain("14 de 27");
    }

    /** Varias subetapas se enumeran en español, con «y» antes de la última. */
    @Test
    void enumeraVariasSubetapasComoSeEscribeEnEspanol() {
        when(lectorDeContratos.porId(7L)).thenReturn(Optional.of(contrato()));
        when(registroRepository.findByContratoIdOrderByFechaDesc(eq(7L), any(Pageable.class)))
                .thenReturn(List.of(
                        registro("SUBETAPA_AVANZADA", "Subetapa 1.3 (Estudios) avanzó de PENDIENTE a COMPLETADA."),
                        registro("SUBETAPA_AVANZADA", "Subetapa 1.2 (Unidad) avanzó de PENDIENTE a COMPLETADA."),
                        registro("SUBETAPA_AVANZADA", "Subetapa 1.1 (Necesidad) avanzó de EN_CURSO a COMPLETADA.")));

        redaccionDeResumen.ejecutar(tareaDeResumen());

        ArgumentCaptor<String> texto = ArgumentCaptor.forClass(String.class);
        verify(alertaService).crearDelSistema(anyLong(), any(), any(), texto.capture());
        assertThat(texto.getValue()).contains("se completaron las subetapas 1.1, 1.2 y 1.3");
    }

    /**
     * Cada {@code SUBETAPA_AVANZADA} genera además un {@code ETAPA_ACTUALIZADA}
     * con el porcentaje recalculado. Contarlo aparte duplicaría cada avance — y
     * es lo que, al entrar sin filtrar en el prompt, hizo que el modelo volcara
     * los códigos internos de auditoría al texto que lee el supervisor.
     */
    @Test
    void losMovimientosDerivadosNoSeCuentanComoHechosPropios() {
        when(lectorDeContratos.porId(7L)).thenReturn(Optional.of(contrato()));
        when(registroRepository.findByContratoIdOrderByFechaDesc(eq(7L), any(Pageable.class)))
                .thenReturn(List.of(
                        registro("ETAPA_ACTUALIZADA", "Etapa 1 (INICIO) ahora está en EN_CURSO al 17%."),
                        registro("SUBETAPA_AVANZADA", "Subetapa 1.1 (Necesidad) avanzó de EN_CURSO a COMPLETADA.")));

        redaccionDeResumen.ejecutar(tareaDeResumen());

        ArgumentCaptor<String> texto = ArgumentCaptor.forClass(String.class);
        verify(alertaService).crearDelSistema(anyLong(), any(), any(), texto.capture());
        assertThat(texto.getValue())
                .contains("se completó la subetapa 1.1")
                .as("los códigos internos de auditoría no llegan nunca al supervisor")
                .doesNotContain("ETAPA_ACTUALIZADA")
                .doesNotContain("SUBETAPA_AVANZADA");
    }

    /**
     * Un periodo cuya única actividad es derivada no produce resumen: no hubo
     * ningún hecho propio que contar.
     */
    @Test
    void unPeriodoSoloConMovimientosDerivadosNoProduceResumen() {
        when(lectorDeContratos.porId(7L)).thenReturn(Optional.of(contrato()));
        when(registroRepository.findByContratoIdOrderByFechaDesc(eq(7L), any(Pageable.class)))
                .thenReturn(List.of(registro("ETAPA_ACTUALIZADA", "Etapa 1 ahora está en EN_CURSO al 17%.")));

        ResultadoDeAccion resultado = redaccionDeResumen.ejecutar(tareaDeResumen());

        assertThat(resultado.ejecutada()).isFalse();
        assertThat(resultado.motivoDeDescarte()).contains("Sin movimientos");
        verify(alertaService, never()).crearDelSistema(anyLong(), any(), any(), anyString());
    }

    /**
     * Sin subetapas sembradas no se afirma un porcentaje. Decir «0%» sugeriría
     * que no se ha avanzado, cuando lo cierto es que no hay con qué medirlo.
     */
    @Test
    void sinSubetapasSembradasNoAfirmaUnPorcentaje() {
        when(lectorDeContratos.porId(7L)).thenReturn(Optional.of(contratoSinSubetapas()));
        when(registroRepository.findByContratoIdOrderByFechaDesc(eq(7L), any(Pageable.class)))
                .thenReturn(List.of(registro("DOCUMENTO_CARGADO", "Acta cargada.")));

        redaccionDeResumen.ejecutar(tareaDeResumen());

        ArgumentCaptor<String> texto = ArgumentCaptor.forClass(String.class);
        verify(alertaService).crearDelSistema(anyLong(), any(), any(), texto.capture());
        assertThat(texto.getValue())
                .contains("no tiene subetapas registradas")
                .doesNotContain("%");
    }

    /**
     * Un documento que el supervisor pidió al copiloto es actividad del
     * expediente y se cuenta como tal. Hasta el 21-09-2026 la generación no
     * dejaba registro, así que el resumen no tenía cómo saberlo.
     */
    @Test
    void unDocumentoGeneradoConElCopilotoSeCuentaEnElResumen() {
        when(lectorDeContratos.porId(7L)).thenReturn(Optional.of(contrato()));
        when(registroRepository.findByContratoIdOrderByFechaDesc(eq(7L), any(Pageable.class)))
                .thenReturn(List.of(
                        registro("DOCUMENTO_GENERADO", "Acta de Inicio (GCCON-F-018) generado con el copiloto."),
                        registro("DOCUMENTO_CARGADO", "Documento «soporte.pdf» cargado.")));

        redaccionDeResumen.ejecutar(tareaDeResumen());

        ArgumentCaptor<String> texto = ArgumentCaptor.forClass(String.class);
        verify(alertaService).crearDelSistema(anyLong(), any(), any(), texto.capture());
        assertThat(texto.getValue())
                .contains("se generó un documento con el copiloto")
                .contains("se cargó un documento");
    }

    /**
     * Si ninguno de los movimientos del periodo es de los que el resumen narra
     * —un contrato al que solo se le corrigió el objeto, por ejemplo—, no hay
     * resumen. Antes se componía igual y el supervisor recibía un texto que
     * acababa en «durante el periodo .»: una frase vacía con apariencia de
     * resumen.
     */
    @Test
    void unPeriodoSinNadaQueNarrarNoProduceUnaFraseVacia() {
        when(lectorDeContratos.porId(7L)).thenReturn(Optional.of(contrato()));
        when(registroRepository.findByContratoIdOrderByFechaDesc(eq(7L), any(Pageable.class)))
                .thenReturn(List.of(registro("CONTRATO_ACTUALIZADO", "Se actualizó el objeto del contrato.")));

        ResultadoDeAccion resultado = redaccionDeResumen.ejecutar(tareaDeResumen());

        assertThat(resultado.ejecutada()).isFalse();
        assertThat(resultado.motivoDeDescarte()).contains("nada que resumir");
        verify(alertaService, never()).crearDelSistema(anyLong(), any(), any(), anyString());
    }

    /**
     * La excepción a la regla anterior: una alteración de integridad se avisa
     * aunque sea lo único que pasó. Descartar ese resumen por «nada que contar»
     * callaría justo lo que exige que alguien actúe.
     */
    @Test
    void unaAlteracionDeIntegridadSeAvisaAunqueNoHayaNadaMasQueContar() {
        when(lectorDeContratos.porId(7L)).thenReturn(Optional.of(contrato()));
        when(registroRepository.findByContratoIdOrderByFechaDesc(eq(7L), any(Pageable.class)))
                .thenReturn(List.of(registro("INTEGRIDAD_COMPROMETIDA", "La huella del acta no coincide.")));

        ResultadoDeAccion resultado = redaccionDeResumen.ejecutar(tareaDeResumen());

        assertThat(resultado.ejecutada()).isTrue();
        ArgumentCaptor<String> texto = ArgumentCaptor.forClass(String.class);
        verify(alertaService).crearDelSistema(anyLong(), any(), any(), texto.capture());
        assertThat(texto.getValue())
                .contains("durante el periodo no se registró avance ni movimiento de documentos.")
                .contains("huella de integridad")
                .doesNotContain("periodo .");
    }

    /** Un periodo sin actividad no produce resumen: seria un renglon vacio. */
    @Test
    void sinMovimientosEnElPeriodoNoProduceResumen() {
        when(lectorDeContratos.porId(7L)).thenReturn(Optional.of(contrato()));
        when(registroRepository.findByContratoIdOrderByFechaDesc(eq(7L), any(Pageable.class)))
                .thenReturn(List.of());

        ResultadoDeAccion resultado = redaccionDeResumen.ejecutar(tareaDeResumen());

        assertThat(resultado.ejecutada()).isFalse();
        assertThat(resultado.motivoDeDescarte()).contains("Sin movimientos");
        verify(alertaService, never()).crearDelSistema(anyLong(), any(), any(), anyString());
    }

    /** Solo se consideran los movimientos dentro del periodo, no todo el historico. */
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

    // ── Utilidades ─────────────────────────────────────────────────────────

    private TareaEnEjecucion tareaDeCorreo() {
        return new TareaEnEjecucion(1L, "supervisor-asignado", TipoTareaAutomatizada.ENVIAR_CORREO, 7L,
                payloadJson.escribir(new PayloadDeTarea.EnviarCorreo(
                        "ana@soy.sena.edu.co", "SICOT — Asignación", "Cuerpo del mensaje.")));
    }

    private TareaEnEjecucion tareaDeResumen() {
        return new TareaEnEjecucion(2L, "resumen-semanal", TipoTareaAutomatizada.REDACTAR_RESUMEN, 7L,
                payloadJson.escribir(new PayloadDeTarea.RedactarResumen(7)));
    }

    private FotoDelContrato contrato() {
        return new FotoDelContrato(7L, "CT-2026-001", "Suministro de mobiliario",
                LocalDate.now().minusDays(50), LocalDate.now().plusDays(50),
                EstadoContrato.ACTIVO, "Ana Gómez", "ana@soy.sena.edu.co", 27, 13, 3, 6);
    }

    private FotoDelContrato contratoSinSubetapas() {
        return new FotoDelContrato(7L, "CT-2026-002", "Contrato recien creado",
                LocalDate.now().minusDays(5), LocalDate.now().plusDays(100),
                EstadoContrato.ACTIVO, "Ana Gomez", "ana@soy.sena.edu.co", 0, 0, 0, 0);
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
