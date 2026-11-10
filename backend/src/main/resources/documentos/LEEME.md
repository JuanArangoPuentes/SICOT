# Recursos de los documentos formales

Lo que usan `PdfInstitucional` y `HojaActaDeRecibo` para que los documentos
que genera SICOT se vean como los formatos oficiales del SENA, y de dónde sale
cada cosa. El detalle de cada formato está en `docs/formatos/README.md`.

## Logos

Se sacaron de los archivos originales de Word y Excel que usa el Centro, no de
los PDF: en los PDF vienen reducidos a unos 120 px y se veían pixelados al
imprimir.

- `logo-sena-verde.png` — verde #39A900, 243×230 px. Es el del encabezado del
  GCCON-F-031 (`word/media/image1.png` de su `.docx`). La hoja GIL-F-010 trae la
  misma imagen, y el GCCON-F-030 la trae reducida.
- `logo-sena-acta-de-inicio.png` — verde #00AF00, 359×351 px. Es la otra
  versión del logo, la que trae el `.docx` del GCCON-F-018
  (`word/media/image2.png`). Cada formato usa la suya.
- `logo-sena-negro.png` — la imagen del Acta de Inicio con el mismo canal alfa,
  en negro. El certificado del supervisor para el pago («ESUCON» en el CTMA)
  usa el logo monocromo; se deriva de este para no depender de la copia de
  110 px que trae ese certificado escaneado.

## Fuentes

Los formatos oficiales están hechos con **Calibri** (GCCON-F-018, GCCON-F-030,
GCCON-F-031 y las barras de la GIL-F-010) y **Arial** (el certificado ESUCON y
los datos de la GIL-F-010). Ninguna de las dos se puede redistribuir, y SICOT
no puede depender de software de pago. Se usan sus equivalentes libres, que
tienen las mismas métricas —cada letra ocupa lo mismo—, así que un renglón
parte donde partiría en Word:

- **Carlito** en lugar de Calibri.
- **Liberation Sans** en lugar de Arial.

Ambas están bajo la SIL Open Font License 1.1 (ver `fuentes/OFL.txt`), que
permite incrustarlas en los PDF y distribuirlas con el programa.

Se incrustan en cada PDF, solo con los caracteres usados. Además de dar el
aspecto correcto, eso resolvió dos defectos de la Helvetica estándar que se
usaba antes: el PDF se veía distinto según el visor de cada quien, y cualquier
carácter fuera de Latin-1 (una comilla tipográfica, un «–», un «€») se
cambiaba por «?» en el documento firmado.

Las ligaduras de Carlito («ti», «fi»…) se desactivan al cargarla
(`FuentesDelDocumento`). Con ellas el dibujo salía bien, pero la capa de texto
del PDF perdía trozos de palabra: el buscador, el copiar y pegar y la
extracción de SICOT leían «-oquia» donde decía «Antioquia».
