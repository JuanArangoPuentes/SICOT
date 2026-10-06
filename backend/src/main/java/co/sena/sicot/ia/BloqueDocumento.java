package co.sena.sicot.ia;

import java.util.Arrays;
import java.util.List;

/**
 * Las piezas de un documento formal, tal como las dibuja {@link PdfInstitucional}.
 *
 * <p>Existen porque los formatos reales del SENA (GCCON-F-018, GCCON-F-030,
 * GCCON-F-031, GIL-F-010, el certificado ESUCON) no son prosa: son fichas de
 * etiqueta y valor, tablas con celdas combinadas, títulos centrados y bloques
 * de firma, con tamaños de letra distintos según la parte. Un documento que
 * llega como una lista de párrafos solo puede imitarlos pidiéndole al modelo
 * que redacte también los datos, que es donde el modelo los alteraba.
 *
 * <p>Un texto que contiene «[dato pendiente…]» es un dato que SICOT no tiene y
 * que alguien debe completar: el PDF lo pinta en rojo, que es la convención de
 * los propios formatos del SENA para lo que falta diligenciar («El contenido
 * que se encuentra en color diferente a negro […] son orientaciones para el
 * diligenciamiento del formato», GCCON-F-031, Generalidades 9).
 */
public sealed interface BloqueDocumento {

    /** Cómo se escribe un tramo de texto. */
    enum Estilo { NORMAL, NEGRITA, ITALICA, NEGRITA_ITALICA }

    enum Alineacion { IZQUIERDA, CENTRO, DERECHA, JUSTIFICADO }

    /** Un trozo de texto con un solo estilo. */
    record Tramo(String texto, Estilo estilo) {
        public static Tramo normal(String texto) {
            return new Tramo(texto, Estilo.NORMAL);
        }

        public static Tramo negrita(String texto) {
            return new Tramo(texto, Estilo.NEGRITA);
        }

        public static Tramo italica(String texto) {
            return new Tramo(texto, Estilo.ITALICA);
        }
    }

    /**
     * Cambia el tamaño de letra y el interlineado de lo que sigue. Los formatos
     * mezclan tamaños: el GCCON-F-031 escribe los títulos y párrafos a 11 pt,
     * la ficha y la tabla de obligaciones a 10 y la de seguridad social a 9.
     *
     * @param multiplo interlineado de Word: 1,15 es el «Normal» de los formatos;
     *                 1 es sencillo (la ficha del GCCON-F-030).
     */
    record Letra(float puntos, float multiplo) implements BloqueDocumento {
        public Letra(float puntos) {
            this(puntos, 1.15f);
        }
    }

    /** Líneas centradas, como «PROCESO GESTIÓN CONTRACTUAL / FORMATO ACTA DE INICIO». */
    record Titulo(List<Tramo> lineas) implements BloqueDocumento {
        public static Titulo de(String... lineasEnNegrita) {
            return new Titulo(Arrays.stream(lineasEnNegrita).map(Tramo::negrita).toList());
        }
    }

    /**
     * Encabezado de un apartado. El número va aparte porque los formatos lo
     * sangran como una lista de Word: «1.» en el margen y el título 1 cm más
     * adentro.
     *
     * @param numero «1.», «1.1.», «2.»… o {@code null} si el apartado no va numerado.
     */
    record Seccion(String numero, String titulo) implements BloqueDocumento {
        public Seccion(String titulo) {
            this(null, titulo);
        }
    }

    /**
     * Texto corrido, con tramos en negrita o cursiva si hace falta.
     *
     * @param sangria cuánto más adentro del margen izquierdo empieza (el «Itagüí,
     *                …» del certificado va 1,5 cm más adentro que su cuerpo).
     */
    record Parrafo(List<Tramo> tramos, Alineacion alineacion, float sangria) implements BloqueDocumento {
        /** Un párrafo normal justificado, que es como van los párrafos de los formatos. */
        public Parrafo(String texto) {
            this(List.of(Tramo.normal(texto)), Alineacion.JUSTIFICADO, 0);
        }

