package co.sena.sicot.service;

import co.sena.sicot.entity.enums.RolDeContacto;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Los correos del estudio previo (paso 2.2). Lo que se comprueba es lo que
 * puede salir mal en un documento real: el rol de un correo no se le pega al
 * siguiente, un correo repetido sale una vez, y la puntuación no termina dentro
 * del correo.
 */
class ContactosEnUnDocumentoTest {

    @Test
    void elRolSeTomaDeLaMencionMasCercanaAntesDelCorreo() {
        List<ContactosEnUnDocumento.Encontrado> encontrados = ContactosEnUnDocumento.leer("""
                Profesional SST y profesional ambiental del Centro:
                SST: Ana María Gómez — amgomez@sena.edu.co
                Ambiental: Carlos Pérez — cperez@sena.edu.co
                """);

        assertThat(encontrados).extracting(ContactosEnUnDocumento.Encontrado::correo)
                .containsExactly("amgomez@sena.edu.co", "cperez@sena.edu.co");
        assertThat(encontrados).extracting(ContactosEnUnDocumento.Encontrado::rol)
                .containsExactly(RolDeContacto.SST, RolDeContacto.AMBIENTAL);
    }

    /** Sin el tope en el correo anterior, «SST» se le sugería también al coordinador. */
    @Test
    void laMencionDeUnCorreoNoSeLePegaAlSiguiente() {
        List<ContactosEnUnDocumento.Encontrado> encontrados = ContactosEnUnDocumento.leer(
                "Seguridad y salud en el trabajo: sst@sena.edu.co Coordinador académico: coordinacion@sena.edu.co");

        assertThat(encontrados).filteredOn(e -> e.correo().equals("coordinacion@sena.edu.co"))
                .singleElement().satisfies(e -> assertThat(e.rol()).isNull());
        // Y los que sí tienen rol van primero, que son los que el supervisor buscaba.
        assertThat(encontrados.getFirst().correo()).isEqualTo("sst@sena.edu.co");
    }

    @Test
    void lasTildesYLasMayusculasNoImpidenReconocerElRol() {
        assertThat(ContactosEnUnDocumento.rolMasCercano("profesional de gestion ambiental:")).isEqualTo(RolDeContacto.AMBIENTAL);
        assertThat(ContactosEnUnDocumento.leer("GESTIÓN AMBIENTAL: Amb@Sena.edu.co").getFirst())
                .satisfies(e -> {
                    assertThat(e.correo()).isEqualTo("amb@sena.edu.co");
                    assertThat(e.rol()).isEqualTo(RolDeContacto.AMBIENTAL);
                });
    }

    @Test
    void unCorreoRepetidoSaleUnaVezYSinElPuntoFinal() {
        List<ContactosEnUnDocumento.Encontrado> encontrados = ContactosEnUnDocumento.leer(
                "Escriba a sst@sena.edu.co. Si no responde, de nuevo a SST@sena.edu.co.");

        assertThat(encontrados).singleElement()
                .satisfies(e -> assertThat(e.correo()).isEqualTo("sst@sena.edu.co"));
    }

    @Test
    void sinCorreosOSinTextoNoSeInventaNada() {
        assertThat(ContactosEnUnDocumento.leer("Profesional SST: por definir")).isEmpty();
        assertThat(ContactosEnUnDocumento.leer("")).isEmpty();
        assertThat(ContactosEnUnDocumento.leer(null)).isEmpty();
    }

    /**
     * Sin tope en la parte local, 40.000 letras sin arroba tardaban 26 s
     * (revisión del 23-10-2026). Con tope, un millón cabe en un segundo.
     */
    @Test
    @org.junit.jupiter.api.Timeout(5)
    void unaTiraLargaSinArrobaNoCuelgaLaBusqueda() {
        String trampa = "a".repeat(1_000_000);

        assertThat(ContactosEnUnDocumento.leer(trampa + " SST: sst.ana@sena.edu.co"))
                .extracting(ContactosEnUnDocumento.Encontrado::correo)
                .containsExactly("sst.ana@sena.edu.co");
    }

    /** «sst» dentro de otra palabra no es una mención de SST. */
    @Test
    void sstDebeSerUnaPalabraCompleta() {
        assertThat(ContactosEnUnDocumento.rolMasCercano("contacto de assttec:")).isNull();
    }
}
