package co.sena.sicot.ia;

import co.sena.sicot.ia.BloqueDocumento.DatosActaDeRecibo;
import co.sena.sicot.ia.BloqueDocumento.Estilo;
import co.sena.sicot.ia.FormatoInstitucional.Familia;
import co.sena.sicot.ia.FormatoInstitucional.Imagen;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDFont;

import java.awt.Color;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * El Acta de Recibo a Satisfacción de Bienes GIL-F-010, dibujada casilla por
 * casilla como la hoja de Excel del formato.
 *
 * <h2>Por qué es una hoja fija y no un documento que fluye</h2>
 * El GIL-F-010 no es un documento de Word: es una hoja de cálculo con cada
 * casilla en su sitio, pares ETIQUETA / valor sobre renglones subrayados, una
 * banda negra «RECIBIDO A SATISFACCION» y un recuadro para la firma. El objeto
 * del contrato tiene siete renglones, ni uno más. Un documento que crece con
 * su contenido no se parecería al formato; aquí, como en Excel, el texto que no
 * cabe se reduce de letra (hasta 4,5 pt) antes de salirse de su casilla.
 *
 * <h2>De dónde salen los números</h2>
 * Las coordenadas son las medidas en la impresión real del formato (Excel la
 * imprime al 55 %: {@code pageSetup scale=55}), en puntos desde la esquina
 * superior izquierda. Se dibujan al 60 % y centradas en la hoja: la misma
 * geometría, con la letra un poco más legible (6 pt en lugar de 5,5).
 */
final class HojaActaDeRecibo {

    /** Escala respecto de la impresión medida (60 % / 55 %). */
    private static final float K = 0.60f / 0.55f;
    private static final float ANCHO_REAL = 493.57f - 50.36f;
    private static final float ALTO_REAL = 712.63f - 53.96f;
    /** Borde izquierdo del recuadro en la hoja. */
    static final float IZQUIERDA = (PdfInstitucional.ANCHO - ANCHO_REAL * K) / 2;
    private static final float ARRIBA = (PdfInstitucional.ALTO - ALTO_REAL * K) / 2;

    // Columnas de la hoja (impresión al 55 %).
    private static final float B = 50.36f, C = 142.61f, D = 211.80f, E = 260.87f, F = 299.42f, G = 340.51f,
            H = 387.47f, I = 424.78f, J = 459.17f, FIN = 493.57f;
    private static final float[] FILA = new float[46];

    static {
        FILA[2] = 53.96f;
        FILA[3] = 73.76f;
        FILA[4] = 90.25f;
        FILA[5] = 106.76f;
        FILA[6] = 123.24f;
        FILA[7] = 139.75f;
        FILA[8] = 156.23f;
        FILA[9] = 172.73f;
        FILA[10] = 189.22f;
        FILA[11] = 197.47f;
        FILA[12] = 205.72f;
        for (int r = 13; r <= 30; r++) {
            FILA[r] = 205.72f + (r - 12) * 15.245f;
        }
        FILA[31] = 497.49f;
        FILA[32] = 509.03f;
        FILA[33] = 544.08f;
        FILA[34] = 559.33f;
        FILA[35] = 574.57f;
        FILA[36] = 589.82f;
        FILA[37] = 605.07f;
        FILA[38] = 620.32f;
        FILA[39] = 629.39f;
        for (int r = 40; r <= 44; r++) {
            FILA[r] = 629.39f + (r - 39) * 15.248f;
        }
        FILA[45] = 712.63f;
    }

    private static final float MEDIA = 0.9626f;
    private static final float DELGADA = 0.4125f;
    private static final float SANGRIA = 0.56f;
    private static final float SOBRE_LA_LINEA = 1.74f;
    private static final float LETRA_MINIMA = 4.5f;
    private static final Color NEGRO = Color.BLACK;
    private static final Color BLANCO = Color.WHITE;
    private static final String AVISO = "(continúa en la hoja siguiente)";
    /** El aviso para las casillas donde el largo no cabe junto al texto. */
    private static final String AVISO_CORTO = "(sigue en la hoja 2)";

