package co.sena.sicot.service;

import co.sena.sicot.dto.pedido.GuardarSolicitudDePedidoRequest;
import co.sena.sicot.dto.pedido.ItemDeSolicitud;
import co.sena.sicot.dto.pedido.SolicitudDePedidoResponse;
import co.sena.sicot.entity.Contrato;
import co.sena.sicot.entity.ItemSolicitudPedido;
import co.sena.sicot.exception.BusinessException;
import co.sena.sicot.repository.ItemSolicitudPedidoRepository;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * La solicitud de pedido que el supervisor le manda al contratista: los bienes
 * con sus cantidades, y el Excel que se adjunta al correo del paso 2.4.
 *
 * <h2>De dónde viene</h2>
 * Del paso 5 de la lista de chequeo de Alex Zapata, que él completó el
 * 20-10-2026: «se debe generar una preforma de correo […] donde se relaciona un
 * formato con el listado de partes y suministros a recibir, este será un
 * documento de excel adjunto en el correo», y como requerimiento, «generar y
 * adjuntar el documento excel con la lista de materiales solicitado (definir
 * cómo será este proceso y dónde se encontrará esta documentación)».
 *
 * <p>La preforma de correo ya existía y dejaba un hueco entre corchetes donde
 * debían ir las cantidades. Esto llena ese hueco con un archivo, que es lo que
 * el contratista recibe hoy.
 *
 * <h2>Qué se reproduce y qué no</h2>
 * El formato es el que el supervisor usa hoy: «Solicitud_Maybe.xlsx», el pedido
 * del Lote 4 que Alex adjuntó a la lista. De ahí salen el encabezado, la tabla
 * (ITEMS · DESCRIPCIÓN · CANTIDAD · VALOR UNITARIO CON IVA · VALOR TOTAL CON
 * IVA), el bloque de presupuesto (ASIGNADO · COMPROMETIDO · QUEDA), el subtotal
 * y la firma del supervisor.
 *
 * <p>Lo que no se copia son los datos de ESE contrato: el nombre del lote, el
 * proponente, las cifras. Cada contrato trae los suyos, y los que no estén
 * registrados se dejan en blanco en vez de arrastrar los del Lote 4 —el
 * archivo se le manda a un contratista, y un dato de otro contrato dentro de
 * él es peor que un espacio vacío—.
 */
@Service
public class SolicitudDePedidoService {

    /** El Centro al que pertenecen los contratos que supervisa SICOT. */
    private static final String CENTRO = "CENTRO TECNOLÓGICO DEL MOBILIARIO";

    private final ContratoService contratoService;
    private final ItemSolicitudPedidoRepository itemRepository;

    public SolicitudDePedidoService(ContratoService contratoService,
                                    ItemSolicitudPedidoRepository itemRepository) {
        this.contratoService = contratoService;
        this.itemRepository = itemRepository;
    }

    /**
     * La solicitud del contrato con su control de presupuesto.
     *
     * <p>El acceso lo restringe {@code ContratoService.buscar}: al SUPERVISOR,
     * a sus contratos. Mismo patrón que la verificación en SECOP.
     */
    @Transactional(readOnly = true)
    public SolicitudDePedidoResponse obtener(Long contratoId) {
        Contrato contrato = contratoService.buscar(contratoId);
        return armarRespuesta(contrato, itemRepository.findByContratoIdOrderByNumeroAsc(contratoId));
    }

    /**
     * Reemplaza la solicitud del contrato por la que manda la pantalla.
     *
     * <p>Una lista vacía la borra: así se deshace un pegado equivocado sin
     * tener que editar 35 filas.
     */
    @Transactional
    public SolicitudDePedidoResponse guardar(Long contratoId, GuardarSolicitudDePedidoRequest request) {
        Contrato contrato = contratoService.buscar(contratoId);
        List<GuardarSolicitudDePedidoRequest.Item> entrada =
                request.items() == null ? List.of() : request.items();
        verificarNumerosSinRepetir(entrada);

        // Borrar e insertar: ver ItemSolicitudPedidoRepository.borrarDelContrato.
        // El flush explícito manda el DELETE antes de los INSERT; sin él,
        // Hibernate ordena las sentencias por tipo de entidad y los INSERT
        // saldrían primero, chocando con la restricción de número único.
        itemRepository.borrarDelContrato(contratoId);
        itemRepository.flush();

        List<ItemSolicitudPedido> guardados = itemRepository.saveAll(entrada.stream()
                .map(i -> new ItemSolicitudPedido(contrato, i.numero(), i.descripcion().strip(),
                        i.cantidad(), i.valorUnitario()))
                .toList());
        return armarRespuesta(contrato, ordenados(guardados));
    }