        public Parrafo(Alineacion alineacion, Tramo... tramos) {
            this(List.of(tramos), alineacion, 0);
        }

        public Parrafo(List<Tramo> tramos, Alineacion alineacion) {
            this(tramos, alineacion, 0);
        }

        /** El texto plano del párrafo, sin estilos. */
        public String texto() {
            StringBuilder sb = new StringBuilder();
            tramos.forEach(t -> sb.append(t.texto()));
            return sb.toString();
        }
    }

    /**
     * Tabla de dos columnas, etiqueta y valor, como las de los formatos.
     *
     * @param anchoEtiqueta fracción del ancho útil que ocupa la etiqueta (0,2896 en
     *                      el Acta de Inicio; 0,353 en el Informe de Supervisión).
     * @param relleno       margen interior izquierdo y derecho de cada celda.
     * @param centrarVertical si el texto de una fila baja se centra en la altura
     *                      de la fila (GCCON-F-018, GCCON-F-030) o va arriba (GCCON-F-031).
     */
    record Ficha(List<Campo> campos, float anchoEtiqueta, float relleno, boolean centrarVertical)
            implements BloqueDocumento {
        public Ficha(List<Campo> campos) {
            this(campos, 0.30f, 5.4f, true);
        }
    }

    /**
     * Una fila de la ficha. Un valor que empieza por «[» es un dato que SICOT no
     * tiene y que el supervisor debe completar; el PDF lo resalta.
     */
    record Campo(String etiqueta, String valor, Estilo estiloValor) {
        public Campo(String etiqueta, String valor) {
            this(etiqueta, valor, Estilo.NORMAL);
        }
    }

    /**
     * Tabla con celdas que pueden abarcar varias columnas o filas: la garantía
     * del Informe de Supervisión («VIGENCIA» sobre «DESDE | HASTA»), las
     * obligaciones del Informe Final, la imputación presupuestal del
     * certificado.
     *
     * @param anchos             ancho de cada columna en puntos.
     * @param filas              filas de arriba abajo.
     * @param filasDeEncabezado  cuántas de las primeras filas se repiten si la
     *                           tabla sigue en otra página (0 = ninguna).
     * @param centradaEnLaHoja   si se centra en la hoja (tabla de pagos del
     *                           GCCON-F-030, imputación del certificado) o empieza
     *                           en el margen izquierdo.
     * @param relleno            margen interior izquierdo y derecho de cada celda.
     */
    record Tabla(List<Float> anchos, List<Fila> filas, int filasDeEncabezado, boolean centradaEnLaHoja,
                 float relleno) implements BloqueDocumento {
        public Tabla {
            for (Fila fila : filas) {
                int columnas = fila.celdas().stream().mapToInt(Celda::columnas).sum();
                if (columnas > anchos.size()) {
                    throw new IllegalArgumentException("Una fila abarca " + columnas + " columnas y la tabla tiene "
                            + anchos.size() + ".");
                }
            }
        }
    }

    /** Una fila de tabla. Las celdas cubiertas por una celda que baja desde la fila de arriba no se escriben. */
    record Fila(List<Celda> celdas) {
        public static Fila de(Celda... celdas) {
            return new Fila(List.of(celdas));
        }
    }