    private final PDPageContentStream cs;
    private final FuentesDelDocumento fuentes;
    private final PDFont arialNegrita;
    private final PDFont carlito;
    private final PDFont carlitoNegrita;

    private HojaActaDeRecibo(PDPageContentStream cs, FuentesDelDocumento fuentes) throws IOException {
        this.cs = cs;
        this.fuentes = fuentes;
        this.arialNegrita = fuentes.fuente(Familia.ARIAL, Estilo.NEGRITA);
        this.carlito = fuentes.fuente(Familia.CALIBRI, Estilo.NORMAL);
        this.carlitoNegrita = fuentes.fuente(Familia.CALIBRI, Estilo.NEGRITA);
    }

    /**
     * Lo que queda de dibujar la hoja.
     *
     * @param ancla          el hueco de la firma del supervisor en puntos PDF: {x, y, ancho, alto}.
     * @param continuaciones casillas cuyo texto no cupo en sus renglones fijos:
     *                       {etiqueta, texto completo}, para una hoja de continuación.
     */
    record Resultado(float[] ancla, List<String[]> continuaciones) {
    }

    /** Dibuja la hoja completa en la página del flujo de contenido dado. */
    static Resultado dibujar(PDPageContentStream cs, FuentesDelDocumento fuentes, DatosActaDeRecibo d)
            throws IOException {
        HojaActaDeRecibo hoja = new HojaActaDeRecibo(cs, fuentes);
        float[] ancla = hoja.dibujar(d);
        return new Resultado(ancla, hoja.continuaciones);
    }

    /**
     * El texto que no cabe en su casilla, ni a la letra mínima, no se pierde:
     * se escribe completo en una hoja de continuación y la casilla lo avisa.
     * Hasta el 28-09-2026 se cortaba con «[…]» y el acta firmada perdía, por
     * ejemplo, la mitad de la relación de bienes recibidos.
     */
    private final List<String[]> continuaciones = new ArrayList<>();