    /**
     * El XLSX que se adjunta al correo del contratista.
     *
     * <p>Se genera al pedirlo y no se guarda: el archivo es una vista de los
     * ítems, y uno almacenado quedaría desactualizado en cuanto el supervisor
     * corrija una cantidad —y se lo mandaría al contratista creyendo que es el
     * bueno—.
     */
    @Transactional(readOnly = true)
    public byte[] generarArchivo(Long contratoId) {
        Contrato contrato = contratoService.buscar(contratoId);
        List<ItemSolicitudPedido> items = itemRepository.findByContratoIdOrderByNumeroAsc(contratoId);
        if (items.isEmpty()) {
            throw new BusinessException("La solicitud de pedido está vacía. Cargue los bienes y sus cantidades "
                    + "—puede pegarlos de la oferta económica— antes de generar el archivo.");
        }
        return escribirLibro(contrato, items);
    }

    /** El nombre con el que se descarga, para que el adjunto se reconozca en el correo. */
    public String nombreDelArchivo(Long contratoId) {
        Contrato contrato = contratoService.buscar(contratoId);
        String numero = contrato.getNumeroContrato() == null ? String.valueOf(contratoId)
                : contrato.getNumeroContrato().replaceAll("[^A-Za-z0-9._-]", "_");
        return "Solicitud de pedido " + numero + ".xlsx";
    }

    /**
     * Dos ítems con el mismo número dejarían el archivo con dos filas iguales y
     * nadie sabría cuál vale. Lo prohíbe también la base; aquí se avisa con el
     * número concreto, porque un 409 de restricción única no le dice al
     * supervisor cuál fila repitió al pegar.
     */
    private static void verificarNumerosSinRepetir(List<GuardarSolicitudDePedidoRequest.Item> items) {
        Set<Integer> vistos = new HashSet<>();
        for (GuardarSolicitudDePedidoRequest.Item i : items) {
            if (!vistos.add(i.numero())) {
                throw new BusinessException("El ítem número " + i.numero() + " está repetido en la solicitud. "
                        + "Cada bien debe tener un número distinto.");
            }
        }
    }

