package co.sena.sicot.ia;

import co.sena.sicot.ia.BloqueDocumento.Alineacion;
import co.sena.sicot.ia.BloqueDocumento.Estilo;
import co.sena.sicot.ia.BloqueDocumento.Tramo;
import co.sena.sicot.ia.FormatoInstitucional.Familia;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.Clock;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Locale;

/**
 * Dibuja un {@link DocumentoFormal} como el formato oficial del SENA al que
 * pertenece: el logo del SENA en el encabezado, la tabla de clasificación de
 * la información, fichas y tablas con filetes negros de ½ pt, letra Calibri
 * (Carlito) o Arial (Liberation Sans) incrustada, y al pie el número de página
 * y el código con su versión. Las medidas de cada formato están en su
 * {@link FormatoInstitucional}.
 *
 * <h2>Por qué se rehízo</h2>
 * Hasta el 28-09-2026 los documentos salían con una franja verde «SICOT»,
 * Helvetica y fichas con fondo verde: se leían como un reporte del sistema, no
 * como el Acta de Inicio o el Informe Final que el Centro archiva y sube al
 * SECOP. Las medidas salen de los formatos reales (el .docx del GCCON-F-018 y
 * del GCCON-F-031, los PDF del GCCON-F-030 y del certificado ESUCON y la hoja
 * GIL-F-010).
 *
 * <h2>Lo que no es del formato, y se dice</h2>
 * Dos cosas se añaden: una línea de trazabilidad pequeña y gris al pie
 * («Generado en SICOT…», y si intervino el Copiloto IA, también eso), y los
 * datos que SICOT no tiene, marcados «[dato pendiente…]» en rojo, que es como
 * los propios formatos señalan lo que falta diligenciar.
 *
 * <h2>Ancla de la firma</h2>
 * El PDF guarda en sus propiedades dónde quedó el hueco de la firma del
 * supervisor ({@value #PROPIEDAD_ANCLA_FIRMA}), para que al firmar se estampe
 * ahí sin volver a generar el documento.
 */
@Component
public class PdfInstitucional {

    /** Propiedad del PDF con la página y el recuadro de la firma: «página;x;y;ancho;alto», en puntos PDF. */
    public static final String PROPIEDAD_ANCLA_FIRMA = "SICOT-AnclaFirma";

    static final float ANCHO = PDRectangle.LETTER.getWidth();
    static final float ALTO = PDRectangle.LETTER.getHeight();

    private static final Color NEGRO = Color.BLACK;
    private static final Color BLANCO = Color.WHITE;
    /** Fila «GESTIÓN CONTRACTUAL» del encabezado del GCCON-F-030. */
    private static final Color GRIS_FILA = new Color(0xF2, 0xF2, 0xF2);
    /** Encabezados de tabla de los formatos. */
    private static final Color GRIS_ENCABEZADO = new Color(0xD9, 0xD9, 0xD9);
    /** Bordes claros de la tabla de clasificación («Grid Table 4» de Word). */
    private static final Color GRIS_BORDE = new Color(0x66, 0x66, 0x66);
    /** El rojo con que los formatos marcan lo que falta diligenciar. */
    static final Color ROJO_PENDIENTE = new Color(0xC0, 0x00, 0x00);
    private static final Color GRIS_TRAZA = new Color(0x7F, 0x7F, 0x7F);

    /** El filete de ½ pt de Word, que en los PDF medidos sale de 0,48 pt. */
    static final float BORDE = 0.48f;
    /** Sangría francesa de los apartados numerados: 567 twips (1 cm). */
    private static final float SANGRIA_SECCION = 28.35f;

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final Clock reloj;

    public PdfInstitucional(Clock reloj) {
        this.reloj = reloj;
    }

    public byte[] generar(DocumentoFormal documentoFormal) {
        try (PDDocument pdf = new PDDocument()) {
            Lienzo l = new Lienzo(pdf, documentoFormal);
            l.primeraPagina();
            for (BloqueDocumento bloque : documentoFormal.bloques()) {
                switch (bloque) {
                    case BloqueDocumento.Letra letra -> l.letra(letra);
                    case BloqueDocumento.Titulo t -> l.titulo(t);
                    case BloqueDocumento.Seccion s -> l.seccion(s);
                    case BloqueDocumento.Parrafo p -> l.parrafo(p);
                    case BloqueDocumento.Ficha f -> l.ficha(f);
                    case BloqueDocumento.Tabla t -> l.tabla(t);
                    case BloqueDocumento.Firmas f -> l.firmas(f);
                    case BloqueDocumento.NotaPequena n -> l.notaPequena(n.texto());
                    case BloqueDocumento.LineasEnBlanco b -> l.espacio(b.lineas() * l.interlinea());
                    case BloqueDocumento.Espacio e -> l.espacio(e.puntos());
                    case BloqueDocumento.MantenerJunto m -> l.asegurar(m.puntos());
                    case BloqueDocumento.HojaDeRecibo h -> l.hojaDeRecibo(h);
                }
            }
            l.cs.close();
            l.pies();
            propiedades(pdf, documentoFormal, l.anclaFirma);

            ByteArrayOutputStream salida = new ByteArrayOutputStream();
            pdf.save(salida);
            return salida.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo generar el PDF del documento.", e);
        }
    }