    private float[] dibujar(DatosActaDeRecibo d) throws IOException {
        // ── Recuadro y encabezado ───────────────────────────────────────────
        linea(FILA[2], B - 0.49f, FIN + 0.48f, MEDIA);
        vertical(B, FILA[2] - 0.48f, FILA[34], MEDIA);
        vertical(FIN, FILA[2] - 0.48f, FILA[34], MEDIA);
        vertical(I, FILA[2] - 0.48f, FILA[4], DELGADA);
        linea(FILA[3], I, FIN, DELGADA);
        linea(FILA[4], B, FIN, MEDIA);
        var logo = fuentes.logo(Imagen.VERDE);
        float altoLogo = 30.20f;
        float anchoLogo = 32.57f;
        cs.drawImage(logo, x(275.55f - anchoLogo / 2), y(57.26f + altoLogo), anchoLogo * K, altoLogo * K);
        texto("Versión: 08", I, 65.78f, arialNegrita, 5.5f, NEGRO, Alinear.CENTRO, FIN);
        texto("Código: GIL-F-010", I + SANGRIA, 83.92f, arialNegrita, 5.5f, NEGRO, Alinear.IZQUIERDA, 0);
        String[][] barras = {
                {"4", "PROCESO", "n"}, {"5", "GESTIÓN DE INFRAESTRUCTURA Y LOGÍSTICA", "b"},
                {"6", "NOMBRE DEL FORMATO", "n"}, {"7", "FORMATO ACTA DE RECIBO A SATISFACCIÓN DE BIENES", "b"},
                {"8", "CLASIFICACIÓN DE LA INFORMACIÓN", "n"}};
        for (String[] barra : barras) {
            int fila = Integer.parseInt(barra[0]);
            boolean negra = barra[2].equals("n");
            if (negra) {
                relleno(B, FILA[fila], FIN, FILA[fila + 1], NEGRO);
            }
            texto(barra[1], B, FILA[fila] + 10.21f, carlitoNegrita, 7.7f, negra ? BLANCO : NEGRO, Alinear.CENTRO, FIN);
        }
        linea(FILA[9], B, FIN, DELGADA);
        texto("Pública:", B, 183.03f, arialNegrita, 6.05f, NEGRO, Alinear.CENTRO, C);
        texto("Pública Clasificada:", D, 183.03f, arialNegrita, 6.05f, NEGRO, Alinear.CENTRO, F);
        texto("Pública Reservada:", G + SANGRIA, 183.03f, arialNegrita, 6.05f, NEGRO, Alinear.IZQUIERDA, 0);
        casilla(114.73f, 175.66f, 138.65f, 185.95f);
        casilla(288.08f, 176.48f, 317.16f, 186.37f);
        casilla(416.83f, 175.64f, 446.72f, 186.76f);
        texto("x", 118.63f, 182.14f, carlito, 6.05f, NEGRO, Alinear.IZQUIERDA, 0);
        linea(FILA[10], B, FIN, MEDIA);

        texto("Acta N°", F, 203.91f, arialNegrita, 6.6f, NEGRO, Alinear.CENTRO, G);
        // Con escribirValor y no con texto(): así se parte, reduce la letra o
        // pasa a la hoja de continuación si es largo, y el «[dato pendiente]»
        // sale en rojo como los demás. Antes era el único marcador en negro de
        // la hoja, y un número largo se salía del recuadro y de la página.
        escribirValor("ACTA N°", d.actaNumero(), H, FIN, 199.78f, arialNegrita, 6.6f, Alinear.CENTRO);
        linea(FILA[12], H, FIN, DELGADA);

        // ── Datos ───────────────────────────────────────────────────────────
        etiqueta("FECHA:", B, 12);
        valor("FECHA", d.fecha(), C, F, 12, arialNegrita, 6.05f, Alinear.CENTRO, true);
        etiqueta("CIUDAD/MUNICIPIO:", F, 12);
        valor("CIUDAD/MUNICIPIO", d.ciudad(), H, FIN, 12, arialNegrita, 6.05f, Alinear.IZQUIERDA, true);
        etiqueta("COD REGIONAL:", B, 13);
        valor("COD REGIONAL", d.codigoRegional(), C, F, 13, arialNegrita, 5.5f, Alinear.CENTRO, true);
        etiqueta("REGIONAL", F, 13);
        valor("REGIONAL", d.regional(), G, FIN, 13, arialNegrita, 6.05f, Alinear.CENTRO, true);
        etiqueta("CENTRO DE COSTO:", B, 14);
        valor("CENTRO DE COSTO", d.centroCosto(), C, F, 14, arialNegrita, 5.5f, Alinear.CENTRO, true);
        etiqueta("COD CENTRO DE COSTO:", B, 15);
        valor("COD CENTRO DE COSTO", d.codigoCentroCosto(), C, F, 15, arialNegrita, 5.5f, Alinear.CENTRO, true);
        etiqueta("TIPO DE ADQUISICIÓN:", B, 16);
        valor("TIPO DE ADQUISICIÓN", d.tipoAdquisicion(), C, F, 16, arialNegrita, 5.5f, Alinear.CENTRO, true);
        etiqueta("TIPO DE ENTREGA:", F, 16);
        valor("TIPO DE ENTREGA", d.tipoEntrega(), H, FIN, 16, arialNegrita, 5.5f, Alinear.CENTRO, true);
        etiqueta("N° DE ACTO ADMINISTRATIVO:", B, 17);
        valor("N° DE ACTO ADMINISTRATIVO", d.numeroActoAdministrativo(), C, F, 17, arialNegrita, 5.5f, Alinear.CENTRO, true);
        etiqueta("FECHA  ACTO ADMINISTRATIVO:", F, 17);
        valor("FECHA ACTO ADMINISTRATIVO", d.fechaActoAdministrativo(), H, FIN, 17, arialNegrita, 5.5f, Alinear.CENTRO, true);
        etiqueta("RUBRO PRESUPUESTAL", B, 18);
        valor("RUBRO PRESUPUESTAL", d.rubroPresupuestal(), C, FIN, 18, arialNegrita, 5.5f, Alinear.IZQUIERDA, true);
        etiqueta("PROVEEDOR CONTRATISTA:", B, 19);
        valor("PROVEEDOR CONTRATISTA", d.proveedor(), C, F, 19, arialNegrita, 5.5f, Alinear.CENTRO, true);
        etiqueta("NIT/CEDULA DE CIUDADANIA:", B, 20);
        valor("NIT/CÉDULA DE CIUDADANÍA", d.nitProveedor(), C, F, 20, arialNegrita, 5.5f, Alinear.CENTRO, true);
        etiqueta("VALOR TOTAL:", B, 21);
        valor("VALOR TOTAL", d.valorTotal(), C, F, 21, arialNegrita, 5.5f, Alinear.CENTRO, true);
        etiqueta("FECHA DE VENCIMIENTO:", B, 22);
        // Sin subrayado propio: se apoya en la línea gruesa de encima del objeto.
        valor("FECHA DE VENCIMIENTO", d.fechaVencimiento(), C, F, 22, arialNegrita, 5.5f, Alinear.CENTRO, false);

        linea(FILA[23], B, FIN, MEDIA);
        etiqueta("OBJETO DEL CONTRATO:", B, 23);
        linea(FILA[24], C, FIN, DELGADA);
        for (int r = 25; r <= 29; r++) {
            linea(FILA[r], B, FIN, DELGADA);
        }
        linea(FILA[30], B, FIN, MEDIA);
        List<Renglon> renglonesObjeto = new ArrayList<>();
        renglonesObjeto.add(new Renglon(C, FIN, 23));
        for (int r = 24; r <= 29; r++) {
            renglonesObjeto.add(new Renglon(B, FIN, r));
        }
        enRenglones("OBJETO DEL CONTRATO", d.objeto(), renglonesObjeto, arialNegrita);

        etiqueta("CANTIDAD BIENES DEVOLUTIVOS", B, 30);
        valor("CANTIDAD BIENES DEVOLUTIVOS", d.cantidadDevolutivos(), C, D, 30, arialNegrita, 5.5f, Alinear.CENTRO, true);
        // En el formato real la etiqueta no cabe en F:G y sale cortada
        // («CONSUMC»): se escribe un poco más pequeña en vez de recortarla.
        texto("CANTIDAD BIENES DE CONSUMO", F + SANGRIA, FILA[31] - SOBRE_LA_LINEA, arialNegrita, 5.35f, NEGRO,
                Alinear.IZQUIERDA, 0);
        valor("CANTIDAD BIENES DE CONSUMO", d.cantidadConsumo(), H, J, 30, arialNegrita, 5.5f, Alinear.CENTRO, true);

        relleno(B, FILA[32], FIN, FILA[33], NEGRO);
        linea(FILA[32], B, FIN, DELGADA);
        // Texto fijo del formato, tal cual: «SATISFACCION» sin tilde y con sus
        // dobles espacios.
        texto("RECIBIDO A SATISFACCION: ", B, 520.66f, arialNegrita, 7.7f, BLANCO, Alinear.CENTRO, FIN);
        texto(" A  través del siguiente documento  certifico  que los bienes recibidos cumplen con las "
                + "características técnicas  y físicas ", B, 529.27f, arialNegrita, 7.7f, BLANCO, Alinear.CENTRO, FIN);
        texto("establecidas por el SENA en el acto administrativo. ", B, 537.87f, arialNegrita, 7.7f, BLANCO,
                Alinear.CENTRO, FIN);
        linea(FILA[33], B, FIN, MEDIA);

        etiqueta("OBSERVACIONES", B, 33);
        linea(FILA[34], C, FIN, DELGADA);
        enRenglones("OBSERVACIONES", d.observaciones(), List.of(new Renglon(C, FIN, 33), new Renglon(B, FIN, 35)),
                carlito);
        linea(FILA[35], B - 0.49f, FIN + 0.48f, DELGADA);
        vertical(B, FILA[35] - 0.2f, FILA[36] + 0.2f, MEDIA);
        vertical(FIN, FILA[35] - 0.2f, FILA[36] + 0.2f, MEDIA);
        linea(FILA[36], B - 0.49f, FIN + 0.48f, DELGADA);

        // ── Recuadro del supervisor ─────────────────────────────────────────
        linea(FILA[37], B - 0.49f, FIN + 0.48f, DELGADA);
        vertical(B, FILA[37] - 0.2f, FILA[45] + 0.48f, MEDIA);
        vertical(FIN, FILA[37] - 0.2f, FILA[45] + 0.48f, MEDIA);
        linea(FILA[38], B - 0.49f, FIN + 0.48f, MEDIA);
        etiqueta("FIRMA SUPERVISOR", B, 39);
        linea(FILA[40], C, F, DELGADA);
        etiqueta("NOMBRE COMPLETO", B, 40);
        valor("NOMBRE COMPLETO DEL SUPERVISOR", d.nombreSupervisor(), C, F, 40, carlitoNegrita, 5.5f, Alinear.CENTRO, true);
        etiqueta("N° DE IDENTIFICACIÓN", B, 41);
        valor("N° DE IDENTIFICACIÓN DEL SUPERVISOR", d.identificacionSupervisor(), C, F, 41, carlitoNegrita, 5.5f, Alinear.CENTRO, true);
        etiqueta("CORREO INSTITUCIONAL", B, 42);
        valor("CORREO INSTITUCIONAL DEL SUPERVISOR", d.correoSupervisor(), C, F, 42, carlito, 6.6f, Alinear.CENTRO, true);
        etiqueta("CARGO", B, 43);
        valor("CARGO DEL SUPERVISOR", d.cargoSupervisor(), C, F, 43, carlitoNegrita, 5.5f, Alinear.CENTRO, true);
        etiqueta("N° DE CONTACTO", F, 43);
        valor("N° DE CONTACTO DEL SUPERVISOR", d.contactoSupervisor(), H, FIN, 43, carlitoNegrita, 5.5f, Alinear.CENTRO, true);
        linea(FILA[45], B - 0.49f, FIN + 0.48f, MEDIA);

        // Hueco de la firma: sobre la línea de FIRMA SUPERVISOR, en C:E.
        float xFirma = x(C + 8f);
        float yFirma = y(FILA[40] - 1f);
        return new float[]{xFirma, yFirma, (F - C - 16f) * K, (FILA[40] - FILA[38] - 1f) * K};
    }

