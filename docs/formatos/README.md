# Formatos oficiales que genera SICOT

SICOT genera cinco documentos formales para el supervisor. Desde el 28-09-2026
cada uno reproduce el formato oficial del SENA que el Centro Tecnológico del
Mobiliario usa de verdad: mismo encabezado, mismas tablas, mismos textos fijos,
mismo pie con código y versión. Este documento dice de dónde sale cada cosa,
para que quien toque `RedactorDeDocumentos`, `PdfInstitucional` o
`PlantillaDocumentoIA` no tenga que volver a medir los originales.

## Fuente de verdad

Los formatos se midieron sobre los archivos de la carpeta «Documentos/SICOT»
del equipo del proyecto (y los `.docx` originales cuando existían):

| Documento en SICOT | Formato | Versión | Archivo de referencia |
|---|---|---|---|
| Acta de Inicio | GCCON-F-018 | V.04 | `2Acta de inicio.docx` / `.pdf` (CO1.PCCNTR.7986334, diligenciado) |
| Informe de Supervisión | GCCON-F-031 | V04 | `GCCON-F-031_Informe_de_supervision_(Unificado_ByS_y_PDS).docx` (plantilla en blanco) |
| Acta de Recibo a Satisfacción | GIL-F-010 | 08 | `GIL-F-010FormatoActaReciboaSatisfacciondeBienes(2).xlsx` (diligenciado) |
| Certificación de cumplimiento | «ESUCON» (sin código) | — | `2. Certificado Formato Supervisor - ESUCON.pdf` y `(1).pdf` (dos contratos) |
| Informe Final de Supervisión | GCCON-F-030 | V05 | `GCCON-F-030 Informe Final CO1.PCCNTR.8426076 del 2025.pdf` (diligenciado) |

Si el Centro publica una versión nueva de un formato, lo que cambia está en un
solo sitio: la entrada de `PlantillaDocumentoIA.CATALOGO` (código, versión,
pie, márgenes, logo) y el método de `RedactorDeDocumentos` que arma su
contenido.

## Lo que comparten los GCCON (F-018, F-030, F-031)

- **Página:** carta, márgenes de Word de 2,5 cm a los lados (70,9 pt) y 3 cm
  arriba (85,05 pt). El logo va en el encabezado de Word a 1,25 cm del borde,
  así que se repite en todas las páginas.
- **Letra:** Calibri en Word. SICOT incrusta **Carlito**, que tiene las mismas
  métricas: cada renglón parte donde partiría en Word. El interlineado es el
  «1,15» de Word: 14,04 pt a 10 pt, 15,44 pt a 11 pt.
- **Clasificación de la información:** siempre «Pública» marcada, porque estos
  documentos se publican en el SECOP II.
- **Tablas:** filetes negros de ½ pt, sin relleno en los datos, encabezados de
  columna en gris #D9D9D9 y negrita.
- **Pie:** solo el número de página a la derecha (sin «Página X de N») y debajo
  el código con su versión, copiado con la puntuación de cada formato:
  «GCCON-F-018 V.04», «GCCON-F-031 V04», «GCCON-F-030 V05».

## Decisiones propias de cada formato

**GCCON-F-018 Acta de Inicio.** El logo está anclado al margen izquierdo en el
`.docx` (no centrado) y se respeta. El valor se escribe como en el acta real,
«DIEZ MILLONES DE PESOS ($10.000.000 COP)», y las fechas en letras («17 de
junio de 2025»). El texto narrativo se copia tal cual, con el «cedula» sin
tilde del formato. Firman dos: supervisor a la izquierda y representante legal
a la derecha. El hueco de firma tiene tres renglones en vez de los dos del
original, para que la firma electrónica estampada se lea.

**GCCON-F-031 Informe de Supervisión.** El formato unifica bienes y servicios
con prestación de servicios personales, y su Generalidad 6 ordena eliminar lo
que no corresponda. SICOT supervisa suministros y compras, así que genera solo
el bloque de **bienes y servicios**: sin portada, sin «Generalidades» y sin la
parte de servicios personales. Las orientaciones en rojo se reemplazan por el
dato o por «[dato pendiente…]». Sin interventoría, que es lo normal en mínima
cuantía, el párrafo de multas omite la referencia a su informe. El numeral 6
dice «No aplica», como indica su nota interna para informes de pago.

**GIL-F-010 Acta de Recibo.** Es una hoja de Excel, no un documento de Word:
se dibuja casilla por casilla (`HojaActaDeRecibo`) con la geometría medida en
la impresión real (Excel la imprime al 55 %). SICOT la dibuja al 60 % y
centrada, con la misma geometría y la letra algo más legible. El texto que no
cabe en su casilla se reduce de letra hasta 4,5 pt antes de salirse. Se
conserva «SATISFACCION» sin tilde, que es texto fijo del formato.

**Certificación de cumplimiento (ESUCON).** No tiene código oficial confirmado
y no se le inventa uno. Usa el logo negro, Arial (**Liberation Sans**) de
12 pt, no tiene pie de página, y el primer numeral va sin número ni «Que»,
como en los dos certificados reales. La tabla de imputación presupuestal se
sale de los márgenes en el original (de −2,4 a 614,4 pt); aquí va centrada de
18 a 594 pt para que no se corte al imprimir.

**GCCON-F-030 Informe Final.** En el ejemplar del que se tomó el modelo, el
número 3 se repite («3. ASPECTOS LEGALES» y «3. OBLIGACIONES DE LA ENTIDAD»).
SICOT numera seguido (3, 4 y 5) sin cambiar ningún título. La tabla de estado
financiero conserva su ancho total, pero la columna VALOR es un poco más ancha
para que «$20.000.000,00» en negrita quepa en un renglón. El apartado
«CONCLUSIÓN» que SICOT inventaba antes no existe en el formato y se quitó.

## Lo que SICOT añade, y lo dice

- Una línea pequeña y gris al pie, «Generado en SICOT el dd/mm/aaaa». Si el
  Copiloto redactó las observaciones, también lo dice.
- Los datos que el contrato no tiene (factura, póliza, cédulas…) se piden al
  supervisor antes de generar (`GET /api/ia/plantillas` dice cuáles pide cada
  formato). Los que deje vacíos salen como «[dato pendiente: …]» en **rojo**,
  que es la convención de los propios formatos para lo que falta diligenciar
  (GCCON-F-031, Generalidad 9).
- Al firmar, un recuadro con «Firmado electrónicamente en SICOT», el nombre,
  el código de la firma y la fecha y hora, en el hueco de firma que el PDF
  reservó (propiedad `SICOT-AnclaFirma`). La huella SHA-256 se calcula después
  de estampar, así que la verificación de integridad sigue valiendo.

## Recursos

Logos y fuentes están en `backend/src/main/resources/documentos/` con su
procedencia en `LEEME.md`. Las fuentes son libres (SIL OFL 1.1), sin costo
de licencia.
