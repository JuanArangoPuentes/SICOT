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
    @DisplayName("sin rótulo de objeto ni de tipo, los deja al modelo en vez de adivinarlos")
    void sinRotuloNoAdivinaObjetoNiTipo() {
        ExtraccionContratoResponse r = extractor.extraer(
                "Contrato CO1.PCCNTR.1 entre las partes, por valor de $1.000.000 pesos.");
        assertThat(r.objeto()).isNull();
        assertThat(r.tipoContrato()).isNull();
    }

    // ── Formatos de la prueba integral del 24-09-2026 ───────────────────────

    @Test
    @DisplayName("notificación con viñetas «Etiqueta: valor»")
    void leeLaNotificacionConVinetas() {
        ExtraccionContratoResponse r = extractor.extraer("""
                Número de contrato CO1.PCCNTR.9100003
                •  Objeto: 05-9-2026-009120 contratar el suministro de insumos de tapicería, espumas y telas para los
                programas de formación en diseño de mobiliario - Lote 3
                •  Contratista: TAPICERÍAS Y ESPUMAS DE ANTIOQUIA S.A.S., NIT 900.112.233-4
                •  Representante legal: LUIS FERNANDO ARANGO ZULUAGA
                •  Valor: $ 54.780.300
                •  Fecha de inicio: 22/09/2026
                •  Registro presupuestal: 81502 del 18/09/2026""");

        assertThat(r.objeto()).isEqualTo("05-9-2026-009120 contratar el suministro de insumos de tapicería, espumas y"
                + " telas para los programas de formación en diseño de mobiliario - Lote 3");
        assertThat(r.proveedor()).isEqualTo("TAPICERÍAS Y ESPUMAS DE ANTIOQUIA S.A.S.");
        assertThat(r.nit()).isEqualTo("900.112.233-4");
        assertThat(r.representanteLegal()).isEqualTo("LUIS FERNANDO ARANGO ZULUAGA");
        assertThat(r.registroPresupuestal()).isEqualTo("81502");
        assertThat(r.valor()).isEqualTo("54780300");
    }

    @Test
    @DisplayName("acta redactada en prosa")
    void leeElActaEnProsa() {
        ExtraccionContratoResponse r = extractor.extraer("""
                ACTA DE INICIO DEL CONTRATO DE PRESTACIÓN DE SERVICIOS No. CO1.PCCNTR.9100002
                se reunieron JORGE ANDRÉS BETANCUR RÚA, en su calidad de supervisor, y el señor HERNÁN
                DARÍO CASTAÑO VÉLEZ, representante legal de MANTENIMIENTOS TÉCNICOS DEL VALLE LTDA, con NIT
                800.765.432-1, con el fin de dar inicio a la ejecución del contrato cuyo objeto es: PRESTAR EL SERVICIO
                DE MANTENIMIENTO PREVENTIVO A LA MAQUINARIA.
                El valor total es de ($86.000.000), amparado en el certificado de registro presupuestal número 80311.
                El lugar de ejecución son las instalaciones del Centro en la Calle 63 No. 58B-03,
                Itagüí.""");

        assertThat(r.proveedor()).isEqualTo("MANTENIMIENTOS TÉCNICOS DEL VALLE LTDA");
        assertThat(r.nit()).isEqualTo("800.765.432-1");
        assertThat(r.representanteLegal()).isEqualTo("HERNÁN DARÍO CASTAÑO VÉLEZ");
        assertThat(r.objeto()).isEqualTo("PRESTAR EL SERVICIO DE MANTENIMIENTO PREVENTIVO A LA MAQUINARIA");
        assertThat(r.registroPresupuestal()).isEqualTo("80311");
        assertThat(r.lugarEjecucion()).isEqualTo("las instalaciones del Centro en la Calle 63 No. 58B-03, Itagüí");
        assertThat(r.tipoContrato()).isEqualTo("Servicios");
    }

    @Test
    @DisplayName("un objeto de tabla en varias líneas no se corta con saltos de línea de Windows")
    void elObjetoDeVariasLineasSaleEnteroAunqueLasLineasTerminenEnCRLF() {
        ExtraccionContratoResponse r = extractor.extraer(
                "TIPO DE CONTRATO COMPRAVENTA\r\nOBJETO \r\n5_9205_278 CONTRATAR EL SERVICIO DE ALQUILER\r\n"
                        + "DE TOLDOS PARA EVENTOS \r\nAL APRENDIZ \r\nVALOR DEL CONTRATO ($10.000.000 COP)\r\n");

        assertThat(r.objeto()).isEqualTo("5_9205_278 CONTRATAR EL SERVICIO DE ALQUILER DE TOLDOS PARA EVENTOS AL APRENDIZ");
        assertThat(r.tipoContrato()).isEqualTo("Compraventa");
    }

    @Test
    @DisplayName("«Contratista» sin dos puntos no es el proveedor: es el cargo de quien elaboró")
    void elCargoDeQuienElaboroNoPasaPorProveedor() {
        ExtraccionContratoResponse r = extractor.extraer(
                "Elaboró: Valentina Jiménez\nContratista Abogada Apoyo Bienes y Servicios\n");
        assertThat(r.proveedor()).isNull();
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

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource(delimiter = '|', value = {
            "contratar el suministro de materiales para la formación | Suministro de Bienes",
            "ADQUISICIÓN POR COMPRAVENTA DE EQUIPOS | Compraventa",
            "arrendamiento de un bien inmueble | Arrendamiento",
            "adecuación de la obra civil del ambiente | Obras",
            "PRESTAR EL SERVICIO DE MANTENIMIENTO PREVENTIVO | Servicios",
    })
    @DisplayName("el tipo se deduce de las palabras del objeto")
    void tipoPorObjeto(String objeto, String tipo) {
        assertThat(ExtraccionDeterminista.tipoPorObjeto(objeto)).isEqualTo(tipo);
    }

    @Test
    @DisplayName("sin una palabra inequívoca, el tipo queda para el modelo")
    void tipoPorObjetoSinPistas() {
        assertThat(ExtraccionDeterminista.tipoPorObjeto("adquisición de herramienta manual")).isNull();
    }

    // ── Hallazgos de la revisión adversarial del 24-09-2026 ─────────────────

    @Test
    @DisplayName("una racha enorme de espacios no dispara un retroceso cuadrático")
    void sinRetrocesoCuadraticoAnteEspaciosLargos() {
        String relleno = " ".repeat(40_000);
        long inicio = System.nanoTime();
        extractor.extraer("Contratista: A" + relleno + "B\nRepresentante legal: A" + relleno
                + "B\nTIPO DE CONTRATO A" + relleno + "B\n");
        long ms = (System.nanoTime() - inicio) / 1_000_000;
        // Antes: 101 s solo con la primera línea. Un segundo es un margen amplio.
        assertThat(ms).isLessThan(1_000);
    }

    @Test
    @DisplayName("el NIT del SENA con dígito de verificación no se toma por el del contratista")
    void elNitDelSenaConDigitoDeVerificacionNoEsElDelContratista() {
        ExtraccionContratoResponse r = extractor.extraer("""
                HERNAN RUIZ, representante legal de MANTENIMIENTOS DEL VALLE LTDA, suscribe con el SERVICIO
                NACIONAL DE APRENDIZAJE SENA, con NIT 899.999.034-1, el presente acta.
                CONTRATISTA MANTENIMIENTOS DEL VALLE LTDA
                CC o NIT 800.765.432-1""");
        assertThat(r.proveedor()).isEqualTo("MANTENIMIENTOS DEL VALLE LTDA");
        assertThat(r.nit()).isEqualTo("800.765.432-1");
    }

    @Test
    @DisplayName("una mención del lugar en prosa no gana a la fila rotulada")
    void laFilaRotuladaDelLugarGanaALaProsa() {
        ExtraccionContratoResponse r = extractor.extraer("""
                El supervisor verificó las condiciones del lugar de ejecución del contrato y las encontró adecuadas.
                LUGAR DE EJECUCIÓN CALLE 63 NO. 58 B 03, BARRIO CALATRAVA- ITAGUI, ANTIOQUIA.""");
        assertThat(r.lugarEjecucion()).isEqualTo("CALLE 63 NO. 58 B 03, BARRIO CALATRAVA- ITAGUI, ANTIOQUIA");
    }

    @Test
    @DisplayName("un formato en blanco no toma un rótulo por el valor de otro")
    void unFormatoEnBlancoNoDevuelveRotulosComoValores() {
        ExtraccionContratoResponse r = extractor.extraer("""
                OBJETO
                VALOR DEL CONTRATO
                PLAZO DEL CONTRATO
                Contratista:
                NIT:
                Representante legal:
                Fecha de inicio:""");
        assertThat(r.objeto()).isNull();
        assertThat(r.proveedor()).isNull();
        assertThat(r.representanteLegal()).isNull();
    }

    @Test
    @DisplayName("el objeto de una notificación termina en la siguiente viñeta")
    void elObjetoDeLaNotificacionNoSeTragaElRestoDelDocumento() {
        ExtraccionContratoResponse r = extractor.extraer("""
                - Objeto: contratar el suministro de materiales para la formación
                - Supervisor: ALEX ZAPATA
                - Dependencia: Almacen
                - VALOR: $ 39.552.042
                Atentamente,
                Coordinador Grupo de Contratacion""");
        assertThat(r.objeto()).isEqualTo("contratar el suministro de materiales para la formación");
    }

    @Test
    @DisplayName("una palabra que empieza como un rótulo no corta el objeto")
    void unaPalabraQueEmpiezaComoRotuloNoCortaElObjeto() {
        ExtraccionContratoResponse r = extractor.extraer("""
                OBJETO CONTRATAR LA ADQUISICION DE MATERIALES PARA LAS
                CANTIDADES MINIMAS DEL AMBIENTE
                VALOR DEL CONTRATO ($1.000.000 COP)""");
        assertThat(r.objeto()).isEqualTo("CONTRATAR LA ADQUISICION DE MATERIALES PARA LAS CANTIDADES MINIMAS DEL AMBIENTE");
    }

    @Test
    @DisplayName("el tipo sale de la primera palabra que lo dice, no de cualquiera")
    void elTipoLoDiceElVerboPrincipal() {
        assertThat(ExtraccionDeterminista.tipoPorObjeto(
                "PRESTACION DEL SERVICIO DE MANTENIMIENTO INCLUIDA LA MANO DE OBRA")).isEqualTo("Servicios");
        assertThat(ExtraccionDeterminista.tipoPorObjeto(
                "PRESTAR EL SERVICIO DE MANTENIMIENTO CON SUMINISTRO DE REPUESTOS")).isEqualTo("Servicios");
    }

    @Test
    @DisplayName("«contrato de obra» en medio del objeto no es el título del documento")
    void unaMencionDeContratoDeObraNoEsElTitulo() {
        assertThat(ExtraccionDeterminista.tipo("OBJETO: INTERVENTORIA TECNICA AL CONTRATO DE OBRA No. 45")).isNull();
    }

    // Los documentos del contrato CO1.PCCNTR.10057820 (Lote 4, Suministros
    // Maybe), que el supervisor entregó el 15-10-2026. Los renglones están
    // copiados tal como los devuelve PDFBox sobre los PDF reales, espacios de
    // sobra incluidos: antes de medirlos se daba por hecho que de esa acta
    // salía todo, y en realidad el valor y la tipología volvían vacíos, y del
    // formato de designación salía un número de contrato truncado.

    private static final String ACTA_LOTE_4 = """
            Número y fecha del registro presupuestal 123726 del 14/10/2026\s
            Fecha de aprobación de las garantías 14/10/2026\s
            Fecha de inicio 14/10/2026\s
            Fecha de terminación 15/12/2026\s

            CONTRATO  CO1.PCCNTR.10057820\s
            TIPOLOGÍA Suministro \s
            OBJETO 5_9205_306 Contratar el suministro de materiales para la formación para los\s
            diferentes programas de formación del Centro Tecnológico del Mobiliario\s
            VALOR 45.900.300 \s
            PLAZO  67 días\s
            LUGAR DE EJECUCIÓN Centro Tecnológico del Mobiliario\s
            CONTRATISTA SUMINISTROS MAYBE S.A.S\s
            NIT. 800154801\s
            REPRESENTANTE LEGAL LUZ MARINA MEJIA PEREZ \s
            SUPERVISOR DESIGNADO ALEX FERNANDO ZAPATA RIOS \s

            2
            GCCON-F-018 V.06

            ALEX FERNANDO ZAPATA RIOS\s
            Supervisor\s

            SUMINISTROS MAYBE S.A.S\s
            NIT. 800154801-6,\s
            Representante legal\s
            maybesuministros@gmail.com\s

            Elaboró: Carolinne Moreno Triana – Apoyo Contratación Bienes y servicios.\s
            """;

    /** La tabla del GCCON-F-032 es estrecha y parte el número en dos renglones. */
    private static final String DESIGNACION_LOTE_4 = """
            1\s
            GCCON-F-032 V.03\s

            FORMATO DE DESIGNACIÓN DE SUPERVISOR Y/O APOYO A LA SUPERVISIÓN\s

            Itagüí, 9 de octubre de 2026 \s

            PARA  Alex Fernando Zapata Rios (Supervisor)\s
                                         Yeison Dariel Zapata Monsalve (Apoyo a la supervisión)\s
            DE  Carlos Mario Castañeda Monsalve\s
            ASUNTO Designación de supervisor(a) y/o apoyo a la supervisión\s

            Me permito comunicarle que ha sido designado(a) como supervisor y/o apoyo a la supervisión del (los)\s
            siguiente(s) contrato(s) o convenios(s).\s

            CONTRATO NRO. CONTRATISTA OBJETO\s
            CO1.PCCNTR.1005
            7820\s
            SUMINISTROS MAYBE S.A.S \s
            5_9205_306 Contratar el suministro\s
            de materiales para la formación para\s
            los diferentes programas de\s
            formación del Centro Tecnológico del\s
            Mobiliario\s
            Link\s
            https://community.secop.gov.co/Public/Tendering/OpportunityDetail/Index?
            noticeUID=CO1.NTC.10815297&isFromPublicArea=True&isModal=False\s

            En caso de presentar incompatibilidad y/o conflicto de interés para ejercer como supervisor(a) y/o apoyo\s
            a la supervisión en el contrato referido, deberá manifestarlo por escrito máximo al día siguiente de la\s
            """;

    private static final String ENLACE_LOTE_4 = "https://community.secop.gov.co/Public/Tendering/OpportunityDetail/"
            + "Index?noticeUID=CO1.NTC.10815297&isFromPublicArea=True&isModal=False";

    @Test
    @DisplayName("de la designación salen el enlace del SECOP entero, el supervisor, el apoyo y la fecha")
    void laDesignacionTraeLoQueNecesitaElPaso1() {
        ExtraccionContratoResponse r = extractor.extraer(DESIGNACION_LOTE_4);

        assertThat(r.enlaceSecop()).isEqualTo(ENLACE_LOTE_4);
        assertThat(r.supervisorDesignado()).isEqualTo("Alex Fernando Zapata Rios");
        assertThat(r.apoyoSupervision()).isEqualTo("Yeison Dariel Zapata Monsalve");
        assertThat(r.fechaDesignacion()).isEqualTo("2026-10-09");
    }

    @Test
    @DisplayName("el contratista y el objeto se leen de la tabla de la designación, sin preguntarle al modelo")
    void laTablaDeLaDesignacionTraeContratistaYObjeto() {
        ExtraccionContratoResponse r = extractor.extraer(DESIGNACION_LOTE_4);

        assertThat(r.proveedor()).isEqualTo("SUMINISTROS MAYBE S.A.S");
        assertThat(r.objeto()).isEqualTo("5_9205_306 Contratar el suministro de materiales para la formación para los"
                + " diferentes programas de formación del Centro Tecnológico del Mobiliario");
        // Con el objeto el tipo se deduce («suministro»): no queda nada para el modelo.
        assertThat(ExtraccionDeterminista.tipoPorObjeto(r.objeto())).isEqualTo("Suministro de Bienes");
    }

    @Test
    @DisplayName("del acta salen el supervisor designado y el correo del contratista, no una fecha de designación")
    void elActaTraeElCorreoDelContratista() {
        ExtraccionContratoResponse r = extractor.extraer(ACTA_LOTE_4);

        assertThat(r.supervisorDesignado()).isEqualTo("ALEX FERNANDO ZAPATA RIOS");
        assertThat(r.correoContratista()).isEqualTo("maybesuministros@gmail.com");
        // El acta no es una designación: su «En Itagüí, el 14 de octubre» no es
        // la fecha en que se designó al supervisor.
        assertThat(r.fechaDesignacion()).isNull();
        assertThat(r.enlaceSecop()).isNull();
    }

    @Test
    @DisplayName("un enlace completo no se pega con la primera palabra del párrafo de abajo")
    void elEnlaceNoArrastraElParrafoSiguiente() {
        assertThat(ExtraccionDeterminista.enlaceSecop("""
                Link
                https://community.secop.gov.co/Public/Tendering/OpportunityDetail/Index?noticeUID=CO1.NTC.1
                Conforme a lo anterior, el supervisor…
                """)).isEqualTo("https://community.secop.gov.co/Public/Tendering/OpportunityDetail/Index?noticeUID=CO1.NTC.1");
    }

    @Test
    @DisplayName("un enlace que no es del SECOP no se toma como enlace del proceso")
    void soloSeTomanEnlacesDelSecop() {
        assertThat(ExtraccionDeterminista.enlaceSecop("Link https://otro-sitio.example/Index?noticeUID=1")).isNull();
    }

    @Test
    @DisplayName("los correos del SENA no se toman como correo del contratista")
    void losCorreosDelSenaNoSonDelContratista() {
        assertThat(ExtraccionDeterminista.correoDelContratista(
                "Supervisor afzapatar@sena.edu.co\nCONTRATISTA Maderas S.A.S\nventas@proveedor.com.co"))
                .isEqualTo("ventas@proveedor.com.co");
        assertThat(ExtraccionDeterminista.correoDelContratista("Elaboró: abogada@soy.sena.edu.co")).isNull();
    }

    // Sondeo del 16-10-2026 sobre los documentos institucionales reales: dos
    // campos salían con datos que no eran del contrato.

    @Test
    @DisplayName("solo se toma el correo que está junto al contratista, no el primero del documento")
    void unCorreoAjenoNoPasaPorElDelContratista() {
        // Sintético: un correo en otra parte del documento no se toma por el del contratista.
        String acta = """
                Numero y fecha del registro presupuestal 56725 del 17 de junio de 2025
                Consultas de la planilla: planillas@operador.com.co

                EVENTOS SUPERNOVA S.A.S.
                NIT. 900.478.852-5,
                Representante legal
                contacto@eventossupernova.com
                """;

        assertThat(ExtraccionDeterminista.correoDelContratista(acta)).isEqualTo("contacto@eventossupernova.com");
    }

    /**
     * El bloque de firmas del acta real CO1.PCCNTR.7986334, tal como lo da el
     * PDF y en una sola línea, como lo junta el .docx del mismo acta. Hasta el
     * 18-10-2026 el .docx no daba ningún correo porque el ancla exigía
     * comienzo de línea: el mismo documento daba resultados distintos.
     */
    @Test
    @DisplayName("el correo del bloque de firma del contratista sale igual del PDF y del .docx")
    void elCorreoDelBloqueDeFirmaSaleIgualDelPdfYDelDocx() {
        String pdf = """
                LAURA CAROLINA RESTREPO TORO
                Supervisora


                CARLOS MARIO REINA MEJÍA
                NIT. 900.478.852-5
                EVENTOS SUPERNOVA S.A.S.
                Representante Legal
                seguridadsocialcres@gmail.com
                """;
        String docx = "LAURA CAROLINA RESTREPO TORO Supervisora CARLOS MARIO REINA MEJÍA NIT. 900.478.852-5 "
                + "EVENTOS SUPERNOVA S.A.S. Representante Legal seguridadsocialcres@gmail.com";

        assertThat(ExtraccionDeterminista.correoDelContratista(pdf)).isEqualTo("seguridadsocialcres@gmail.com");
        assertThat(ExtraccionDeterminista.correoDelContratista(docx)).isEqualTo("seguridadsocialcres@gmail.com");
    }

    /**
     * Un GCCON-F-031 en blanco trae «Valor inicial del contrato $00.000.000».
     * Tomado como valor, llegaba «00000000» a Gestión e impedía completar el
     * valor real desde el SECOP. En el diligenciado, el valor está rotulado
     * «VALOR ACTUAL DEL CONTRATO».
     */
    @Test
    @DisplayName("el marcador $00.000.000 de un formato en blanco no es un valor, y se lee VALOR ACTUAL DEL CONTRATO")
    void elValorEnCeroDeUnFormatoEnBlancoNoEsUnValor() {
        assertThat(extractor.extraer("Valor inicial del contrato $00.000.000\nPlazo inicial del contrato").valor())
                .isNull();
        assertThat(extractor.extraer("""
                Valor inicial del contrato $00.000.000
                VALOR ACTUAL DEL CONTRATO 20.600.000 COP
                """).valor()).isEqualTo("20600000");
        // Los centavos no se suman a la cifra.
        assertThat(extractor.extraer("VALOR DEL CONTRATO 20.000.000,00").valor()).isEqualTo("20000000");
    }

    @Test
    @DisplayName("lee FECHA DE TERMINACIÓN en mayúsculas y las fechas sin «de» antes del año")
    void leeLasFechasEnMayusculasYSinDeAntesDelAno() {
        ExtraccionContratoResponse r = extractor.extraer("""
                FECHA DE INICIO 12 de junio 2026
                FECHA DE TERMINACIÓN 15 de julio 2026
                """);

        assertThat(r.vigenciaInicio()).isEqualTo("2026-06-12");
        assertThat(r.vigenciaFin()).isEqualTo("2026-07-15");
        assertThat(extractor.extraer("FECHA DE TERMINACIÓN 15/12/2026").vigenciaFin()).isEqualTo("2026-12-15");
    }

    @Test
    @DisplayName("un formato en blanco no deja sus marcadores como si fueran datos")
    void losHuecosDelFormatoNoSonDatos() {
        // El GCCON-F-031 sin diligenciar rotulaba el contratista así, y llegaba
        // al formulario de Gestión como el nombre de una empresa.
        ExtraccionContratoResponse r = extractor.extraer("""
                CONTRATISTA [Incluya el nombre del contratista]
                OBJETO [Incluya el objeto del contrato]
                """);

        assertThat(r.proveedor()).isNull();
        assertThat(r.objeto()).isNull();
    }

    @Test
    @DisplayName("del acta real del Lote 4 sale todo, incluido el valor sin símbolo de peso")
    void elActaDelLote4SaleCompleta() {
        ExtraccionContratoResponse r = extractor.extraer(ACTA_LOTE_4);

        assertThat(r.idContrato()).isEqualTo("CO1.PCCNTR.10057820");
        assertThat(r.valor()).isEqualTo("45900300");
        assertThat(r.tipoContrato()).isEqualTo("Suministro de Bienes");
        assertThat(r.proveedor()).isEqualTo("SUMINISTROS MAYBE S.A.S");
        // El acta escribe el NIT sin el dígito de verificación en esta fila; se
        // copia lo que dice el documento, no lo que uno esperaría que dijera.
        assertThat(r.nit()).isEqualTo("800154801");
        assertThat(r.representanteLegal()).isEqualTo("LUZ MARINA MEJIA PEREZ");
        assertThat(r.vigenciaInicio()).isEqualTo("2026-10-14");
        assertThat(r.vigenciaFin()).isEqualTo("2026-12-15");
        assertThat(r.registroPresupuestal()).isEqualTo("123726");
        assertThat(r.lugarEjecucion()).isEqualTo("Centro Tecnológico del Mobiliario");
        assertThat(extractor.estaCompleta(r)).isTrue();
    }

    @Test
    @DisplayName("el número partido por la tabla de la designación se recompone entero")
    void elNumeroPartidoPorLaTablaSaleEntero() {
        assertThat(extractor.extraer(DESIGNACION_LOTE_4).idContrato()).isEqualTo("CO1.PCCNTR.10057820");
    }

    @Test
    @DisplayName("un número completo no se pega con el número de página de abajo")
    void unNumeroCompletoNoArrastraLaLineaSiguiente() {
        assertThat(extractor.extraer("""
                CONTRATO  CO1.PCCNTR.10057820
                2
                GCCON-F-018 V.06
                """).idContrato()).isEqualTo("CO1.PCCNTR.10057820");
    }
}
