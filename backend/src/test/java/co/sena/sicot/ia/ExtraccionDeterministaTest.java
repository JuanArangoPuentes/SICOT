package co.sena.sicot.ia;

import co.sena.sicot.dto.ia.ExtraccionContratoResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Los campos con forma fija se sacan sin modelo, y se sacan bien.
 *
 * <h2>Qué vigila esta clase</h2>
 * El texto de abajo es el de un contrato de suministro del SENA con los mismos
 * once campos que se usaron para medir los modelos el 14 de septiembre de 2026.
 * Sobre ese texto, `qwen2.5:3b` tardó 105 s y <b>no consiguió extraer el valor
 * del contrato</b>; `qwen2.5:7b` tardó 137 s y devolvió el representante legal
 * con la cédula pegada detrás. Las dos cosas se comprueban aquí en
 * microsegundos.
 *
 * <p>La prueba del valor y la del representante no son una más de la lista: son
 * exactamente los dos campos donde el modelo falló. Si alguien simplifica estas
 * expresiones regulares, son las que deben ponerse rojas.
 */
class ExtraccionDeterministaTest {

    private final ExtraccionDeterminista extractor = new ExtraccionDeterminista();

    private static final String CONTRATO = """
            REPUBLICA DE COLOMBIA
            SERVICIO NACIONAL DE APRENDIZAJE - SENA
            CENTRO TECNOLOGICO DEL MOBILIARIO - REGIONAL ANTIOQUIA

            CONTRATO DE SUMINISTRO No. CO1.PCCNTR.7788991

            Entre los suscritos a saber: el SERVICIO NACIONAL DE APRENDIZAJE - SENA,
            identificado con NIT 899.999.034-1, y por otra parte la sociedad
            MADERAS Y DISENOS DEL CARIBE S.A.S., identificada con NIT 901.455.876-3,
            representada legalmente por MARTHA LUCIA RESTREPO OSSA, identificada con
            cedula de ciudadania No. 43.618.902, se ha celebrado el presente contrato.

            SEGUNDA. VALOR: El valor total del presente contrato es de CIENTO OCHENTA
            Y CUATRO MILLONES SETECIENTOS CINCUENTA MIL PESOS ($184.750.000) M/CTE,
            incluido IVA y todos los impuestos, tasas y contribuciones a que haya lugar.

            TERCERA. PLAZO DE EJECUCION: El plazo de ejecucion del contrato sera desde
            el 02 de febrero de 2026 hasta el 30 de noviembre de 2026, contados a
            partir de la suscripcion del acta de inicio.

            CUARTA. LUGAR DE EJECUCION: Las obligaciones se ejecutaran en el Centro
            Tecnologico del Mobiliario, municipio de Itagui, departamento de Antioquia.

            QUINTA. IMPUTACION PRESUPUESTAL: El presente contrato se respalda con el
            Registro Presupuestal No. RP-2026-00471 expedido por el area financiera.
            """;

    @Test
    @DisplayName("saca los nueve campos estructurados del contrato")
    void extraeLosCamposConFormaFija() {
        ExtraccionContratoResponse r = extractor.extraer(CONTRATO);

        assertThat(r.idContrato()).isEqualTo("CO1.PCCNTR.7788991");
        assertThat(r.proveedor()).isEqualTo("MADERAS Y DISENOS DEL CARIBE S.A.S.");
        assertThat(r.nit()).isEqualTo("901.455.876-3");
        assertThat(r.vigenciaInicio()).isEqualTo("2026-02-02");
        assertThat(r.vigenciaFin()).isEqualTo("2026-11-30");
        assertThat(r.registroPresupuestal()).isEqualTo("RP-2026-00471");
        assertThat(r.lugarEjecucion()).contains("Itagui");
    }

    @Test
    @DisplayName("el valor sale como entero plano — el modelo de 3B no lo conseguía")
    void extraeElValorComoEnteroPlano() {
        assertThat(extractor.extraer(CONTRATO).valor()).isEqualTo("184750000");
    }

    @Test
    @DisplayName("el representante legal sale sin la cédula pegada — el modelo de 7B la pegaba")
    void extraeElRepresentanteSinArrastrarLaCedula() {
        assertThat(extractor.extraer(CONTRATO).representanteLegal())
                .isEqualTo("MARTHA LUCIA RESTREPO OSSA");
    }

    @Test
    @DisplayName("toma el NIT del contratista, no el del SENA que aparece antes")
    void noSeQuedaConElPrimerNitDelDocumento() {
        // El del SENA (899.999.034-1) aparece dos líneas más arriba. Coger "el
        // primer NIT" habría sido lo natural de escribir y habría estado mal
        // en todos los contratos.
        assertThat(extractor.extraer(CONTRATO).nit()).isNotEqualTo("899.999.034-1");
    }

    @Test
    @DisplayName("deja objeto y tipoContrato al modelo, no los inventa")
    void noTocaLosCamposQueSonTrabajoDelModelo() {
        ExtraccionContratoResponse r = extractor.extraer(CONTRATO);
        assertThat(r.objeto()).isNull();
        assertThat(r.tipoContrato()).isNull();
    }

    @Test
    @DisplayName("ante un documento sin datos devuelve todo nulo, sin adivinar")
    void noAdivinaCuandoElDocumentoNoTraeLosDatos() {
        ExtraccionContratoResponse r = extractor.extraer(
                "FORMATO GCCON-F-018 ACTA DE INICIO. Version 03. Pagina 1 de 2.");

        assertThat(r.idContrato()).isNull();
        assertThat(r.nit()).isNull();
        assertThat(r.valor()).isNull();
        assertThat(r.vigenciaInicio()).isNull();
    }

    @Test
    @DisplayName("un texto vacío o nulo no revienta")
    void toleraTextoVacio() {
        assertThat(extractor.extraer(null).idContrato()).isNull();
        assertThat(extractor.extraer("   ").idContrato()).isNull();
    }
}
