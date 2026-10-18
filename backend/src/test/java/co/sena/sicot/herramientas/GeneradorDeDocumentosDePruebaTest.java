package co.sena.sicot.herramientas;

import co.sena.sicot.entity.Contrato;
import co.sena.sicot.entity.Usuario;
import co.sena.sicot.ia.BloqueDocumento;
import co.sena.sicot.ia.DocumentoFormal;
import co.sena.sicot.ia.PdfInstitucional;
import co.sena.sicot.ia.PlantillaDocumentoIA;
import co.sena.sicot.ia.PlantillaDocumentoIA.CampoDelDocumento;
import co.sena.sicot.ia.PlantillaDocumentoIA.ColumnaDeTabla;
import co.sena.sicot.ia.PlantillaDocumentoIA.TablaDelDocumento;
import co.sena.sicot.ia.RedactorDeDocumentos;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Genera los documentos de prueba que se reparten con cada versión para probar
 * SICOT a mano: actas de inicio (GCCON-F-018) de contratos ficticios, para
 * cargarlas en Gestión y en el paso 2.1 del supervisor, y una muestra de cada
 * formato que SICOT arma y el supervisor firma.
 *
 * <h2>Por qué salen del mismo motor que los documentos reales</h2>
 * Un acta hecha a mano en un procesador de texto se parecería al formato, pero
 * no probaría nada del extractor: tendría otra disposición del texto. Estas
 * salen de {@link RedactorDeDocumentos} y {@link PdfInstitucional}, los que
 * reproducen las medidas del .docx oficial (docs/formatos/README.md), así que
 * el texto que lee el extractor tiene la misma forma que el de un acta real.
 *
 * <h2>Por qué se distinguen de un documento real</h2>
 * Los contratos, las empresas, los NIT y las personas son inventados, los
 * números de contrato empiezan por 99 (los del SECOP II van hoy por los 10
 * millones, así que la consulta a los datos abiertos no encuentra uno real) y
 * el pie de cada página dice que es un documento de prueba sin validez.
 *
 * <p>No corre con el resto de las pruebas: solo con
 * {@code -Dsicot.documentos-de-prueba=<carpeta>}.
 */
class GeneradorDeDocumentosDePruebaTest {

    private static final String ORIGEN = "DOCUMENTO DE PRUEBA de SICOT — datos ficticios, sin validez oficial. Generado";
    private static final Clock RELOJ = Clock.system(ZoneId.of("America/Bogota"));

    /** Un contrato ficticio y los datos del acta que el contrato no trae. */
    private record Caso(String archivo, Contrato contrato, Map<String, String> datosDelActa, LocalDate fechaDelActa) {
    }

    @Test
    void generarDocumentosDePrueba() throws Exception {
        String carpeta = System.getProperty("sicot.documentos-de-prueba");
        assumeTrue(carpeta != null && !carpeta.isBlank(), "sin -Dsicot.documentos-de-prueba no se genera nada");
        Path actas = Files.createDirectories(Path.of(carpeta, "actas-de-inicio"));
        Path muestras = Files.createDirectories(Path.of(carpeta, "muestras-de-los-formatos-del-supervisor"));
        PdfInstitucional escritor = new PdfInstitucional(RELOJ);
        PlantillaDocumentoIA acta = PlantillaDocumentoIA.CATALOGO.get("ACTA_INICIO");

        List<Caso> casos = casos();
        for (Caso caso : casos) {
            List<BloqueDocumento> bloques = RedactorDeDocumentos.componer(acta, caso.contrato(), caso.fechaDelActa(),
                    null, caso.datosDelActa());
            Files.write(actas.resolve(caso.archivo()), escritor.generar(documento(acta, caso.contrato(), bloques)));
        }

        // Una muestra de cada documento que firma el supervisor, sobre el
        // primer contrato y con los datos de ejemplo del propio formulario.
        Contrato c = casos.get(0).contrato();
        for (String clave : List.of("INFORME_SUPERVISION", "ACTA_RECIBO", "CERTIFICACION_CUMPLIMIENTO", "INFORME_FINAL")) {
            PlantillaDocumentoIA p = PlantillaDocumentoIA.CATALOGO.get(clave);
            String observaciones = p.llevaObservaciones()
                    ? "Durante el periodo se recibieron en la bodega del Centro las cantidades pactadas; los bienes "
                    + "se revisaron contra la ficha técnica y no se encontraron novedades."
                    : null;
            List<BloqueDocumento> bloques = RedactorDeDocumentos.componer(p, c, LocalDate.of(2026, 11, 20),
                    observaciones, datosDeEjemplo(p), tablasDeEjemplo(p));
            Files.write(muestras.resolve("muestra-" + p.codigo().replace(' ', '_') + "-" + clave.toLowerCase() + ".pdf"),
                    escritor.generar(documento(p, c, bloques)));
        }
    }

