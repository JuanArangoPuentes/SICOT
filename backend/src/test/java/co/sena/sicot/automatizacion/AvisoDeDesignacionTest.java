package co.sena.sicot.automatizacion;

import co.sena.sicot.automatizacion.reglas.NotificacionDeSupervisorAsignado;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cómo llega el supervisor al contrato según el aviso de designación (paso 1
 * de la lista de chequeo de Alex): con el enlace del instalador y la dirección
 * del servidor si no tiene SICOT, y por el selector de contratos si ya lo
 * tiene, que es la respuesta a la pregunta que Alex dejó en la hoja.
 */
class AvisoDeDesignacionTest {

    private static final String ENLACE = "https://github.com/JuanArangoPuentes/SICOT/releases/latest";

    @Test
    void quienNoLoTieneRecibeElEnlaceYLaDireccionDelServidorEnSuPropioRenglon() {
        String texto = NotificacionDeSupervisorAsignado.comoEntrar(ENLACE, "https://sicot.centro.example");

        assertThat(texto)
                .contains("Si ya tiene SICOT instalado, ábralo: este contrato aparece en el selector de contratos")
                .contains("descárguelo aquí:\n" + ENLACE + "\n")
                .contains("escriba en Configuración la dirección del servidor:\nhttps://sicot.centro.example\n");
    }

    @Test
    void sinDireccionDelServidorNoSePideEscribirNada() {
        assertThat(NotificacionDeSupervisorAsignado.comoEntrar(ENLACE, ""))
                .contains(ENLACE)
                .doesNotContain("Configuración");
    }

    @Test
    void sinEnlaceNoSeOfreceDescargaPeroSiDondeEncontrarElContrato() {
        assertThat(NotificacionDeSupervisorAsignado.comoEntrar("", "https://sicot.centro.example"))
                .startsWith("Ingrese a SICOT")
                .contains("selector de contratos")
                .doesNotContain("descárguelo");
    }
}
