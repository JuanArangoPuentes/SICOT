package co.sena.sicot.ia;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La normalización de la que dependen los atajos sin modelo. Si aquí algo se
 * escapa, una pregunta que tiene respuesta fija acaba en el modelo: minutos de
 * CPU y, para el paso actual, una respuesta que los modelos pequeños dan mal.
 */
class PreguntaNormalizadaTest {

    @Test
    @DisplayName("quita mayúsculas, tildes y signos")
    void quitaMayusculasTildesYSignos() {
        assertThat(PreguntaNormalizada.normalizar("¿Qué es el GCCON-F-031?")).isEqualTo("que es el gccon f 031");
        assertThat(PreguntaNormalizada.normalizar("  ¡SIGUIENTE   sub-paso!  ")).isEqualTo("siguiente sub paso");
        assertThat(PreguntaNormalizada.normalizar(null)).isEmpty();
    }

    /**
     * Algunos teclados mandan la tilde como un carácter aparte (Unicode NFD):
     * «qué» llega como «que» más un acento combinante. Antes no coincidía ni con
     * «qué» ni con «que».
     */
    @Test
    @DisplayName("la tilde escrita como carácter aparte (NFD) da lo mismo que la tilde normal")
    void laTildeDescompuestaDaLoMismo() {
        String descompuesta = "¿Qué me falta?";
        assertThat(PreguntaNormalizada.normalizar(descompuesta)).isEqualTo("que me falta");
        assertThat(PreguntaNormalizada.normalizar("¿Qué me falta?")).isEqualTo("que me falta");
    }

    /**
     * «¿Qué falta?» se encontraba dentro de «porque faltan las fotos», porque se
     * buscaba la subcadena sin mirar dónde empezaba la palabra.
     */
    @Test
    @DisplayName("una frase solo cuenta si empieza en una palabra")
    void unaFraseSoloCuentaSiEmpiezaEnUnaPalabra() {
        PreguntaNormalizada p = PreguntaNormalizada.de("No firmo porque faltan las fotos");

        assertThat(p.contiene("que falta")).isFalse();
        assertThat(p.contiene("faltan las")).isTrue();
        // Por la derecha queda abierta a propósito: «me falta» reconoce «me faltan».
        assertThat(PreguntaNormalizada.de("¿Qué documentos me faltan?").contiene("me falta")).isTrue();
    }

    @Test
    @DisplayName("«es exactamente» compara la pregunta entera, no un trozo")
    void esExactamenteComparaLaPreguntaEntera() {
        Set<String> frases = PreguntaNormalizada.normalizarTodas("qué necesito");

        assertThat(PreguntaNormalizada.de("¿Qué necesito?").esExactamenteAlguna(frases)).isTrue();
        assertThat(PreguntaNormalizada.de("¿qué necesito para la póliza?").esExactamenteAlguna(frases)).isFalse();
    }

    @Test
    @DisplayName("«si» se reconoce como palabra suelta, no dentro de otra")
    void siComoPalabraSuelta() {
        assertThat(PreguntaNormalizada.de("¿Qué hago si no llega?").tienePalabra("si")).isTrue();
        assertThat(PreguntaNormalizada.de("¿Qué sigue?").tienePalabra("si")).isFalse();
    }

    /**
     * 2-10-2026: así se escribe en el teléfono. Sin el mapa, «en q paso voy»
     * no coincidía con ninguna frase y se iba al modelo.
     */
    @Test
    @DisplayName("las abreviaturas del teléfono se escriben enteras, solo como palabra suelta")
    void lasAbreviaturasSeEscribenEnteras() {
        assertThat(PreguntaNormalizada.normalizar("en q paso voy")).isEqualTo("en que paso voy");
        assertThat(PreguntaNormalizada.normalizar("k me falta xq no se")).isEqualTo("que me falta porque no se");
        assertThat(PreguntaNormalizada.normalizar("pa cuando es? tb el rut")).isEqualTo("para cuando es tambien el rut");
        // La «q» dentro de una palabra o de un código no se toca.
        assertThat(PreguntaNormalizada.normalizar("¿qué es el GCCON-F-031?")).isEqualTo("que es el gccon f 031");
    }

    /**
     * «Sí» y el «si» condicional quedaban iguales al quitar la tilde, y los
     * atajos, que descartan las condicionales, mandaban «sí, ¿y ahora qué?» al
     * modelo.
     */
    @Test
    @DisplayName("el «sí» que afirma se quita; el «si» condicional se queda")
    void elSiQueAfirmaSeQuita() {
        assertThat(PreguntaNormalizada.normalizar("Sí, ¿y ahora qué?")).isEqualTo("y ahora que");
        assertThat(PreguntaNormalizada.normalizar("si, y ahora q")).isEqualTo("y ahora que");
        assertThat(PreguntaNormalizada.normalizar("si y ahora que")).isEqualTo("y ahora que");
        assertThat(PreguntaNormalizada.de("¿Qué hago si no llega la póliza?").tienePalabra("si")).isTrue();
        assertThat(PreguntaNormalizada.de("si no llega, ¿qué hago?").tienePalabra("si")).isTrue();
    }

    @Test
    @DisplayName("«contieneFrase» exige el borde de palabra también a la derecha")
    void contieneFraseExigeLosDosBordes() {
        PreguntaNormalizada p = PreguntaNormalizada.de("¿Qué escribo en las observaciones?");

        assertThat(p.contiene("que es")).isTrue();
        assertThat(p.contieneFrase("que es")).isFalse();
        assertThat(PreguntaNormalizada.de("¿Qué es el acta?").contieneFrase("que es")).isTrue();
    }

    @Test
    @DisplayName("separa las otras preguntas de un mismo mensaje, tal como se escribieron")
    void separaLasOtrasPreguntas() {
        assertThat(PreguntaNormalizada.otrasPreguntas("¿Qué es el acta de inicio y cuánto vale el contrato?"))
                .containsExactly("cuánto vale el contrato");
        assertThat(PreguntaNormalizada.otrasPreguntas("¿Qué es el GCCON-F-030? ¿Es lo mismo que el acta de "
                + "liquidación? ¿En qué sub-paso se genera?"))
                .containsExactly("En qué sub-paso se genera");
        // «y» sin palabra interrogativa detrás no abre otra pregunta.
        assertThat(PreguntaNormalizada.otrasPreguntas("¿Quién firma el acta de inicio y el informe final?")).isEmpty();
        assertThat(PreguntaNormalizada.otrasPreguntas(null)).isEmpty();
    }
}