    private static DocumentoFormal documento(PlantillaDocumentoIA p, Contrato c, List<BloqueDocumento> bloques) {
        return new DocumentoFormal(p.formato(), p.nombre() + " — " + c.getNumeroContrato(), c.getNumeroContrato(),
                c.getSupervisor().getNombre(), ORIGEN, bloques, c.getSupervisor().getId());
    }

    private static Map<String, String> datosDeEjemplo(PlantillaDocumentoIA p) {
        Map<String, String> datos = new LinkedHashMap<>();
        for (CampoDelDocumento campo : p.campos()) {
            datos.put(campo.clave(), campo.ejemplo());
        }
        return datos;
    }

    private static Map<String, List<List<String>>> tablasDeEjemplo(PlantillaDocumentoIA p) {
        Map<String, List<List<String>>> tablas = new LinkedHashMap<>();
        for (TablaDelDocumento t : p.tablas()) {
            tablas.put(t.clave(), List.of(t.columnas().stream().map(ColumnaDeTabla::ejemplo).toList()));
        }
        return tablas;
    }

    private static List<Caso> casos() {
        return List.of(
                caso("01-acta-inicio-suministro-materiales-madera.pdf", 1L, "CO1.PCCNTR.99000101",
                        "Suministro de materiales de formación para el programa de Fabricación de Muebles: tableros de "
                                + "MDF, aglomerado, chapillas y cantos para los ambientes de aprendizaje del Centro",
                        "38450000", "Suministro", "2026-10-21", "2026-12-18",
                        "MADERAS DEL VALLE DE ABURRÁ S.A.S.", "901.234.567-1", "LUISA FERNANDA OSORIO MEJÍA",
                        "Paola Andrea Mejía Ríos", "56901", "2026-10-02",
                        "43.512.908", "Medellín-Antioquia", "71.654.320", "2026-10-15",
                        "ventas@maderasaburra.com.co", "2026-10-20"),
                caso("02-acta-inicio-compraventa-maquinaria.pdf", 2L, "CO1.PCCNTR.99000102",
                        "Compraventa de una sierra escuadradora industrial y una canteadora automática con su "
                                + "instalación, puesta en marcha y capacitación para el taller de carpintería del Centro",
                        "187300000", "Compraventa", "2026-11-03", "2027-02-26",
                        "INDUSTRIAS TECNOMADERA LTDA.", "900.876.543-2", "CARLOS ANDRÉS RESTREPO GIRALDO",
                        "Jorge Iván Cardona Vélez", "57022", "2026-10-09",
                        "98.587.121", "Envigado-Antioquia", "8.402.117", "2026-10-28",
                        "contratos@tecnomadera.co", "2026-11-03"),
                caso("03-acta-inicio-mantenimiento-equipos.pdf", 3L, "CO1.PCCNTR.99000103",
                        "Prestación del servicio de mantenimiento preventivo y correctivo, con suministro de "
                                + "repuestos, para los equipos de los talleres de mobiliario y del laboratorio de acabados",
                        "62750000", "Prestación de servicios", "2026-10-27", "2027-04-30",
                        "SOLUCIONES TÉCNICAS INTEGRALES DE ANTIOQUIA S.A.S.", "901.555.210-8",
                        "SANTIAGO ALBERTO HENAO PÉREZ",
                        "Paola Andrea Mejía Ríos", "56988", "2026-10-06",
                        "43.512.908", "Itagüí-Antioquia", "1.036.622.904", "2026-10-22",
                        "gerencia@stiantioquia.com", "2026-10-26"),
                caso("04-acta-inicio-suministro-herrajes.pdf", 4L, "CO1.PCCNTR.99000104",
                        "Suministro de herrajes, tornillería, bisagras de cazoleta y correderas telescópicas para "
                                + "las prácticas de ensamble del programa de Tecnología en Diseño de Mobiliario",
                        "21980500", "Suministro", "2026-11-10", "2026-12-15",
                        "FERRETERÍA Y HERRAJES LA 80 S.A.S.", "900.112.334-5", "MARÍA CAMILA ZULUAGA ARANGO",
                        "Jorge Iván Cardona Vélez", "57110", "2026-10-30",
                        "98.587.121", "Bello-Antioquia", "1.017.255.680", "2026-11-05",
                        "comercial@herrajesla80.com", "2026-11-10"),
                caso("05-acta-inicio-suministro-pinturas-acabados.pdf", 5L, "CO1.PCCNTR.99000105",
                        "Suministro de lacas, sellantes, tintes, solventes y elementos de protección para el "
                                + "laboratorio de acabados del Centro Tecnológico del Mobiliario",
                        "16420000", "Suministro", "2026-11-17", "2026-12-11",
                        "QUÍMICOS Y ACABADOS DEL SUR S.A.S.", "901.678.901-3", "ANDRÉS FELIPE MARÍN TORO",
                        "Paola Andrea Mejía Ríos", "57203", "2026-11-04",
                        "43.512.908", "Sabaneta-Antioquia", "15.380.441", "2026-11-12",
                        "pedidos@quimicosdelsur.com.co", "2026-11-17"),
                caso("06-acta-inicio-dotacion-mobiliario-aulas.pdf", 6L, "CO1.PCCNTR.99000106",
                        "Adquisición e instalación de mobiliario escolar ergonómico (mesas, sillas y tableros) para "
                                + "cuatro aulas de formación del Centro, incluido el transporte y el retiro del mobiliario antiguo",
                        "94600000", "Compraventa", "2026-11-24", "2027-01-29",
                        "MOBILIARIO CORPORATIVO ANDINO S.A.S.", "900.345.678-9", "DIANA MARCELA LÓPEZ CASTAÑO",
                        "Jorge Iván Cardona Vélez", "57288", "2026-11-11",
                        "98.587.121", "Rionegro-Antioquia", "39.442.016", "2026-11-19",
                        "licitaciones@mobiliarioandino.com", "2026-11-24"));
    }

