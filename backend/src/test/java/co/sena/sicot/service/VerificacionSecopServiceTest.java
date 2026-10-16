package co.sena.sicot.service;

import co.sena.sicot.dto.secop.VerificacionSecopResponse.Diferencia;
import co.sena.sicot.dto.secop.VerificacionSecopResponse.Ficha;
import co.sena.sicot.entity.Contrato;
import co.sena.sicot.entity.Usuario;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La comparación entre lo que SICOT tiene registrado y lo que publica el SECOP
 * II. Los datos son los del contrato real del Lote 4 (CO1.PCCNTR.10057820): el
 * acta escribe «SUMINISTROS MAYBE S.A.S» y «800154801-6», y el SECOP publica
 * «SUMINISTROS MAYBE SAS» y «800154801». Son el mismo contratista y el mismo
 * NIT, y señalarlos como diferencia enseñaría al supervisor a no leer la lista.
 */
class VerificacionSecopServiceTest {

    private static Ficha fichaLote4() {
        return new Ficha("CO1.PCCNTR.10057820", "5_9205_306 Contratar el suministro de materiales", "Aprobado",
                "SUMINISTROS MAYBE SAS", "800154801", "LUZ MARINA MEJIA PEREZ", new BigDecimal("45900300"),
                "67 Dia(s)", LocalDate.of(2026, 10, 13), LocalDate.of(2026, 12, 15), null, null,
                "Alex Fernando Zapata Rios", "Carlos Mario Castañeda Monsalve", "Selección abreviada subasta inversa",
                "Suministros", true, BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("45900300"), null);
    }

    private static Contrato comoEnElActa() {
        Contrato c = new Contrato();
        c.setNumeroContrato("CO1.PCCNTR.10057820");
        c.setValor(new BigDecimal("45900300"));
        c.setFechaFin(LocalDate.of(2026, 12, 15));
        c.setContratista("SUMINISTROS MAYBE S.A.S");
        c.setContratistaNit("800154801-6");
        c.setRepresentanteLegal("LUZ MARINA MEJIA PEREZ");
        Usuario supervisor = new Usuario();
        supervisor.setNombre("Alex Zapata");
        c.setSupervisor(supervisor);
        return c;
    }

    @Test
    @DisplayName("el contrato real cuadra con el SECOP aunque escriban distinto la sociedad, el NIT y el nombre")
    void elContratoDelLote4NoTieneDiferencias() {
        assertThat(VerificacionSecopService.comparar(comoEnElActa(), fichaLote4())).isEmpty();
    }

    @Test
    @DisplayName("un valor o una fecha distintos sí se señalan, con las cifras de cada lado")
    void unValorDigitadoMalSeSenala() {
        Contrato c = comoEnElActa();
        c.setValor(new BigDecimal("45000000"));
        c.setFechaFin(LocalDate.of(2026, 12, 31));

        List<Diferencia> d = VerificacionSecopService.comparar(c, fichaLote4());

        assertThat(d).extracting(Diferencia::campo).containsExactly("Valor", "Fecha de terminación");
        assertThat(d.get(0).enSicot()).isEqualTo("$45.000.000");
        assertThat(d.get(0).enSecop()).isEqualTo("$45.900.300");
        assertThat(d.get(1).enSecop()).isEqualTo("15/12/2026");
    }

    @Test
    @DisplayName("lo que el SECOP publica y SICOT no tiene se señala como dato faltante")
    void unDatoQueFaltaEnSicotSeSenala() {
        Contrato c = comoEnElActa();
        c.setRepresentanteLegal(null);

        assertThat(VerificacionSecopService.comparar(c, fichaLote4()))
                .containsExactly(new Diferencia("Representante legal", "(sin dato)", "LUZ MARINA MEJIA PEREZ"));
    }

    @Test
    @DisplayName("lo que el SECOP no publica no se cuenta como diferencia")
    void loNoPublicadoNoEsDiferencia() {
        Ficha sinRepresentante = new Ficha("CO1.PCCNTR.10057820", null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, null);

        assertThat(VerificacionSecopService.comparar(new Contrato(), sinRepresentante)).isEmpty();
    }

    @Test
    void otraPersonaSiEsDiferencia() {
        assertThat(VerificacionSecopService.mismaPersona("Alex Zapata", "Alex Fernando Zapata Rios")).isTrue();
        assertThat(VerificacionSecopService.mismaPersona("Laura Restrepo", "Alex Fernando Zapata Rios")).isFalse();
        assertThat(VerificacionSecopService.mismaPersona(null, "Alex Fernando Zapata Rios")).isFalse();
        // Un solo nombre en común no identifica a nadie.
        assertThat(VerificacionSecopService.mismaPersona("Alex", "Alex Fernando Zapata Rios")).isFalse();
    }

    @Test
    void elNitSeComparaSinDigitoDeVerificacionNiPuntos() {
        assertThat(VerificacionSecopService.mismoNit("800.154.801-6", "800154801")).isTrue();
        assertThat(VerificacionSecopService.mismoNit("800154802", "800154801")).isFalse();
    }
}