    // ── Casillas ────────────────────────────────────────────────────────────

    private void etiqueta(String texto, float columna, int fila) throws IOException {
        texto(texto, columna + SANGRIA, FILA[fila + 1] - SOBRE_LA_LINEA, arialNegrita, 5.5f, NEGRO,
                Alinear.IZQUIERDA, 0);
    }

    /**
     * Un valor de una línea sobre su subrayado. Si no cabe se parte en dos
     * renglones dentro de la misma fila y, si aun así no cabe, se reduce la
     * letra hasta {@value #LETRA_MINIMA} pt.
     */
    private void valor(String rotulo, String valor, float desde, float hasta, int fila, PDFont f, float tamano,
                       Alinear alinear, boolean subrayar) throws IOException {
        if (subrayar) {
            linea(FILA[fila + 1], desde, hasta, DELGADA);
        }
        escribirValor(rotulo, valor, desde, hasta, FILA[fila + 1] - SOBRE_LA_LINEA, f, tamano, alinear);
    }

    /**
     * @param rotulo con qué nombre aparece el valor en la hoja de continuación
     *               si no cabe: «Casilla de la fila 30» no decía si eran los
     *               bienes devolutivos o los de consumo, que van en la misma fila.
     */
    private void escribirValor(String rotulo, String valor, float desde, float hasta, float baseInferior, PDFont f,
                               float tamano, Alinear alinear) throws IOException {
        if (valor == null || valor.isBlank()) {
            return;
        }
        String t = fuentes.escribible(f, valor.strip());
        Color color = t.startsWith("[dato") ? PdfInstitucional.ROJO_PENDIENTE : NEGRO;
        float disponible = hasta - desde - 2 * SANGRIA;
        float tam = tamano;
        if (FuentesDelDocumento.ancho(f, tam, t) <= disponible) {
            texto(t, desde + SANGRIA, baseInferior, f, tam, color, alinear, hasta - SANGRIA);
            return;
        }
        List<String> lineas = partir(t, f, tam, List.of(disponible));
        while (lineas.size() > 2 && tam > LETRA_MINIMA) {
            tam = Math.round((tam - 0.1f) * 100) / 100f;
            lineas = partir(t, f, tam, List.of(disponible));
        }
        if (lineas.size() > 2) {
            // Más de dos renglones pisarían la fila de arriba: el valor completo
            // va a la hoja de continuación y aquí queda el comienzo con el aviso.
            continuaciones.add(new String[]{rotulo, valor.strip()});
            // En las casillas estrechas el aviso largo no cabe al lado del
            // texto: reservarle su ancho dejaba un ancho negativo, y las
            // cantidades salían letra por letra («2 6 re s m a s»). Ahí va el
            // aviso corto, y si tampoco cabe al lado, en su propio renglón.
            String aviso = FuentesDelDocumento.ancho(f, tam, " " + AVISO) <= disponible / 2 ? AVISO : AVISO_CORTO;
            float resto = disponible - FuentesDelDocumento.ancho(f, tam, " " + aviso);
            if (resto >= disponible / 3) {
                List<String> cabe = partir(t, f, tam, List.of(disponible, resto));
                lineas = List.of(cabe.get(0), cabe.get(1) + " " + aviso);
            } else {
                lineas = List.of(partir(t, f, tam, List.of(disponible)).getFirst(), aviso);
            }
        }
        float base = baseInferior - (lineas.size() - 1) * tam * 1.15f;
        for (String l : lineas) {
            texto(l, desde + SANGRIA, base, f, tam, color, alinear, hasta - SANGRIA);
            base += tam * 1.15f;
        }
    }

