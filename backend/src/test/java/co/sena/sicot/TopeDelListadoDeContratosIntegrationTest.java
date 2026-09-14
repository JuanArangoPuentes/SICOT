package co.sena.sicot;

import co.sena.sicot.entity.Contrato;
import co.sena.sicot.entity.enums.EstadoContrato;
import co.sena.sicot.repository.ContratoRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El listado de contratos está acotado, y el barrido del motor NO.
 *
 * <h2>Por qué existe esta clase</h2>
 * El hallazgo D de la auditoría del 8 de septiembre pedía acotar los listados de
 * tablas que crecen sin techo. {@code alertas} y {@code registros} se acotaron
 * entonces; {@code contratos} se quedó fuera, y {@code GET /api/contratos} —que
 * el panel de GESTIÓN pide en cada carga— seguía devolviendo todos los contratos
 * que hubieran existido.
 *
 * <h2>La trampa que estas pruebas vigilan</h2>
 * Al ponerle el tope apareció algo que no era evidente: el mismo
 * {@code findByEstado} que usaba el listado lo usa
 * {@link co.sena.sicot.automatizacion.LectorDeContratos} para <b>evaluar las
 * reglas de calendario sobre todos los contratos activos</b>. Acotar ese método
 * habría hecho que el motor dejara de mirar los contratos sobrantes.
 *
 * <p>Y ese fallo no se ve. No hay excepción, ni log, ni una pantalla que quede a
 * medias: las alertas de esos contratos sencillamente no se crean nunca, y el
 * sistema parece sano. Por eso el barrido del motor y el listado de pantalla son
 * hoy dos métodos distintos, y por eso la segunda prueba de aquí abajo es más
 * importante que la primera — es la que se pondrá roja si alguien, con buen
 * criterio y sin este contexto, decide unificarlos.
 */
class TopeDelListadoDeContratosIntegrationTest extends PruebaDeIntegracion {

    @Autowired
    private ContratoRepository contratoRepository;

    /** Cuántos contratos siembra cada prueba. Basta con superar el tope pedido. */
    private static final int SEMBRADOS = 12;

    @Test
    @DisplayName("el listado de pantalla respeta el tope y devuelve lo más reciente primero")
    void listadoAcotadoDevuelveSoloElTopeYOrdenadoPorFecha() {
        sembrarContratosActivos(SEMBRADOS);

        List<Contrato> pagina = contratoRepository
                .findAllByOrderByFechaCreacionDesc(PageRequest.of(0, 5));

        assertThat(pagina)
                .as("el tope tiene que recortar: si esto devuelve los %d, el Pageable no se está aplicando",
                        SEMBRADOS)
                .hasSize(5);

        // Descendente, comprobado como "no creciente" y no con un orden exacto:
        // @CreationTimestamp puede dar el mismo instante a dos filas guardadas
        // seguidas, y una aserción de orden estricto sería intermitente por un
        // motivo que no tiene nada que ver con lo que se quiere probar.
        assertThat(pagina)
                .extracting(Contrato::getFechaCreacion)
                .isSortedAccordingTo((a, b) -> b.compareTo(a));
    }

    @Test
    @DisplayName("el barrido del motor sigue viendo TODOS los contratos activos")
    void barridoDelMotorNoEstaAcotado() {
        sembrarContratosActivos(SEMBRADOS);

        List<Contrato> todos = contratoRepository.findByEstado(EstadoContrato.ACTIVO);

        assertThat(todos)
                .as("si esto recorta, el motor deja de evaluar contratos sin que nada falle a la vista")
                .hasSize(SEMBRADOS);
    }

    private void sembrarContratosActivos(int cuantos) {
        for (int i = 0; i < cuantos; i++) {
            Contrato contrato = new Contrato();
            contrato.setNumeroContrato("CO1.PCCNTR.TOPE-" + i);
            contrato.setObjeto("Contrato de prueba para el tope del listado, número " + i);
            contrato.setValor(new BigDecimal("1000000.00"));
            contrato.setEstado(EstadoContrato.ACTIVO);
            contratoRepository.saveAndFlush(contrato);
        }
        assertThat(contratoRepository.count())
                .as("la siembra tiene que haber entrado; si no, las dos pruebas de abajo no prueban nada")
                .isEqualTo(cuantos);
    }
}
