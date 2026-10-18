package co.sena.sicot.ia;

import co.sena.sicot.dto.ia.ChatResponse;
import co.sena.sicot.entity.Contrato;
import co.sena.sicot.entity.Usuario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Lo que el contrato ya dice se contesta con el contrato: el valor exacto y en
 * letras, las fechas, los días que quedan contados desde «hoy» en la zona del
 * Centro, y el atraso tal como lo calcula {@code Cronograma}.
 */
class FichaDelContratoTest {

    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");

    /**
     * Las 21:30 del 1 de octubre en Bogotá son las 02:30 del 2 en UTC: el
     * «hoy» que cuenta es el del Centro, el 1 de octubre.
     */
    private final Clock reloj = Clock.fixed(LocalDate.of(2026, 10, 1).atTime(21, 30).atZone(BOGOTA).toInstant(), BOGOTA);

    private final FichaDelContrato ficha = new FichaDelContrato(reloj);
    private final AtomicInteger calculosDelCronograma = new AtomicInteger();
    private final Supplier<Optional<String>> cronograma = () -> {
        calculosDelCronograma.incrementAndGet();
        return Optional.of("Va con un atraso de 52 puntos.");
    };
    private Contrato contrato;

    @BeforeEach
    void contrato() {
        Usuario supervisor = new Usuario();
        supervisor.setNombre("Alex Zapata");
        contrato = new Contrato();
        contrato.setNumeroContrato("CO1.PCCNTR.7986334");
        contrato.setObjeto("Suministro de materiales para el lote 8.");
        contrato.setValor(new BigDecimal("450000000.00"));
        contrato.setFechaInicio(LocalDate.of(2026, 3, 2));
        contrato.setFechaFin(LocalDate.of(2026, 12, 15));
        contrato.setContratista("EVENTOS SUPERNOVA S.A.S.");
        contrato.setContratistaNit("900123456-7");
        contrato.setSupervisor(supervisor);
    }

    private ChatResponse responder(String pregunta) {
        return ficha.responder(pregunta, contrato, cronograma).orElseThrow();
    }

    @Test
    @DisplayName("el valor sale exacto, con separadores y en letras, como en los PDF")
    void elValorSaleExactoYEnLetras() {
        assertThat(responder("¿cuánto vale el contrato?").respuesta())
                .isEqualTo("Valor del contrato: $450.000.000,00 (CUATROCIENTOS CINCUENTA MILLONES DE PESOS M/CTE).");
        assertThat(calculosDelCronograma.get()).as("el valor no necesita el cronograma").isZero();
    }

    @Test
    @DisplayName("las fechas y los días que quedan, contados desde hoy en la zona del Centro")
    void lasFechasYLosDiasQueQuedan() {
        ChatResponse r = responder("cuando vence");

        assertThat(r.respuesta()).isEqualTo("""
                Fecha de inicio: 02/03/2026.
                Fecha de terminación: 15/12/2026.
                Hoy es 01/10/2026: quedan 75 días calendario hasta la fecha de terminación.""");
        assertThat(r.accion()).isNull();
    }

    @Test
    @DisplayName("«¿cuántos días me quedan?» y «¿voy atrasado?» repiten el cronograma y ofrecen Alertas")
    void elPlazoYElAtrasoRepitenElCronograma() {
        ChatResponse dias = responder("cuantos dias me quedan");
        assertThat(dias.respuesta()).contains("quedan 75 días calendario").endsWith("Cronograma: Va con un atraso de 52 puntos.");
        assertThat(dias.accion().tipo()).isEqualTo(ChatResponse.TipoAccion.MOSTRAR_ALERTAS);

        ChatResponse atraso = responder("voy atrasado?");
        assertThat(atraso.respuesta()).isEqualTo("""
                Hoy es 01/10/2026: quedan 75 días calendario hasta la fecha de terminación.
                Cronograma: Va con un atraso de 52 puntos.""");
        assertThat(atraso.accion().tipo()).isEqualTo(ChatResponse.TipoAccion.MOSTRAR_ALERTAS);
    }

    @Test
    @DisplayName("un plazo vencido se dice como vencido")
    void unPlazoVencido() {
        contrato.setFechaFin(LocalDate.of(2026, 9, 21));

        assertThat(responder("ya se venció el contrato?").respuesta())
                .contains("Hoy es 01/10/2026: el plazo terminó hace 10 días.");
    }

    @Test
    @DisplayName("lo que el contrato no trae se dice «no registrado», nunca se rellena")
    void loQueNoTraeSeDiceNoRegistrado() {
        contrato.setValor(null);
        contrato.setFechaFin(null);
        contrato.setRepresentanteLegal(null);

        assertThat(responder("cuanto vale el contrato").respuesta()).isEqualTo("Valor del contrato: no registrado.");
        assertThat(responder("cuantos dias le quedan").respuesta())
                .contains("Fecha de terminación: no registrada.")
                .contains("sin fecha de terminación registrada no puedo calcular cuántos días quedan");
        assertThat(responder("quien es el contratista").respuesta())
                .isEqualTo("Contratista: EVENTOS SUPERNOVA S.A.S. NIT: 900123456-7. Representante legal: no registrado.");
    }

    @Test
    @DisplayName("«resume el contrato» da los datos fijos y el cronograma")
    void resumeElContrato() {
        String r = responder("resume el contrato").respuesta();

        assertThat(r).startsWith("Contrato Nro.: CO1.PCCNTR.7986334.\nObjeto: Suministro de materiales para el lote 8.\n")
                .contains("Valor del contrato: $450.000.000,00")
                .contains("Supervisor: Alex Zapata.")
                .endsWith("Cronograma: Va con un atraso de 52 puntos.");
    }

    @Test
    @DisplayName("no contesta lo que es de otro documento, lo condicional ni lo que no pregunta por el contrato")
    void noContestaLoQueNoEsSuyo() {
        assertThat(ficha.reconocer("¿cuándo vence la póliza?")).isEmpty();
        assertThat(ficha.reconocer("¿cuánto vale la factura?")).isEmpty();
        assertThat(ficha.reconocer("¿qué pasa si se vence el contrato?")).isEmpty();
        assertThat(ficha.reconocer("¿cómo voy a firmar el acta?")).isEmpty();
        assertThat(ficha.reconocer("el contratista no ha entregado")).isEmpty();
        assertThat(ficha.reconocer("hola")).isEmpty();
        assertThat(ficha.responder("hola", contrato, cronograma)).isEmpty();
    }

    @Test
    @DisplayName("si pregunta dos cosas del contrato, contesta las dos")
    void dosCosasDelContrato() {
        assertThat(responder("cuanto vale y cuando vence el contrato").respuesta())
                .startsWith("Valor del contrato: $450.000.000,00")
                .contains("Fecha de terminación: 15/12/2026.");
    }
}