    private record Renglon(float desde, float hasta, int fila) {
    }

    /** Un texto largo repartido en renglones fijos; se reduce la letra hasta que quepa. */
    private void enRenglones(String etiqueta, String texto, List<Renglon> renglones, PDFont f) throws IOException {
        if (texto == null || texto.isBlank()) {
            return;
        }
        String t = fuentes.escribible(f, texto.strip().replace('\n', ' '));
        List<Float> anchos = renglones.stream().map(r -> r.hasta() - r.desde() - 2 * SANGRIA).toList();
        float tam = 5.5f;
        List<String> lineas = partir(t, f, tam, anchos);
        while (lineas.size() > renglones.size() && tam > LETRA_MINIMA) {
            tam = Math.round((tam - 0.1f) * 100) / 100f;
            lineas = partir(t, f, tam, anchos);
        }
        if (lineas.size() > renglones.size()) {
            // Ni a la letra mínima cabe: el texto completo va a la hoja de
            // continuación y el último renglón lo avisa, en vez de perder la
            // cola en silencio en un documento firmado.
            continuaciones.add(new String[]{etiqueta, texto.strip()});
            List<Float> conAviso = new ArrayList<>(anchos);
            int ultimo = renglones.size() - 1;
            conAviso.set(ultimo, anchos.get(ultimo) - FuentesDelDocumento.ancho(f, tam, " " + AVISO));
            List<String> cabe = new ArrayList<>(partir(t, f, tam, conAviso).subList(0, renglones.size()));
            cabe.set(ultimo, cabe.get(ultimo) + " " + AVISO);
            lineas = cabe;
        }
        boolean pendiente = t.startsWith("[dato");
        for (int i = 0; i < lineas.size(); i++) {
            Renglon r = renglones.get(i);
            boolean centrar = lineas.size() == 1 && r.desde() == C;
            texto(lineas.get(i), r.desde() + SANGRIA, FILA[r.fila() + 1] - SOBRE_LA_LINEA, f, tam,
                    pendiente ? PdfInstitucional.ROJO_PENDIENTE : NEGRO,
                    centrar ? Alinear.CENTRO : Alinear.IZQUIERDA, r.hasta() - SANGRIA);
        }
    }