    private static SolicitudDePedidoResponse armarRespuesta(Contrato contrato, List<ItemSolicitudPedido> items) {
        BigDecimal comprometido = items.stream()
                .map(ItemSolicitudPedido::valorTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal asignado = contrato.getValor() == null ? BigDecimal.ZERO : contrato.getValor();
        BigDecimal queda = asignado.subtract(comprometido);
        return new SolicitudDePedidoResponse(
                items.stream()
                        .map(i -> new ItemDeSolicitud(i.getNumero(), i.getDescripcion(), i.getCantidad(),
                                i.getValorUnitario(), i.valorTotal()))
                        .toList(),
                asignado,
                comprometido,
                queda,
                queda.signum() < 0);
    }

    // ─────────────────────────── El archivo ───────────────────────────

    private byte[] escribirLibro(Contrato contrato, List<ItemSolicitudPedido> items) {
        try (XSSFWorkbook libro = new XSSFWorkbook(); ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            XSSFSheet hoja = libro.createSheet("Solicitud de pedido");
            Estilos estilos = new Estilos(libro);

            int fila = 0;
            fila = escribirEncabezado(hoja, estilos, contrato, fila);
            fila = escribirProponente(hoja, estilos, contrato, fila);
            fila = escribirTabla(hoja, estilos, contrato, items, fila);
            escribirFirma(hoja, estilos, contrato, fila + 1);

            anchosDeColumna(hoja);
            libro.write(salida);
            return salida.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo generar el Excel de la solicitud de pedido.", e);
        }
    }

    private int escribirEncabezado(XSSFSheet hoja, Estilos e, Contrato c, int fila) {
        // Las tres líneas del formato real: el contratista y el Centro, el
        // título, y el objeto del contrato.
        celdaFusionada(hoja, fila, e.titulo,
                (c.getContratista() == null ? "" : c.getContratista().toUpperCase(Locale.ROOT) + " — ") + CENTRO);
        celdaFusionada(hoja, ++fila, e.titulo, "SOLICITUD DE PEDIDO");
        celdaFusionada(hoja, ++fila, e.objeto, c.getObjeto() == null ? "" : c.getObjeto());
        hoja.getRow(fila).setHeightInPoints(28);
        return fila + 2;
    }

    private int escribirProponente(XSSFSheet hoja, Estilos e, Contrato c, int fila) {
        Row cabecera = hoja.createRow(fila);
        texto(cabecera, 0, "No.", e.encabezadoTabla);
        texto(cabecera, 1, "Proponente", e.encabezadoTabla);
        texto(cabecera, 2, "NIT", e.encabezadoTabla);
        texto(cabecera, 3, "Contrato", e.encabezadoTabla);
        texto(cabecera, 4, "Valor del contrato", e.encabezadoTabla);

        Row datos = hoja.createRow(fila + 1);
        numero(datos, 0, 1, e.celdaCentrada);
        texto(datos, 1, c.getContratista() == null ? "" : c.getContratista(), e.celda);
        texto(datos, 2, c.getContratistaNit() == null ? "" : c.getContratistaNit(), e.celdaCentrada);
        texto(datos, 3, c.getNumeroContrato() == null ? "" : c.getNumeroContrato(), e.celdaCentrada);
        dinero(datos, 4, c.getValor() == null ? BigDecimal.ZERO : c.getValor(), e.moneda);
        return fila + 3;
    }

    private int escribirTabla(XSSFSheet hoja, Estilos e, Contrato c, List<ItemSolicitudPedido> items, int fila) {
        Row cabecera = hoja.createRow(fila);
        texto(cabecera, 0, "ITEMS", e.encabezadoTabla);
        texto(cabecera, 1, "DESCRIPCIÓN", e.encabezadoTabla);
        texto(cabecera, 2, "CANTIDAD", e.encabezadoTabla);
        texto(cabecera, 3, "VALOR UNITARIO CON IVA", e.encabezadoTabla);
        texto(cabecera, 4, "VALOR TOTAL CON IVA", e.encabezadoTabla);
        // El bloque de presupuesto del formato real, a la derecha de la tabla.
        texto(cabecera, 6, "PRESUPUESTO DEL CONTRATO", e.encabezadoTabla);

        BigDecimal comprometido = BigDecimal.ZERO;
        int primeraFilaDeItems = fila + 1;
        int f = primeraFilaDeItems;
        for (ItemSolicitudPedido item : items) {
            Row r = hoja.createRow(f);
            numero(r, 0, item.getNumero(), e.celdaCentrada);
            texto(r, 1, item.getDescripcion(), e.descripcion);
            numero(r, 2, item.getCantidad(), e.celdaCentrada);
            dinero(r, 3, item.getValorUnitario(), e.moneda);
            dinero(r, 4, item.valorTotal(), e.moneda);
            comprometido = comprometido.add(item.valorTotal());
            f++;
        }

        Row total = hoja.createRow(f);
        texto(total, 0, "SUBTOTAL", e.encabezadoTabla);
        hoja.addMergedRegion(new CellRangeAddress(f, f, 0, 3));
        dinero(total, 4, comprometido, e.monedaTotal);

        // Después del subtotal, y con filaOCrear: con uno o dos ítems el bloque
        // de presupuesto cae sobre la fila del subtotal, y createRow la
        // reemplazaría sin decir nada, dejando el archivo sin la suma.
        BigDecimal asignado = c.getValor() == null ? BigDecimal.ZERO : c.getValor();
        escribirPresupuesto(hoja, e, primeraFilaDeItems, asignado, comprometido);
        return f + 1;
    }

    /**
     * ASIGNADO · COMPROMETIDO · QUEDA, las tres celdas con las que el
     * supervisor sabe si el pedido cabe en el contrato. Cuando no cabe, lo dice
     * en la misma celda: el archivo sale igual —puede faltarle media tabla por
     * pegar— pero no sale callado.
     */
    private void escribirPresupuesto(XSSFSheet hoja, Estilos e, int primeraFila,
                                     BigDecimal asignado, BigDecimal comprometido) {
        BigDecimal queda = asignado.subtract(comprometido);
        String[] etiquetas = {"ASIGNADO", "COMPROMETIDO", "QUEDA"};
        BigDecimal[] valores = {asignado, comprometido, queda};
        for (int i = 0; i < etiquetas.length; i++) {
            Row r = filaOCrear(hoja, primeraFila + i);
            texto(r, 6, etiquetas[i], e.celda);
            dinero(r, 7, valores[i], queda.signum() < 0 && i == 2 ? e.monedaEnRojo : e.moneda);
        }
        if (queda.signum() < 0) {
            Row r = filaOCrear(hoja, primeraFila + 3);
            texto(r, 6, "La solicitud excede el valor del contrato.", e.avisoEnRojo);
            hoja.addMergedRegion(new CellRangeAddress(primeraFila + 3, primeraFila + 3, 6, 7));
        }
    }

    private void escribirFirma(XSSFSheet hoja, Estilos e, Contrato c, int fila) {
        Row nombre = hoja.createRow(fila + 1);
        texto(nombre, 0, c.getSupervisor() == null ? "" : c.getSupervisor().getNombre().toUpperCase(Locale.ROOT),
                e.firma);
        Row cargo = hoja.createRow(fila + 2);
        texto(cargo, 0, "SUPERVISOR", e.firma);
    }

    private static void anchosDeColumna(XSSFSheet hoja) {
        hoja.setColumnWidth(0, 2_200);   // ITEMS
        hoja.setColumnWidth(1, 22_000);  // DESCRIPCIÓN: la ficha técnica completa
        hoja.setColumnWidth(2, 3_000);   // CANTIDAD
        hoja.setColumnWidth(3, 6_500);   // VALOR UNITARIO
        hoja.setColumnWidth(4, 6_500);   // VALOR TOTAL
        hoja.setColumnWidth(5, 1_200);   // separación con el bloque de presupuesto
        hoja.setColumnWidth(6, 7_500);
        hoja.setColumnWidth(7, 6_500);
    }

    /** La fila, sin reemplazarla si ya tiene celdas escritas. */
    private static Row filaOCrear(XSSFSheet hoja, int fila) {
        Row r = hoja.getRow(fila);
        return r == null ? hoja.createRow(fila) : r;
    }

    private static void celdaFusionada(XSSFSheet hoja, int fila, CellStyle estilo, String valor) {
        Row r = hoja.createRow(fila);
        texto(r, 0, valor, estilo);
        hoja.addMergedRegion(new CellRangeAddress(fila, fila, 0, 4));
    }

    private static void texto(Row fila, int columna, String valor, CellStyle estilo) {
        Cell c = fila.createCell(columna);
        c.setCellValue(valor);
        c.setCellStyle(estilo);
    }

    private static void numero(Row fila, int columna, int valor, CellStyle estilo) {
        Cell c = fila.createCell(columna);
        c.setCellValue(valor);
        c.setCellStyle(estilo);
    }

    private static void dinero(Row fila, int columna, BigDecimal valor, CellStyle estilo) {
        Cell c = fila.createCell(columna);
        c.setCellValue(valor.doubleValue());
        c.setCellStyle(estilo);
    }

    /**
     * Los estilos del libro, creados una sola vez.
     *
     * <p>Un {@code CellStyle} por celda agota el límite de 64.000 estilos de
     * XLSX y abulta el archivo; con 35 ítems no reventaría, pero el tope de
     * 300 ítems por solicitud sí lo haría si cada fila creara los suyos.
     */
    private static final class Estilos {
        private final CellStyle titulo;
        private final CellStyle objeto;
        private final CellStyle encabezadoTabla;
        private final CellStyle celda;
        private final CellStyle celdaCentrada;
        private final CellStyle descripcion;
        private final CellStyle moneda;
        private final CellStyle monedaTotal;
        private final CellStyle monedaEnRojo;
        private final CellStyle avisoEnRojo;
        private final CellStyle firma;

        private Estilos(XSSFWorkbook libro) {
            // El formato de moneda es el del archivo real: pesos con separador
            // de miles y dos decimales. Lo interpreta Excel, no SICOT: así el
            // contratista puede sumar la columna en su propia hoja.
            String formatoPesos = "\"$\" #,##0.00";
            short pesos = libro.createDataFormat().getFormat(formatoPesos);

            Font negrita = libro.createFont();
            negrita.setBold(true);
            Font blancaNegrita = libro.createFont();
            blancaNegrita.setBold(true);
            blancaNegrita.setColor(IndexedColors.WHITE.getIndex());
            Font roja = libro.createFont();
            roja.setBold(true);
            roja.setColor(IndexedColors.DARK_RED.getIndex());

            titulo = libro.createCellStyle();
            titulo.setFont(negrita);
            titulo.setAlignment(HorizontalAlignment.CENTER);

            objeto = libro.createCellStyle();
            objeto.setWrapText(true);
            objeto.setVerticalAlignment(VerticalAlignment.TOP);

            encabezadoTabla = libro.createCellStyle();
            encabezadoTabla.setFont(blancaNegrita);
            // Verde institucional del SENA, el mismo de los documentos que
            // genera SICOT (ver docs/formatos/README.md).
            encabezadoTabla.setFillForegroundColor(IndexedColors.GREEN.getIndex());
            encabezadoTabla.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            encabezadoTabla.setAlignment(HorizontalAlignment.CENTER);
            encabezadoTabla.setVerticalAlignment(VerticalAlignment.CENTER);
            encabezadoTabla.setWrapText(true);
            bordes(encabezadoTabla);

            celda = libro.createCellStyle();
            bordes(celda);
            celda.setVerticalAlignment(VerticalAlignment.TOP);

            celdaCentrada = libro.createCellStyle();
            bordes(celdaCentrada);
            celdaCentrada.setAlignment(HorizontalAlignment.CENTER);
            celdaCentrada.setVerticalAlignment(VerticalAlignment.TOP);

            descripcion = libro.createCellStyle();
            bordes(descripcion);
            descripcion.setWrapText(true);
            descripcion.setVerticalAlignment(VerticalAlignment.TOP);

            moneda = libro.createCellStyle();
            bordes(moneda);
            moneda.setDataFormat(pesos);
            moneda.setVerticalAlignment(VerticalAlignment.TOP);

            monedaTotal = libro.createCellStyle();
            bordes(monedaTotal);
            monedaTotal.setDataFormat(pesos);
            monedaTotal.setFont(negrita);

            monedaEnRojo = libro.createCellStyle();
            bordes(monedaEnRojo);
            monedaEnRojo.setDataFormat(pesos);
            monedaEnRojo.setFont(roja);

            avisoEnRojo = libro.createCellStyle();
            avisoEnRojo.setFont(roja);
            avisoEnRojo.setWrapText(true);

            firma = libro.createCellStyle();
            firma.setFont(negrita);
        }

        private static void bordes(CellStyle estilo) {
            estilo.setBorderTop(BorderStyle.THIN);
            estilo.setBorderBottom(BorderStyle.THIN);
            estilo.setBorderLeft(BorderStyle.THIN);
            estilo.setBorderRight(BorderStyle.THIN);
        }
    }

    /**
     * Los ítems por número de ítem. {@code saveAll} los devuelve en el orden en
     * que se insertaron, que es el del pegado, y el contratista los espera
     * numerados.
     */
    private static List<ItemSolicitudPedido> ordenados(List<ItemSolicitudPedido> items) {
        List<ItemSolicitudPedido> copia = new ArrayList<>(items);
        copia.sort((a, b) -> Integer.compare(a.getNumero(), b.getNumero()));
        return copia;
    }
}