    private static Caso caso(String archivo, long id, String numero, String objeto, String valor, String tipo,
                             String inicio, String fin, String contratista, String nit, String representante,
                             String supervisor, String registro, String fechaRegistro, String cedulaSupervisor,
                             String expedicion, String cedulaRepresentante, String fechaGarantias, String correo,
                             String fechaDelActa) {
        Usuario s = new Usuario();
        s.setId(100L + id);
        s.setNombre(supervisor);
        s.setEmail("supervisor.prueba" + id + "@soy.sena.edu.co");
        Contrato c = new Contrato();
        c.setId(id);
        c.setNumeroContrato(numero);
        c.setObjeto(objeto);
        c.setValor(new BigDecimal(valor));
        c.setTipoContrato(tipo);
        c.setFechaInicio(LocalDate.parse(inicio));
        c.setFechaFin(LocalDate.parse(fin));
        c.setContratista(contratista);
        c.setContratistaNit(nit);
        c.setRepresentanteLegal(representante);
        c.setLugarEjecucion("Centro Tecnológico del Mobiliario, Itagüí (Antioquia)");
        c.setNumeroRegistroPresupuestal(registro);
        c.setFechaRegistroPresupuestal(LocalDate.parse(fechaRegistro));
        c.setCorreoContratista(correo);
        c.setSupervisor(s);
        Map<String, String> datos = new LinkedHashMap<>();
        datos.put("cedulaSupervisor", cedulaSupervisor);
        datos.put("cedulaRepresentante", cedulaRepresentante);
        datos.put("expedicionCedulaRepresentante", expedicion);
        datos.put("fechaAprobacionGarantias", LocalDate.parse(fechaGarantias)
                .format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")));
        datos.put("correoContratista", correo);
        return new Caso(archivo, c, datos, LocalDate.parse(fechaDelActa));
    }
}