    /**
     * Una celda.
     *
     * @param gris fondo #D9D9D9, el de los encabezados de tabla de los formatos.
     */
    record Celda(List<Tramo> tramos, int columnas, int filas, Alineacion alineacion, boolean gris,
                 boolean centrarVertical) {
        public static Celda de(String texto) {
            return new Celda(List.of(Tramo.normal(texto == null ? "" : texto)), 1, 1, Alineacion.IZQUIERDA, false, true);
        }

        public static Celda encabezado(String texto) {
            return new Celda(List.of(Tramo.negrita(texto)), 1, 1, Alineacion.CENTRO, true, true);
        }

        public Celda abarcando(int columnas, int filas) {
            return new Celda(tramos, columnas, filas, alineacion, gris, centrarVertical);
        }

        public Celda alineada(Alineacion otra) {
            return new Celda(tramos, columnas, filas, otra, gris, centrarVertical);
        }

        public Celda enNegrita() {
            return new Celda(tramos.stream().map(t -> new Tramo(t.texto(), Estilo.NEGRITA)).toList(), columnas, filas,
                    alineacion, gris, centrarVertical);
        }

        /**
         * Hoy ningún documento la llama, y se mantiene a propósito: es la única
         * forma de encender el {@code gris} que {@code PdfInstitucional} sí
         * pinta (#D9D9D9, el de los encabezados de tabla de los formatos). Si se
         * borrara, habría que borrar también el componente del record y su rama
         * en el renderizador, y con ellos el fondo de encabezado que los
         * formatos del SENA llevan de verdad.
         */
        public Celda conFondoGris() {
            return new Celda(tramos, columnas, filas, alineacion, true, centrarVertical);
        }
    }

    enum DisposicionFirmas {
        /** Columnas lado a lado sin bordes (GCCON-F-018: supervisor y contratista). */
        COLUMNAS,
        /** Tabla con bordes de dos columnas (GCCON-F-031: supervisor y apoyo). */
        TABLA,
        /** Un firmante centrado en la hoja, en negrita (certificado ESUCON). */
        CENTRADA,
        /** Un firmante a la izquierda sin bordes (GCCON-F-030). */
        IZQUIERDA
    }

    /**
     * Bloque de firmas. La firma va en el espacio en blanco encima de los
     * nombres, como en los formatos; ahí la estampa SICOT al firmar.
     *
     * @param espacioParaFirmar alto del hueco encima del nombre.
     */
    record Firmas(List<Firmante> firmantes, DisposicionFirmas disposicion, float espacioParaFirmar)
            implements BloqueDocumento {
    }

    /** Quien firma: nombre (en negrita si el formato lo pide) y las líneas de debajo. */
    record Firmante(String nombre, boolean nombreEnNegrita, List<String> lineas) {
    }

    /** Texto pequeño, como el «Elaboró:» del Acta de Inicio. */
    record NotaPequena(String texto) implements BloqueDocumento {
    }

    /** Líneas en blanco del tamaño de letra actual: así separan los formatos sus apartados. */
    record LineasEnBlanco(int lineas) implements BloqueDocumento {
    }

    /**
     * Lo que sigue necesita al menos estos puntos en la misma página: si no
     * caben, empieza una página nueva. Evita que la firma quede sola en una
     * hoja, separada del «Itagüí, fecha» o del «Para constancia se firma».
     */
    record MantenerJunto(float puntos) implements BloqueDocumento {
    }

    /** Espacio vertical en puntos. */
    record Espacio(float puntos) implements BloqueDocumento {
    }

    /**
     * El Acta de Recibo GIL-F-010 completa. No es un documento que fluye sino
     * una hoja de cálculo con cada casilla en su sitio, así que se dibuja entera
     * con {@code HojaActaDeRecibo} a partir de sus valores.
     */
    record HojaDeRecibo(DatosActaDeRecibo datos) implements BloqueDocumento {
    }

    /**
     * Las casillas de la GIL-F-010, ya con el texto que va en cada una (o el
     * marcador de pendiente).
     */
    record DatosActaDeRecibo(String actaNumero, String fecha, String ciudad, String codigoRegional, String regional,
                             String centroCosto, String codigoCentroCosto, String tipoAdquisicion,
                             String tipoEntrega, String numeroActoAdministrativo, String fechaActoAdministrativo,
                             String rubroPresupuestal, String proveedor, String nitProveedor, String valorTotal,
                             String fechaVencimiento, String objeto, String cantidadDevolutivos,
                             String cantidadConsumo, String observaciones, String nombreSupervisor,
                             String identificacionSupervisor, String correoSupervisor, String cargoSupervisor,
                             String contactoSupervisor) {
    }
}
