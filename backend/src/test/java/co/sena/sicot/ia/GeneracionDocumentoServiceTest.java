package co.sena.sicot.ia;

import co.sena.sicot.entity.Contrato;
import co.sena.sicot.entity.Documento;
import co.sena.sicot.entity.Subetapa;
import co.sena.sicot.entity.Usuario;
import co.sena.sicot.entity.enums.EstadoDocumento;
import co.sena.sicot.entity.enums.Rol;
import co.sena.sicot.entity.enums.TipoDocumento;
import co.sena.sicot.exception.BusinessException;
import co.sena.sicot.repository.DocumentoRepository;
import co.sena.sicot.repository.SubetapaRepository;
import co.sena.sicot.service.ContratoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * El servicio que redacta con IA los cinco documentos formales del proceso y los
 * deja como borrador para que el supervisor los firme.
 *
 * <p>Su contrato tiene una parte que no es opcional: los datos duros del
 * contrato —número, valor, fechas, contratista— viajan del backend al prompt
 * <b>sin pasar por el modelo</b>, para que el modelo redacte alrededor de ellos
 * pero no pueda inventarlos. Es la regla de no inventar convertida en código, y
 * hasta ahora ninguna prueba comprobaba que siguiera siendo cierta.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GeneracionDocumentoServiceTest {

    @Mock
    private ContratoService contratoService;

    @Mock
    private SubetapaRepository subetapaRepository;

    @Mock
    private DocumentoRepository documentoRepository;

    @Mock
    private OllamaClient ollamaClient;

    private GeneracionDocumentoService servicio;
    private Contrato contrato;

    @BeforeEach
    void construirElServicio() {
        servicio = new GeneracionDocumentoService(
                contratoService, subetapaRepository, documentoRepository, ollamaClient, new SimplePdfWriter());

        Usuario supervisor = new Usuario();
        supervisor.setId(2L);
        supervisor.setNombre("Alex Zapata");
        supervisor.setEmail("supervisor@soy.sena.edu.co");
        supervisor.setRol(Rol.SUPERVISOR);

        contrato = new Contrato();
        contrato.setId(1L);
        contrato.setNumeroContrato("CO1.PCCNTR.7986334");
        contrato.setObjeto("Suministro de materiales para el lote 8");
        contrato.setValor(new BigDecimal("10000000"));
        contrato.setFechaInicio(LocalDate.of(2026, 3, 2));
        contrato.setFechaFin(LocalDate.of(2026, 12, 15));
        contrato.setContratista("EVENTOS SUPERNOVA S.A.S.");
        contrato.setContratistaNit("900123456-7");
        contrato.setRepresentanteLegal("María Fernanda Ruiz");
        contrato.setLugarEjecucion("Centro Tecnológico del Mobiliario, Itagüí");
        contrato.setNumeroRegistroPresupuestal("RP-2026-0451");
        contrato.setSupervisor(supervisor);

        given(contratoService.buscar(1L)).willReturn(contrato);
        given(ollamaClient.generar(anyString(), anyBoolean()))
                .willReturn("El presente documento deja constancia del inicio de la ejecución del contrato.");
        given(documentoRepository.save(any(Documento.class))).willAnswer(i -> i.getArgument(0));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Catálogo de documentos: no se genera nada que no esté confirmado
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void unTipoDeDocumentoFueraDelCatalogoEsUnErrorDeNegocioYNoLlegaAlModelo() {
        assertThatThrownBy(() -> servicio.generar(1L, null, "ACTA_INVENTADA"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("no reconocido");

        verify(ollamaClient, never()).generar(anyString(), anyBoolean());
    }

    @Test
    void elCodigoDelFormatoInstitucionalLlegaAlPromptTalComoEstaEnElCatalogo() {
        servicio.generar(1L, null, "ACTA_INICIO");

        assertThat(promptCapturado())
                .contains("Acta de Inicio")
                .contains("GCCON-F-018");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Los datos duros no pasan por el modelo
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void losDatosRealesDelContratoViajanAlPromptSinPasarPorElModelo() {
        servicio.generar(1L, null, "ACTA_INICIO");

        assertThat(promptCapturado())
                .contains("CO1.PCCNTR.7986334")
                .contains("Suministro de materiales para el lote 8")
                .contains("10000000")
                .contains("02/03/2026")
                .contains("15/12/2026")
                .contains("EVENTOS SUPERNOVA S.A.S.")
                .contains("900123456-7")
                .contains("María Fernanda Ruiz")
                .contains("RP-2026-0451")
                .contains("Alex Zapata");
    }

    @Test
    void alModeloSeLeProhibeExplicitamenteInventarLosDatosQueFalten() {
        servicio.generar(1L, null, "INFORME_SUPERVISION");

        assertThat(promptCapturado())
                .contains("No inventes datos")
                .contains("[dato pendiente]");
    }

    @Test
    void unContratoSinFechasNoInventaFechasSinoQueLasDeclaraNoRegistradas() {
        contrato.setFechaInicio(null);
        contrato.setFechaFin(null);

        servicio.generar(1L, null, "ACTA_INICIO");

        assertThat(promptCapturado()).contains("no registrada");
    }

    @Test
    void unContratoSinSupervisorAsignadoSeDeclaraComoTalEnVezDeQuedarEnBlanco() {
        contrato.setSupervisor(null);

        servicio.generar(1L, null, "ACTA_INICIO");

        assertThat(promptCapturado()).contains("Supervisor: sin asignar");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Aislamiento entre contratos
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Sin la condición de pertenencia en la consulta, un supervisor podía generar
     * un documento en su contrato apuntando a la subetapa de otro, contaminando
     * el avance de un contrato ajeno.
     */
    @Test
    void unaSubetapaQueNoEsDeEsteContratoNoSePuedeUsar() {
        given(subetapaRepository.findByIdAndEtapaContratoId(99L, 1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.generar(1L, 99L, "ACTA_INICIO"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("no pertenece a este contrato");
    }

    @Test
    void unaSubetapaDelPropioContratoSeAsociaAlDocumento() {
        Subetapa subetapa = new Subetapa();
        subetapa.setId(27L);
        given(subetapaRepository.findByIdAndEtapaContratoId(27L, 1L)).willReturn(Optional.of(subetapa));

        servicio.generar(1L, 27L, "ACTA_INICIO");

        assertThat(documentoGuardado().getSubetapa()).isSameAs(subetapa);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Lo que se guarda es un borrador, nunca algo ya dado por bueno
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void elDocumentoSeGuardaComoBorradorPendienteYMarcadoComoGeneradoPorIa() {
        servicio.generar(1L, null, "ACTA_INICIO");

        Documento guardado = documentoGuardado();
        assertThat(guardado.getEstado()).isEqualTo(EstadoDocumento.PENDIENTE);
        assertThat(guardado.isGeneradoPorIa()).isTrue();
        assertThat(guardado.getTipo()).isEqualTo(TipoDocumento.PDF);
        assertThat(guardado.getContentType()).isEqualTo("application/pdf");
        assertThat(guardado.getContrato()).isSameAs(contrato);
    }

    @Test
    void elNombreDelDocumentoIdentificaElFormatoYElContrato() {
        servicio.generar(1L, null, "INFORME_FINAL");

        assertThat(documentoGuardado().getNombre())
                .contains("Informe Final de Supervisión")
                .contains("CO1.PCCNTR.7986334");
    }

    @Test
    void elDocumentoNaceSinFirmaAunqueElContratoTengaSupervisorAsignado() {
        servicio.generar(1L, null, "ACTA_INICIO");

        Documento guardado = documentoGuardado();
        assertThat(guardado.getFirmaId()).isNull();
        assertThat(guardado.getFechaFirma()).isNull();
        assertThat(guardado.getFirmadoPor()).isNull();
    }

    @Test
    void elPdfGuardadoContieneDeVerdadElTextoQueRedactoElModelo() {
        servicio.generar(1L, null, "ACTA_INICIO");

        Documento guardado = documentoGuardado();
        assertThat(guardado.getContenido()).isNotEmpty();
        assertThat(guardado.getTamanioBytes()).isEqualTo(guardado.getContenido().length);

        String textoDelPdf = new PdfTextExtractor().extraerTexto(guardado.getContenido());
        assertThat(textoDelPdf)
                .contains("deja constancia del inicio")
                .contains("CO1.PCCNTR.7986334");
    }

    @Test
    void seLePideTextoLibreAlModeloYNoJson() {
        servicio.generar(1L, null, "ACTA_INICIO");

        verify(ollamaClient).generar(anyString(), org.mockito.ArgumentMatchers.eq(false));
    }

    private String promptCapturado() {
        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(ollamaClient).generar(prompt.capture(), anyBoolean());
        return prompt.getValue();
    }

    private Documento documentoGuardado() {
        ArgumentCaptor<Documento> documento = ArgumentCaptor.forClass(Documento.class);
        verify(documentoRepository).save(documento.capture());
        return documento.getValue();
    }
}
