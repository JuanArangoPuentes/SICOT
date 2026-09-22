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

    // ── El documento como formulario ────────────────────────────────────────
    //
    // Gestión no carga el contrato: carga el acta de inicio y la notificación
    // al supervisor. Los textos de abajo copian la disposición real de esos dos
    // formatos —tabla de etiqueta y valor, no prosa— con datos inventados.
    //
    // Antes de que existieran estas pruebas, de un acta real solo salían el
    // número de contrato y el valor. Proveedor, NIT, representante y fechas
    // volvían vacíos aunque estuvieran impresos en el documento, y el usuario
    // los tenía que teclear a mano creyendo que la lectura automática no servía.

    private static final String ACTA_DE_INICIO = """
            GCCON-F-018 V.04

            PROCESO GESTION CONTRACTUAL
            FORMATO ACTA DE INICIO

            En Itagui- Antioquia el dia 30 de mayo de 2025, entre los suscritos
            LAURA CAROLINA RESTREPO TORO, identificado con cedula de ciudadania
            nro. xxxx, en calidad de supervisor y, de otra parte, CARLOS MARIO
            REINA MEJIA, identificado con cedula de ciudadania nro. 98.587.121 de
            Bello-Antioquia, en calidad de representante legal de EVENTOS
            SUPERNOVA S.A.S., identificada con NIT. 900.478.852-5, hemos convenido
            suscribir el acta de inicio del contrato de la referencia.

            Numero y fecha del registro presupuestal 56725 del 17 de junio de 2025
            Fecha de aprobacion de las garantias 17 de junio de 2025
            Fecha de inicio 17 de junio de 2025
            Fecha de terminacion 17 de diciembre de 2025

            CONTRATO NRO. CO1.PCCNTR.7986334
            TIPO DE CONTRATO SUMINISTRO
            VALOR DEL CONTRATO DIEZ MILLONES DE PESOS ($10.000.000 COP)
            PLAZO DEL CONTRATO  6 Meses
            LUGAR DE EJECUCION CALLE 63 NO. 58 B 03, BARRIO CALATRAVA- ITAGUI, ANTIOQUIA.
            CONTRATISTA EVENTOS SUPERNOVA S.A.S.
            CC o NIT 900.478.852-5
            REPRESENTANTE LEGAL CARLOS MARIO REINA MEJIA
            SUPERVISOR DESIGNADO LAURA CAROLINA RESTREPO TORO

            Elaboro: Valentina Jimenez Giraldo
            Contratista Abogada Apoyo Bienes y Servicios
            """;

    private static final String NOTIFICACION_AL_SUPERVISOR = """
            Itagui, 15 de octubre de 2025

            Senor
            ALEX FERNANDO ZAPATA RIOS
            Supervisor
            Numero de contrato CO1.PCCNTR.8151794
            ASUNTO: Recordatorio de obligaciones como supervisor(a) contractual

            - Objeto: contratar el suministro de materiales para la formacion
            - Valor: $ 39.552.042
            - Fecha de inicio: 11/08/2025
            - Fecha de terminacion: 15/11/2025
            """;

    @Test
    @DisplayName("del acta de inicio saca los datos de la tabla, no solo los de la prosa")
    void leeElActaDeInicioComoFormulario() {
        ExtraccionContratoResponse r = extractor.extraer(ACTA_DE_INICIO);

        assertThat(r.idContrato()).isEqualTo("CO1.PCCNTR.7986334");
        assertThat(r.proveedor()).isEqualTo("EVENTOS SUPERNOVA S.A.S.");
        assertThat(r.nit()).isEqualTo("900.478.852-5");
        assertThat(r.representanteLegal()).isEqualTo("CARLOS MARIO REINA MEJIA");
        assertThat(r.valor()).isEqualTo("10000000");
        assertThat(r.vigenciaInicio()).isEqualTo("2025-06-17");
        assertThat(r.vigenciaFin()).isEqualTo("2025-12-17");
        assertThat(r.registroPresupuestal()).isEqualTo("56725");
    }

    @Test
    @DisplayName("el lugar no se corta en el punto de «NO.» de la dirección")
    void noConfundeLaAbreviaturaDelNumeralConElFinDeLaFrase() {
        // Devolvía «CALLE 63 NO»: el primer punto del texto es el de la
        // abreviatura, no el que cierra la frase.
        assertThat(extractor.extraer(ACTA_DE_INICIO).lugarEjecucion())
                .isEqualTo("CALLE 63 NO. 58 B 03, BARRIO CALATRAVA- ITAGUI, ANTIOQUIA");
    }

    @Test
    @DisplayName("el proveedor no es el cargo de quien elaboró el acta")
    void noTomaElCargoDeLaAbogadaComoProveedor() {
        // El acta cierra con «Contratista Abogada Apoyo Bienes y Servicios».
        // Leer la etiqueta sin distinguir mayúsculas daba eso como proveedor.
        assertThat(extractor.extraer(ACTA_DE_INICIO).proveedor())
                .doesNotContain("Abogada");
    }

    @Test
    @DisplayName("el representante legal es la persona, no la empresa que va en la misma frase")
    void noTomaLaEmpresaComoRepresentanteLegal() {
        // El cuerpo del acta dice «representante legal de EVENTOS SUPERNOVA
        // S.A.S.»: ahí lo que sigue es la empresa.
        assertThat(extractor.extraer(ACTA_DE_INICIO).representanteLegal())
                .doesNotContain("SUPERNOVA");
    }

    @Test
    @DisplayName("de la notificación saca las fechas en dd/MM/yyyy")
    void leeLasFechasNumericasDeLaNotificacion() {
        ExtraccionContratoResponse r = extractor.extraer(NOTIFICACION_AL_SUPERVISOR);

        assertThat(r.idContrato()).isEqualTo("CO1.PCCNTR.8151794");
        assertThat(r.valor()).isEqualTo("39552042");
        assertThat(r.vigenciaInicio()).isEqualTo("2025-08-11");
        assertThat(r.vigenciaFin()).isEqualTo("2025-11-15");
    }

    @Test
    @DisplayName("la notificación no trae contratista, y eso vuelve vacío en vez de inventado")
    void noSeInventaLoQueLaNotificacionNoTrae() {
        ExtraccionContratoResponse r = extractor.extraer(NOTIFICACION_AL_SUPERVISOR);

        assertThat(r.proveedor()).isNull();
        assertThat(r.nit()).isNull();
        assertThat(r.representanteLegal()).isNull();
    }

    @Test
    @DisplayName("el respaldo por etiqueta no le quita el NIT del contratista a la prosa")
    void laProsaSigueMandandoSobreLaEtiqueta() {
        // El contrato en prosa está escrito para saltarse el NIT del SENA. Si
        // la etiqueta ganara, ese cuidado se perdería en el primer documento
        // que trajera las dos formas.
        String mixto = CONTRATO + """

                NIT 899.999.034-1
                CONTRATISTA QUIEN NO ES
                """;

        assertThat(extractor.extraer(mixto).nit()).isEqualTo("901.455.876-3");
        assertThat(extractor.extraer(mixto).proveedor())
                .isEqualTo("MADERAS Y DISENOS DEL CARIBE S.A.S.");
    }

    @Test
    @DisplayName("un registro presupuestal sin numeral ni cifra no se llena con la palabra siguiente")
    void noTomaUnaPalabraCualquieraComoRegistroPresupuestal() {
        assertThat(extractor.extraer("El registro presupuestal del contrato se anexa.")
                .registroPresupuestal()).isNull();
    }
}