    private static List<String> partir(String texto, PDFont f, float tamano, List<Float> anchos) throws IOException {
        List<String> lineas = new ArrayList<>();
        StringBuilder actual = new StringBuilder();
        for (String entera : texto.split(" ")) {
            if (entera.isEmpty()) {
                continue;
            }
            // Una palabra más ancha que su renglón (un enlace del SECOP) se
            // parte en trozos: sin esto salía por fuera del recuadro de la hoja.
            // Nunca un límite de cero o negativo: partía la palabra en letras sueltas.
            float limiteDePalabra = Math.max(anchos.stream().min(Float::compare).orElse(100f), 12f);
            List<String> trozos = new ArrayList<>();
            StringBuilder trozo = new StringBuilder();
            for (int i = 0; i < entera.length(); ) {
                int cp = entera.codePointAt(i);
                String siguiente = trozo + new String(Character.toChars(cp));
                if (!trozo.isEmpty() && FuentesDelDocumento.ancho(f, tamano, siguiente) > limiteDePalabra) {
                    trozos.add(trozo.toString());
                    trozo.setLength(0);
                } else {
                    trozo.appendCodePoint(cp);
                    i += Character.charCount(cp);
                }
            }
            trozos.add(trozo.toString());
            for (int k = 0; k < trozos.size(); k++) {
                String palabra = trozos.get(k);
                // Los trozos de una misma palabra que caben en el mismo renglón
                // se vuelven a juntar sin espacio: «Antio quia» no es lo escrito.
                String candidata = actual.isEmpty() ? palabra : actual + (k == 0 ? " " : "") + palabra;
                float limite = anchos.get(Math.min(lineas.size(), anchos.size() - 1));
                if (FuentesDelDocumento.ancho(f, tamano, candidata) <= limite || actual.isEmpty()) {
                    actual = new StringBuilder(candidata);
                } else {
                    lineas.add(actual.toString());
                    actual = new StringBuilder(palabra);
                }
            }
        }
        if (!actual.isEmpty()) {
            lineas.add(actual.toString());
        }
        return lineas;
    }