    private void propiedades(PDDocument pdf, DocumentoFormal d, String anclaFirma) {
        PDDocumentInformation info = pdf.getDocumentInformation();
        info.setTitle(d.titulo());
        if (d.autor() != null && !d.autor().isBlank()) {
            info.setAuthor(d.autor());
        }
        FormatoInstitucional f = d.formato();
        info.setSubject(f.codigo() != null ? f.nombreDelFormato() + " (" + f.codigo() + ")" : f.nombreDelFormato());
        info.setKeywords("SENA; Centro Tecnológico del Mobiliario"
                + (d.numeroContrato() != null ? "; " + d.numeroContrato() : ""));
        info.setCreator("SICOT");
        info.setProducer("SICOT (Apache PDFBox)");
        GregorianCalendar ahora = GregorianCalendar.from(ZonedDateTime.now(reloj));
        info.setCreationDate(ahora);
        info.setModificationDate(ahora);
        if (anclaFirma != null) {
            info.setCustomMetadataValue(PROPIEDAD_ANCLA_FIRMA, anclaFirma);
        }
        pdf.getDocumentCatalog().setLanguage("es-CO");
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Lienzo: la página en curso, la posición vertical, la letra actual.
    // ═════════════════════════════════════════════════════════════════════════

    private final class Lienzo {
        final PDDocument pdf;
        final DocumentoFormal documento;
        final FormatoInstitucional formato;
        final FuentesDelDocumento fuentes;
        final List<PDPage> paginas = new ArrayList<>();
        final float izquierda;
        final float derecha;
        final float anchoUtil;
        final float techo;
        final float piso;
        float tamano;
        float multiplo = 1.15f;
        PDPageContentStream cs;
        float y;
        String anclaFirma;

        Lienzo(PDDocument pdf, DocumentoFormal documento) {
            this.pdf = pdf;
            this.documento = documento;
            this.formato = documento.formato();
            this.fuentes = new FuentesDelDocumento(pdf);
            FormatoInstitucional.Pagina p = formato.pagina();
            this.izquierda = p.margenIzquierdo();
            this.derecha = ANCHO - p.margenDerecho();
            this.anchoUtil = derecha - izquierda;
            this.techo = ALTO - p.inicioCuerpo();
            this.piso = ALTO - p.finCuerpo();
            this.tamano = formato.tamanoTexto();
        }

        // ── Letra ───────────────────────────────────────────────────────────

        PDFont fuente(Estilo estilo) throws IOException {
            return fuentes.fuente(formato.familia(), estilo);
        }

        /**
         * Alto de renglón como lo calcula Word: la altura de la fuente
         * (usWinAscent + usWinDescent: 1,2207 em en Calibri, 1,15 en Arial)
         * por el múltiplo del párrafo. Calibri 10 a 1,15 da 14,04 pt, que es
         * exactamente lo que se mide en el Acta de Inicio.
         */
        float interlinea() {
            float altura = formato.familia() == Familia.CALIBRI ? 1.2207f : 1.15f;
            return tamano * altura * multiplo;
        }

        /** Distancia del borde superior del renglón a la línea base. */
        float ascenso() {
            return tamano * (formato.familia() == Familia.CALIBRI ? 0.945f : 0.905f);
        }

        void letra(BloqueDocumento.Letra letra) {
            tamano = letra.puntos();
            multiplo = letra.multiplo();
        }

        // ── Páginas ─────────────────────────────────────────────────────────

        void primeraPagina() throws IOException {
            nuevaPagina();
            encabezadoInstitucional();
        }

        void nuevaPagina() throws IOException {
            if (cs != null) {
                cs.close();
            }
            PDPage pagina = new PDPage(PDRectangle.LETTER);
            pdf.addPage(pagina);
            paginas.add(pagina);
            cs = new PDPageContentStream(pdf, pagina);
            FormatoInstitucional.Logo logo = formato.logo();
            // El logo va en el encabezado de Word de los GCCON, así que se
            // repite en cada página: una hoja suelta sigue diciendo de dónde es.
            if (logo != null && (logo.todasLasPaginas() || paginas.size() == 1)) {
                PDImageXObject img = fuentes.logo(logo.imagen());
                float x = Float.isNaN(logo.x()) ? (ANCHO - logo.ancho()) / 2 : logo.x();
                cs.drawImage(img, x, ALTO - logo.desdeArriba() - logo.alto(), logo.ancho(), logo.alto());
            }
            y = techo;
        }

        void asegurar(float alto) throws IOException {
            if (y - alto < piso) {
                nuevaPagina();
            }
        }

        void espacio(float puntos) throws IOException {
            if (y - puntos < piso) {
                nuevaPagina();
            } else {
                y -= puntos;
            }
        }

        // ── Encabezado institucional (solo en la primera página) ───────────

        void encabezadoInstitucional() throws IOException {
            switch (formato.encabezado()) {
                case PROCESO_Y_CLASIFICACION -> {
                    // GCCON-F-030: cuatro barras de 27,1 pt y la clasificación
                    // una línea más abajo.
                    float arriba = 85.08f;
                    String[] textos = {"PROCESO", formato.proceso(), "NOMBRE DEL FORMATO", formato.nombreDelFormato()};
                    Color[] fondos = {NEGRO, GRIS_FILA, NEGRO, BLANCO};
                    for (int i = 0; i < 4; i++) {
                        float alto = 27.11f;
                        barra(textos[i], arriba, alto, fondos[i], fondos[i] == NEGRO ? BLANCO : NEGRO,
                                fondos[i] == NEGRO ? NEGRO : GRIS_BORDE, 11f);
                        arriba += alto;
                    }
                    clasificacionEnCeldas(209.54f);
                }
                case CLASIFICACION_EN_CELDAS -> clasificacionEnCeldas(85.22f);
                case CLASIFICACION_CON_CASILLAS -> clasificacionConCasillas();
                case NINGUNO -> {
                    FormatoInstitucional.Logo logo = formato.logo();
                    y = ALTO - (logo != null ? logo.desdeArriba() + logo.alto() : formato.pagina().inicioCuerpo());
                }
                case HOJA_DE_CALCULO -> {
                    // La hoja GIL-F-010 dibuja su propio encabezado.
                }
            }
        }

        /** Una barra a todo el ancho con el texto centrado; «arriba» se mide desde el borde superior. */
        void barra(String texto, float arriba, float alto, Color fondo, Color colorTexto, Color borde, float tam)
                throws IOException {
            float yInferior = ALTO - arriba - alto;
            rectangulo(izquierda, yInferior, anchoUtil, alto, fondo, borde);
            PDFont f = fuente(Estilo.NEGRITA);
            String t = fuentes.escribible(f, texto);
            float w = FuentesDelDocumento.ancho(f, tam, t);
            texto(f, tam, t, izquierda + (anchoUtil - w) / 2, yInferior + alto / 2 - tam * 0.32f, colorTexto);
        }

        /**
         * «CLASIFICACIÓN DE LA INFORMACIÓN» con «Pública | X | Pública
         * Clasificada | | Pública Reservada | » (GCCON-F-030 y GCCON-F-031).
         * Siempre «Pública»: estos documentos se publican en el SECOP II.
         */
        void clasificacionEnCeldas(float arriba) throws IOException {
            barra("CLASIFICACIÓN DE LA INFORMACIÓN", arriba, 24.25f, NEGRO, BLANCO, NEGRO, 11f);
            float top = arriba + 24.25f;
            float alto = 24.6f;
            float[] cortes = {0f, 127.4f, 156.8f, 283.3f, 313.3f, 439.3f};
            String[] textos = {"Pública", "X", "Pública Clasificada", "", "Pública Reservada", ""};
            float yInferior = ALTO - top - alto;
            for (int i = 0; i < textos.length; i++) {
                float x = izquierda + cortes[i];
                float w = (i + 1 < cortes.length ? cortes[i + 1] : anchoUtil) - cortes[i];
                rectangulo(x, yInferior, w, alto, null, GRIS_BORDE);
                boolean marca = i % 2 == 1;
                PDFont f = fuente(marca ? Estilo.NEGRITA : Estilo.NORMAL);
                String t = fuentes.escribible(f, textos[i]);
                float tx = marca ? x + (w - FuentesDelDocumento.ancho(f, 11f, t)) / 2 : x + 5.8f;
                texto(f, 11f, t, tx, yInferior + alto / 2 - 3.6f, NEGRO);
            }
            linea(izquierda, ALTO - top, derecha, ALTO - top, NEGRO);
            y = yInferior;
        }

        /** «Pública [x]  Pública Clasificada [ ]  Pública Reservada [ ]» con casillas (GCCON-F-018). */
        void clasificacionConCasillas() throws IOException {
            float[] anchos = {153.35f, 153.35f, 160.85f};
            float total = anchos[0] + anchos[1] + anchos[2];
            float arriba = 92.66f;
            float x0 = 70.82f;
            float yBarra = ALTO - arriba - 24.96f;
            rectangulo(x0, yBarra, total, 24.96f, NEGRO, NEGRO);
            PDFont negrita = fuente(Estilo.NEGRITA);
            String titulo = fuentes.escribible(negrita, "CLASIFICACIÓN DE LA INFORMACIÓN");
            texto(negrita, 12f, titulo, x0 + (total - FuentesDelDocumento.ancho(negrita, 12f, titulo)) / 2,
                    ALTO - 109.22f, BLANCO);
            float yFila = ALTO - 141.89f;
            String[] textos = {"Pública", "Pública Clasificada", "Pública Reservada"};
            float[] casillas = {185.80f, 346.10f, 494.60f};
            PDFont normal = fuente(Estilo.NORMAL);
            float x = x0;
            for (int i = 0; i < 3; i++) {
                rectangulo(x, yFila, anchos[i], 141.89f - 117.62f, null, GRIS_BORDE);
                texto(normal, 12f, fuentes.escribible(normal, textos[i]), x + 5.76f, ALTO - 130.13f, NEGRO);
                rectangulo(casillas[i], ALTO - 119.25f - 19.8f, 20.4f, 19.8f, BLANCO, NEGRO);
                if (i == 0) {
                    float xw = FuentesDelDocumento.ancho(negrita, 12f, "x");
                    texto(negrita, 12f, "x", casillas[i] + (20.4f - xw) / 2, ALTO - 134.69f, NEGRO);
                }
                x += anchos[i];
            }
            linea(x0, ALTO - 117.62f, x0 + total, ALTO - 117.62f, NEGRO);
            y = yFila;
        }

        // ── Bloques de texto ────────────────────────────────────────────────

        void titulo(BloqueDocumento.Titulo t) throws IOException {
            asegurar(interlinea() * (t.lineas().size() + 2));
            for (Tramo linea : t.lineas()) {
                for (Linea l : envolver(trocear(List.of(linea)), anchoUtil)) {
                    asegurar(interlinea());
                    dibujarLinea(l, izquierda, y - ascenso(), anchoUtil, Alineacion.CENTRO, NEGRO);
                    y -= interlinea();
                }
            }
        }

        void seccion(BloqueDocumento.Seccion s) throws IOException {
            // Un título solo al pie de una página, separado de su contenido, no
            // se lee: se exige sitio para el título y tres renglones más.
            asegurar(interlinea() * 4);
            PDFont negrita = fuente(Estilo.NEGRITA);
            float x = izquierda;
            float ancho = anchoUtil;
            if (s.numero() != null) {
                texto(negrita, tamano, fuentes.escribible(negrita, s.numero()), izquierda, y - ascenso(), NEGRO);
                x += SANGRIA_SECCION;
                ancho -= SANGRIA_SECCION;
            }
            for (Linea l : envolver(trocear(List.of(Tramo.negrita(s.titulo()))), ancho)) {
                dibujarLinea(l, x, y - ascenso(), ancho, Alineacion.IZQUIERDA, NEGRO);
                y -= interlinea();
            }
        }

        void parrafo(BloqueDocumento.Parrafo p) throws IOException {
            float x = izquierda + p.sangria();
            float ancho = anchoUtil - p.sangria();
            for (Linea l : envolver(trocear(p.tramos()), ancho)) {
                asegurar(interlinea());
                dibujarLinea(l, x, y - ascenso(), ancho, p.alineacion(), NEGRO);
                y -= interlinea();
            }
        }

        void notaPequena(String texto) throws IOException {
            float anterior = tamano;
            float multAnterior = multiplo;
            tamano = 6f;
            multiplo = 1.15f;
            for (Linea l : envolver(trocear(List.of(Tramo.normal(texto))), anchoUtil)) {
                asegurar(interlinea());
                dibujarLinea(l, izquierda, y - ascenso(), anchoUtil, Alineacion.IZQUIERDA, NEGRO);
                y -= interlinea();
            }
            tamano = anterior;
            multiplo = multAnterior;
        }

        // ── Ficha: tabla etiqueta-valor ─────────────────────────────────────

        void ficha(BloqueDocumento.Ficha ficha) throws IOException {
            float anchoEtiqueta = anchoUtil * ficha.anchoEtiqueta();
            float anchoValor = anchoUtil - anchoEtiqueta;
            float r = ficha.relleno();
            for (BloqueDocumento.Campo campo : ficha.campos()) {
                String valor = campo.valor() == null || campo.valor().isBlank() ? "[dato pendiente]" : campo.valor();
                List<CeldaLista> celdas = List.of(
                        new CeldaLista(izquierda, anchoEtiqueta,
                                envolver(trocear(List.of(Tramo.negrita(campo.etiqueta()))), anchoEtiqueta - 2 * r),
                                Alineacion.IZQUIERDA, ficha.centrarVertical(), null, r),
                        new CeldaLista(izquierda + anchoEtiqueta, anchoValor,
                                envolver(trocear(List.of(new Tramo(valor, campo.estiloValor()))), anchoValor - 2 * r),
                                Alineacion.JUSTIFICADO, ficha.centrarVertical(), null, r));
                filaSencilla(celdas);
            }
        }

        // ── Tabla con celdas combinadas ─────────────────────────────────────

        void tabla(BloqueDocumento.Tabla tabla) throws IOException {
            int n = tabla.anchos().size();
            float total = 0;
            for (float w : tabla.anchos()) {
                total += w;
            }
            float x0 = tabla.centradaEnLaHoja() ? (ANCHO - total) / 2 : izquierda;
            float[] xs = new float[n + 1];
            xs[0] = x0;
            for (int i = 0; i < n; i++) {
                xs[i + 1] = xs[i] + tabla.anchos().get(i);
            }

            // Colocar cada celda en su columna, saltando las que ocupa una
            // celda que baja desde una fila anterior.
            int filas = tabla.filas().size();
            boolean[][] ocupada = new boolean[filas + 8][n];
            List<List<CeldaColocada>> porFila = new ArrayList<>();
            for (int f = 0; f < filas; f++) {
                List<CeldaColocada> colocadas = new ArrayList<>();
                int c = 0;
                for (BloqueDocumento.Celda celda : tabla.filas().get(f).celdas()) {
                    while (c < n && ocupada[f][c]) {
                        c++;
                    }
                    if (c >= n) {
                        break;
                    }
                    int abarca = Math.min(celda.columnas(), n - c);
                    int baja = Math.min(Math.max(celda.filas(), 1), filas - f);
                    for (int ff = f; ff < f + baja; ff++) {
                        for (int cc = c; cc < c + abarca; cc++) {
                            ocupada[ff][cc] = true;
                        }
                    }
                    float x = xs[c];
                    float w = xs[c + abarca] - x;
                    List<Linea> lineas = envolver(trocear(celda.tramos()), w - 2 * tabla.relleno());
                    colocadas.add(new CeldaColocada(f, baja, new CeldaLista(x, w, lineas, celda.alineacion(),
                            celda.centrarVertical(), celda.gris() ? GRIS_ENCABEZADO : null, tabla.relleno())));
                    c += abarca;
                }
                porFila.add(colocadas);
            }

            // Alto de cada fila: el de su celda más alta; una celda que abarca
            // varias filas estira la última si no le alcanza.
            float[] alto = new float[filas];
            for (int f = 0; f < filas; f++) {
                float h = interlinea() + BORDE;
                for (CeldaColocada cc : porFila.get(f)) {
                    if (cc.filas == 1) {
                        h = Math.max(h, altoDe(cc.celda.lineas.size()));
                    }
                }
                alto[f] = h;
            }
            int[] finDeGrupo = new int[filas];
            for (int f = 0; f < filas; f++) {
                finDeGrupo[f] = f;
            }
            for (int f = 0; f < filas; f++) {
                for (CeldaColocada cc : porFila.get(f)) {
                    if (cc.filas > 1) {
                        int ultima = f + cc.filas - 1;
                        float tiene = 0;
                        for (int ff = f; ff <= ultima; ff++) {
                            tiene += alto[ff];
                        }
                        float necesita = altoDe(cc.celda.lineas.size());
                        if (necesita > tiene) {
                            alto[ultima] += necesita - tiene;
                        }
                        for (int ff = f; ff <= ultima; ff++) {
                            finDeGrupo[ff] = Math.max(finDeGrupo[ff], ultima);
                        }
                    }
                }
            }

            // Grupos de filas que no se pueden separar (unidas por celdas
            // combinadas en vertical); las primeras son el encabezado.
            List<int[]> grupos = new ArrayList<>();
            int f = 0;
            while (f < filas) {
                int fin = finDeGrupo[f];
                for (int ff = f; ff <= fin; ff++) {
                    fin = Math.max(fin, finDeGrupo[ff]);
                }
                grupos.add(new int[]{f, fin});
                f = fin + 1;
            }
            int encabezado = Math.min(tabla.filasDeEncabezado(), filas);
            float altoEncabezado = 0;
            for (int i = 0; i < encabezado; i++) {
                altoEncabezado += alto[i];
            }
            // El encabezado y la primera fila de datos van juntos.
            float primera = grupos.isEmpty() ? 0 : altoDeGrupo(alto, grupos.getFirst());
            asegurar(Math.min(altoEncabezado + primera + (encabezado > 0 && grupos.size() > 1
                    ? altoDeGrupo(alto, grupos.get(Math.min(1, grupos.size() - 1))) : 0), techo - piso));

            for (int[] g : grupos) {
                float h = altoDeGrupo(alto, g);
                boolean esEncabezado = g[1] < encabezado;
                if (y - h < piso) {
                    if (h <= techo - piso - altoEncabezado || g[0] != g[1]) {
                        nuevaPagina();
                        if (!esEncabezado && encabezado > 0) {
                            dibujarFilas(porFila, alto, 0, encabezado - 1);
                        }
                    } else {
                        // Una sola fila más alta que lo que queda: se parte.
                        List<CeldaLista> celdas = porFila.get(g[0]).stream().map(cc -> cc.celda).toList();
                        filaSencilla(celdas);
                        continue;
                    }
                }
                dibujarFilas(porFila, alto, g[0], g[1]);
            }
        }

        float altoDeGrupo(float[] alto, int[] g) {
            float h = 0;
            for (int i = g[0]; i <= g[1]; i++) {
                h += alto[i];
            }
            return h;
        }

        void dibujarFilas(List<List<CeldaColocada>> porFila, float[] alto, int desde, int hasta) throws IOException {
            float[] arriba = new float[hasta - desde + 2];
            arriba[0] = y;
            for (int f = desde; f <= hasta; f++) {
                arriba[f - desde + 1] = arriba[f - desde] - alto[f];
            }
            for (int f = desde; f <= hasta; f++) {
                for (CeldaColocada cc : porFila.get(f)) {
                    int ultima = Math.min(f + cc.filas - 1, hasta);
                    float top = arriba[f - desde];
                    float bottom = arriba[ultima - desde + 1];
                    dibujarCelda(cc.celda, top, top - bottom, cc.celda.lineas);
                }
            }
            y = arriba[hasta - desde + 1];
        }

        float altoDe(int renglones) {
            return Math.max(renglones, 1) * interlinea() + BORDE;
        }

        void dibujarCelda(CeldaLista c, float top, float alto, List<Linea> lineas) throws IOException {
            rectangulo(c.x, top - alto, c.ancho, alto, c.fondo, NEGRO);
            float usado = lineas.size() * interlinea();
            float desplazamiento = c.centrarVertical ? Math.max(0, (alto - BORDE - usado) / 2) : 0;
            float yy = top - BORDE - desplazamiento;
            for (Linea l : lineas) {
                dibujarLinea(l, c.x + c.relleno, yy - ascenso(), c.ancho - 2 * c.relleno, c.alineacion, NEGRO);
                yy -= interlinea();
            }
        }

        /**
         * Una fila sin celdas combinadas en vertical. Si no cabe en lo que queda
         * de página pasa entera a la siguiente; si ni en una página vacía cabe
         * (un objeto contractual de media página, unas observaciones largas),
         * se parte por renglones y sigue en la otra. Nunca se recorta texto.
         */
        void filaSencilla(List<CeldaLista> celdas) throws IOException {
            int maximo = celdas.stream().mapToInt(c -> c.lineas.size()).max().orElse(1);
            float alto = altoDe(maximo);
            if (y - alto < piso && alto <= techo - piso) {
                nuevaPagina();
            }
            List<CeldaLista> pendientes = celdas;
            while (true) {
                int caben = (int) Math.floor((y - piso - BORDE) / interlinea());
                int quedan = pendientes.stream().mapToInt(c -> c.lineas.size()).max().orElse(1);
                if (caben < 1) {
                    nuevaPagina();
                    continue;
                }
                int ahora = Math.min(caben, Math.max(quedan, 1));
                float altoParte = altoDe(ahora);
                List<CeldaLista> resto = new ArrayList<>();
                for (CeldaLista c : pendientes) {
                    int k = Math.min(ahora, c.lineas.size());
                    dibujarCelda(c, y, altoParte, c.lineas.subList(0, k));
                    resto.add(c.conLineas(c.lineas.subList(k, c.lineas.size())));
                }
                y -= altoParte;
                if (quedan <= ahora) {
                    return;
                }
                pendientes = resto;
                nuevaPagina();
            }
        }

        // ── Firmas ──────────────────────────────────────────────────────────

        void firmas(BloqueDocumento.Firmas firmas) throws IOException {
            List<BloqueDocumento.Firmante> firmantes = firmas.firmantes();
            if (firmantes.isEmpty()) {
                return;
            }
            int renglones = firmantes.stream().mapToInt(f -> f.lineas().size() + 1).max().orElse(1);
            float espacio = firmas.espacioParaFirmar();
            // El bloque entero en la misma página que su hueco de firma.
            asegurar(espacio + renglones * interlinea() + BORDE);
            float yNombres = y - espacio;
            switch (firmas.disposicion()) {
                case CENTRADA -> {
                    BloqueDocumento.Firmante f = firmantes.getFirst();
                    anclar(ANCHO / 2 - 90f, yNombres, 180f, espacio);
                    float yy = yNombres;
                    List<Tramo> renglonesFirma = new ArrayList<>();
                    renglonesFirma.add(new Tramo(f.nombre(), f.nombreEnNegrita() ? Estilo.NEGRITA : Estilo.NORMAL));
                    f.lineas().forEach(t -> renglonesFirma.add(new Tramo(t,
                            f.nombreEnNegrita() ? Estilo.NEGRITA : Estilo.NORMAL)));
                    for (Tramo t : renglonesFirma) {
                        for (Linea l : envolver(trocear(List.of(t)), ANCHO - 2 * izquierda)) {
                            dibujarLinea(l, 0, yy - ascenso(), ANCHO, Alineacion.CENTRO, NEGRO);
                            yy -= interlinea();
                        }
                    }
                    y = yy;
                }
                case IZQUIERDA, COLUMNAS, TABLA -> {
                    int columnas = firmas.disposicion() == BloqueDocumento.DisposicionFirmas.IZQUIERDA
                            ? 1 : Math.max(2, firmantes.size());
                    float anchoColumna = anchoUtil / columnas;
                    float altoTabla = renglones * interlinea() + BORDE;
                    float yy0 = yNombres;
                    for (int i = 0; i < Math.min(firmantes.size(), columnas); i++) {
                        BloqueDocumento.Firmante f = firmantes.get(i);
                        float x = izquierda + i * anchoColumna;
                        if (i == 0) {
                            anclar(x + 5.4f, yNombres, Math.min(anchoColumna - 10.8f, 200f), espacio);
                        }
                        if (firmas.disposicion() == BloqueDocumento.DisposicionFirmas.TABLA) {
                            rectangulo(x, yNombres - altoTabla, anchoColumna, altoTabla, null, NEGRO);
                        }
                        float yy = yNombres - (firmas.disposicion() == BloqueDocumento.DisposicionFirmas.TABLA ? BORDE : 0);
                        List<Tramo> renglonesFirma = new ArrayList<>();
                        renglonesFirma.add(new Tramo(f.nombre(), f.nombreEnNegrita() ? Estilo.NEGRITA : Estilo.NORMAL));
                        f.lineas().forEach(t -> renglonesFirma.add(Tramo.normal(t)));
                        for (Tramo t : renglonesFirma) {
                            for (Linea l : envolver(trocear(List.of(t)), anchoColumna - 10.8f)) {
                                dibujarLinea(l, x + 5.4f, yy - ascenso(), anchoColumna - 10.8f,
                                        Alineacion.IZQUIERDA, NEGRO);
                                yy -= interlinea();
                            }
                        }
                        yy0 = Math.min(yy0, yy);
                    }
                    y = firmas.disposicion() == BloqueDocumento.DisposicionFirmas.TABLA
                            ? Math.min(yNombres - altoTabla, yy0) : yy0;
                }
            }
        }

        /** Guarda el hueco de la firma del supervisor (la primera que se dibuja). */
        void anclar(float x, float yNombres, float ancho, float espacio) {
            if (anclaFirma == null) {
                anclaFirma = String.format(Locale.ROOT, "%d;%.2f;%.2f;%.2f;%.2f",
                        paginas.size() - 1, x, yNombres + 2f, ancho, Math.max(espacio - 6f, 20f));
            }
        }

        // ── La hoja GIL-F-010 ───────────────────────────────────────────────

        void hojaDeRecibo(BloqueDocumento.HojaDeRecibo hoja) throws IOException {
            HojaActaDeRecibo.Resultado r = HojaActaDeRecibo.dibujar(cs, fuentes, hoja.datos());
            float[] ancla = r.ancla();
            anclaFirma = String.format(Locale.ROOT, "%d;%.2f;%.2f;%.2f;%.2f",
                    paginas.size() - 1, ancla[0], ancla[1], ancla[2], ancla[3]);
            y = piso;
            if (r.continuaciones().isEmpty()) {
                return;
            }
            // Hoja de continuación: el texto que no cupo en las casillas fijas,
            // completo, con la referencia al acta a la que pertenece.
            nuevaPagina();
            y = ALTO - 60f;
            tamano = 9f;
            multiplo = 1.15f;
            parrafo(new BloqueDocumento.Parrafo(List.of(Tramo.negrita("FORMATO ACTA DE RECIBO A SATISFACCIÓN DE BIENES"
                    + " (GIL-F-010) — Continuación, Acta N° " + hoja.datos().actaNumero() + ", contrato "
                    + hoja.datos().numeroActoAdministrativo())), Alineacion.IZQUIERDA, 0));
            espacio(interlinea());
            for (String[] c : r.continuaciones()) {
                parrafo(new BloqueDocumento.Parrafo(List.of(Tramo.negrita(c[0])), Alineacion.IZQUIERDA, 0));
                parrafo(new BloqueDocumento.Parrafo(c[1]));
                espacio(interlinea());
            }
        }

        // ── Primitivas ──────────────────────────────────────────────────────

        void texto(PDFont f, float tam, String t, float x, float base, Color color) throws IOException {
            if (t.isEmpty()) {
                return;
            }
            cs.beginText();
            cs.setNonStrokingColor(color);
            cs.setFont(f, tam);
            cs.newLineAtOffset(x, base);
            cs.showText(t);
            cs.endText();
        }

        void rectangulo(float x, float yy, float w, float h, Color fondo, Color borde) throws IOException {
            if (fondo != null) {
                cs.setNonStrokingColor(fondo);
                cs.addRect(x, yy, w, h);
                cs.fill();
            }
            if (borde != null) {
                cs.setStrokingColor(borde);
                cs.setLineWidth(BORDE);
                cs.addRect(x, yy, w, h);
                cs.stroke();
            }
        }

        void linea(float x1, float y1, float x2, float y2, Color color) throws IOException {
            cs.setStrokingColor(color);
            cs.setLineWidth(BORDE);
            cs.moveTo(x1, y1);
            cs.lineTo(x2, y2);
            cs.stroke();
        }

        /**
         * Parte el texto en palabras con su estilo. Una palabra dentro de
         * «[dato pendiente…]» se marca para pintarla en rojo, también si el
         * marcador queda repartido en dos renglones.
         */
        List<Pieza> trocear(List<Tramo> tramos) throws IOException {
            List<Pieza> piezas = new ArrayList<>();
            boolean espacio = false;
            boolean dentroDePendiente = false;
            for (Tramo tramo : tramos) {
                String texto = tramo.texto() == null ? "" : Normalizer.normalize(tramo.texto(), Normalizer.Form.NFC);
                PDFont f = fuente(tramo.estilo());
                StringBuilder palabra = new StringBuilder();
                boolean espacioAntes = espacio;
                for (int i = 0; i <= texto.length(); i++) {
                    boolean fin = i == texto.length();
                    char c = fin ? ' ' : texto.charAt(i);
                    boolean blanco = c == ' ' || c == '\t' || c == '\r' || c == ' ' || c == '\n';
                    if (!blanco) {
                        if (palabra.isEmpty()) {
                            espacioAntes = espacio;
                        }
                        palabra.append(c);
                        espacio = false;
                        continue;
                    }
                    if (!palabra.isEmpty()) {
                        String p = fuentes.escribible(f, palabra.toString());
                        int abre = p.indexOf("[dato");
                        boolean pendiente = dentroDePendiente || abre >= 0;
                        if (abre >= 0) {
                            dentroDePendiente = p.indexOf(']', abre) < 0;
                        } else if (dentroDePendiente && p.indexOf(']') >= 0) {
                            dentroDePendiente = false;
                        }
                        piezas.add(new Pieza(p, f, pendiente, espacioAntes, false));
                        palabra.setLength(0);
                    }
                    if (fin) {
                        break;
                    }
                    if (c == '\n') {
                        piezas.add(new Pieza("", f, false, false, true));
                        espacio = false;
                    } else {
                        espacio = true;
                    }
                }
                // El espacio al final de un tramo cuenta para la palabra del siguiente.
                espacio = !texto.isEmpty() && Character.isWhitespace(texto.charAt(texto.length() - 1));
            }
            return piezas;
        }

        float ancho(Pieza p) throws IOException {
            return FuentesDelDocumento.ancho(p.fuente, tamano, p.texto);
        }

        float anchoEspacio(Pieza p) throws IOException {
            return FuentesDelDocumento.ancho(p.fuente, tamano, " ");
        }

        List<Linea> envolver(List<Pieza> piezas, float disponible) throws IOException {
            List<Linea> lineas = new ArrayList<>();
            List<Pieza> actual = new ArrayList<>();
            float anchoActual = 0;
            for (Pieza pieza : piezas) {
                Pieza p = pieza;
                if (p.salto) {
                    lineas.add(new Linea(actual, true));
                    actual = new ArrayList<>();
                    anchoActual = 0;
                    continue;
                }
                float w = ancho(p);
                float sep = !actual.isEmpty() && p.espacioAntes ? anchoEspacio(p) : 0;
                if (!actual.isEmpty() && anchoActual + sep + w > disponible) {
                    lineas.add(new Linea(actual, false));
                    actual = new ArrayList<>();
                    anchoActual = 0;
                    sep = 0;
                }
                if (actual.isEmpty() && w > disponible) {
                    // Una palabra que no cabe sola en el renglón (un enlace del
                    // SECOP, un código largo) se parte donde haga falta: sin esto
                    // la cola quedaba fuera de la hoja del documento firmado.
                    List<Pieza> trozos = partir(p, disponible);
                    for (int i = 0; i < trozos.size() - 1; i++) {
                        lineas.add(new Linea(List.of(trozos.get(i)), false));
                    }
                    p = trozos.getLast();
                    w = ancho(p);
                }
                actual.add(p);
                anchoActual += sep + w;
            }
            lineas.add(new Linea(actual, true));
            return lineas;
        }

        List<Pieza> partir(Pieza p, float disponible) throws IOException {
            List<Pieza> trozos = new ArrayList<>();
            StringBuilder trozo = new StringBuilder();
            int i = 0;
            while (i < p.texto.length()) {
                int cp = p.texto.codePointAt(i);
                String siguiente = trozo + new String(Character.toChars(cp));
                if (FuentesDelDocumento.ancho(p.fuente, tamano, siguiente) > disponible && !trozo.isEmpty()) {
                    trozos.add(new Pieza(trozo.toString(), p.fuente, p.pendiente, trozos.isEmpty() && p.espacioAntes,
                            false));
                    trozo.setLength(0);
                } else {
                    trozo.appendCodePoint(cp);
                    i += Character.charCount(cp);
                }
            }
            trozos.add(new Pieza(trozo.toString(), p.fuente, p.pendiente, trozos.isEmpty() && p.espacioAntes, false));
            return trozos;
        }

        /**
         * Un renglón. Justificado se reparte el sobrante entre los espacios,
         * palabra por palabra: con fuentes incrustadas de dos bytes el
         * operador de espaciado entre palabras del PDF no aplica.
         */
        void dibujarLinea(Linea linea, float x, float base, float disponible, Alineacion alineacion, Color colorBase)
                throws IOException {
            if (linea.piezas.isEmpty()) {
                return;
            }
            float natural = 0;
            int huecos = 0;
            for (int i = 0; i < linea.piezas.size(); i++) {
                Pieza p = linea.piezas.get(i);
                if (i > 0 && p.espacioAntes) {
                    natural += anchoEspacio(p);
                    huecos++;
                }
                natural += ancho(p);
            }
            float xx = switch (alineacion) {
                case IZQUIERDA, JUSTIFICADO -> x;
                case CENTRO -> x + (disponible - natural) / 2;
                case DERECHA -> x + disponible - natural;
            };
            float extra = alineacion == Alineacion.JUSTIFICADO && !linea.ultima && huecos > 0
                    ? Math.max(0, (disponible - natural) / huecos) : 0;
            for (int i = 0; i < linea.piezas.size(); i++) {
                Pieza p = linea.piezas.get(i);
                if (i > 0 && p.espacioAntes) {
                    xx += anchoEspacio(p) + extra;
                }
                // El espacio se escribe de verdad detrás de la palabra, aunque la
                // siguiente se coloque a mano: sin él, un lector de la capa de
                // texto (pypdf) juntaba las palabras de un renglón justificado.
                boolean siguienteConEspacio = i + 1 < linea.piezas.size() && linea.piezas.get(i + 1).espacioAntes;
                texto(p.fuente, tamano, siguienteConEspacio ? p.texto + " " : p.texto, xx, base,
                        p.pendiente ? ROJO_PENDIENTE : colorBase);
                xx += ancho(p);
            }
        }

        // ── Pie de página ───────────────────────────────────────────────────

        /**
         * Segunda pasada: número de página, código y versión, y la línea de
         * trazabilidad en cada página ya generada.
         */
        void pies() throws IOException {
            FormatoInstitucional.EstiloPie estilo = formato.estiloPie();
            PDFont traza = fuentes.fuente(Familia.CALIBRI, Estilo.NORMAL);
            String lineaTraza = fuentes.escribible(traza, trazabilidad());
            for (int i = 0; i < paginas.size(); i++) {
                try (PDPageContentStream p = new PDPageContentStream(pdf, paginas.get(i),
                        PDPageContentStream.AppendMode.APPEND, true, true)) {
                    PDPageContentStream anterior = cs;
                    cs = p;
                    if (estilo != null) {
                        PDFont fn = fuentes.fuente(estilo.familiaNumero(), Estilo.NORMAL);
                        String numero = String.valueOf(i + 1);
                        texto(fn, estilo.tamanoNumero(), numero,
                                derecha - FuentesDelDocumento.ancho(fn, estilo.tamanoNumero(), numero),
                                estilo.baseNumero(), NEGRO);
                        if (formato.pie() != null) {
                            PDFont fc = fuentes.fuente(estilo.familiaCodigo(), Estilo.NORMAL);
                            String t = fuentes.escribible(fc, formato.pie());
                            texto(fc, estilo.tamanoCodigo(), t,
                                    (ANCHO - FuentesDelDocumento.ancho(fc, estilo.tamanoCodigo(), t)) / 2,
                                    estilo.baseCodigo(), NEGRO);
                        }
                    }
                    float base = estilo != null ? estilo.baseNumero() : 30f;
                    texto(traza, 6.5f, lineaTraza, formato.encabezado() == FormatoInstitucional.Encabezado.HOJA_DE_CALCULO
                            ? HojaActaDeRecibo.IZQUIERDA : izquierda, base, GRIS_TRAZA);
                    cs = anterior;
                }
            }
        }

        String trazabilidad() {
            return (documento.origen() != null ? documento.origen() : "Generado en SICOT")
                    + " el " + LocalDate.now(reloj).format(FECHA);
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Piezas del texto y de las tablas
    // ═════════════════════════════════════════════════════════════════════════

    /** Una palabra ya escribible, con su fuente. */
    private record Pieza(String texto, PDFont fuente, boolean pendiente, boolean espacioAntes, boolean salto) {
    }

    /** Un renglón; el último de un párrafo no se justifica. */
    private record Linea(List<Pieza> piezas, boolean ultima) {
    }

    /** Una celda con su texto ya repartido en renglones. */
    private record CeldaLista(float x, float ancho, List<Linea> lineas, Alineacion alineacion,
                              boolean centrarVertical, Color fondo, float relleno) {
        CeldaLista conLineas(List<Linea> otras) {
            return new CeldaLista(x, ancho, otras, alineacion, centrarVertical, fondo, relleno);
        }
    }

    /** Una celda de tabla ya colocada: en qué fila empieza y cuántas abarca. */
    private record CeldaColocada(int fila, int filas, CeldaLista celda) {
    }
}