    // ── Primitivas en coordenadas de la hoja ────────────────────────────────

    private enum Alinear { IZQUIERDA, CENTRO }

    private static float x(float xHoja) {
        return IZQUIERDA + (xHoja - B) * K;
    }

    /** De «desde arriba, en la hoja» a coordenadas PDF. */
    private static float y(float yHoja) {
        return PdfInstitucional.ALTO - (ARRIBA + (yHoja - FILA[2]) * K);
    }

    private void texto(String texto, float xHoja, float baseHoja, PDFont f, float tamano, Color color,
                       Alinear alinear, float hastaHoja) throws IOException {
        String t = fuentes.escribible(f, texto);
        if (t.isBlank()) {
            return;
        }
        float tam = tamano * K;
        float xx = x(xHoja);
        if (alinear == Alinear.CENTRO) {
            float w = FuentesDelDocumento.ancho(f, tam, t);
            xx = (x(xHoja) + x(hastaHoja)) / 2 - w / 2;
        }
        cs.beginText();
        cs.setNonStrokingColor(color);
        cs.setFont(f, tam);
        cs.newLineAtOffset(xx, y(baseHoja));
        cs.showText(t);
        cs.endText();
    }

    private void linea(float yHoja, float desde, float hasta, float grosor) throws IOException {
        cs.setStrokingColor(NEGRO);
        cs.setLineWidth(grosor * K);
        cs.moveTo(x(desde), y(yHoja));
        cs.lineTo(x(hasta), y(yHoja));
        cs.stroke();
    }

    private void vertical(float xHoja, float desde, float hasta, float grosor) throws IOException {
        cs.setStrokingColor(NEGRO);
        cs.setLineWidth(grosor * K);
        cs.moveTo(x(xHoja), y(desde));
        cs.lineTo(x(xHoja), y(hasta));
        cs.stroke();
    }

    private void relleno(float x0, float y0, float x1, float y1, Color color) throws IOException {
        cs.setNonStrokingColor(color);
        cs.addRect(x(x0), y(y1), (x1 - x0) * K, (y1 - y0) * K);
        cs.fill();
    }

    private void casilla(float x0, float y0, float x1, float y1) throws IOException {
        cs.setNonStrokingColor(BLANCO);
        cs.addRect(x(x0), y(y1), (x1 - x0) * K, (y1 - y0) * K);
        cs.fill();
        cs.setStrokingColor(NEGRO);
        cs.setLineWidth(0.5457f * K);
        cs.addRect(x(x0), y(y1), (x1 - x0) * K, (y1 - y0) * K);
        cs.stroke();
    }
}
