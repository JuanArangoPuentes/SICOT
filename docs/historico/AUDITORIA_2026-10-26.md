# Auditoría del repositorio — 26 de octubre de 2026

Esta es una **foto del 26 de octubre de 2026**, no documentación vigente. Lo
que aquí se da por corregido lo está desde ese día; lo que queda abierto puede
haberse resuelto después.

Entró por una queja concreta: *«siempre que descargo un documento y lo abro me
aparece con error»*. La pregunta de fondo era si la generación de documentos
estaba rota. **No lo está** — lo que estaba roto era la descarga de lo que ya
había guardado en el expediente.

## Qué se comprobó, y cómo

### La generación, con documentos de verdad

No se midió leyendo el código: se generaron **diez documentos** con el mismo
motor que usa el supervisor (`RedactorDeDocumentos` + `PdfInstitucional`,
a través de `GeneradorDeDocumentosDePruebaTest`) — seis actas de inicio y una
muestra de cada formato que SICOT arma — y después se les **estampó la firma**
con `EstampaDeFirma`, que es el PDF que de verdad se descarga.

A los veinte archivos (diez sin firmar, diez firmados) se les comprobó, uno a
uno, la estructura real del PDF: cabecera `%PDF-` en el byte cero, `startxref`
apuntando dentro del archivo, cada entrada de la tabla de referencias cruzadas
cayendo sobre el objeto que dice, y `%%EOF` al final.

| Comprobación | Resultado |
| --- | --- |
| 10 documentos generados, estructura del PDF | 10 correctos |
| Los mismos 10 con la firma estampada | 10 correctos |
| Representación de las páginas (PDFBox) | sin errores |
| Revisión visual del GCCON-F-031 firmado | el formato sale como el oficial |

El estampado de la firma añade ~12,8 kB por documento porque vuelve a
incrustar las fuentes en vez de reutilizar las que el PDF ya trae. No rompe
nada y no se tocó; queda anotado abajo.

### Los archivos que el propio Juan había descargado

La prueba que cerró el diagnóstico. Los PDF de SICOT que quedaron en su
carpeta de descargas, pasados por el mismo lector que usa su visor:

| Archivo | Tamaño | Veredicto |
| --- | ---: | --- |
| `Acta de Inicio firmada.pdf` (20-10) | 44 B | **no abre** — «Missing root object specification in trailer» |
| `Informe de supervision mensual - periodo 2.pdf` (25-09) | 0 B | **no abre** — vacío |
| `Informe de supervision mensual - periodo 2 (1).pdf` (25-09) | 0 B | **no abre** — vacío |
| `Acta de Inicio — CO1.PCCNTR.7788991.pdf` (25-09) | 2 677 B | abre, generado por SICOT |
| `Acta de Inicio — CO1.PCCNTR.7986334-UI28.pdf` (28-09) | 64 092 B | abre, generado por SICOT |
| `Evidencia del envío del acta.png` (20-10) | 70 B | abre: es un PNG válido de **1 × 1 píxel** |

Los dos que SICOT **generó** abren. Los que no abren son los que SICOT
**guardó de una carga**: el de 44 bytes es un texto cuya primera línea dice
`%PDF-1.4` y nada más, y los de 0 bytes son filas que se registraron sin
contenido.

## El hallazgo

SICOT ya rechaza al cargarlo un archivo que no se abre — se corrigió el
20-10-2026, después de que ese acta de 44 bytes entrara al expediente del
contrato de prueba el 15-10 (`ArchivoValidator.validarQueSeAbre`).

**Pero eso solo cerró la puerta de entrada.** Las filas que ya estaban dentro
siguieron ahí, y la descarga las servía tal cual: un 200 con bytes que ningún
lector abre. El supervisor bajaba el archivo, su visor lo daba por dañado y
nada le decía por qué ni qué hacer. La descarga comprobaba que hubiera
contenido, no que ese contenido sirviera.

### Lo que se corrigió

1. **La descarga de un documento del expediente** comprueba ahora que el PDF
   guardado se abra, con el mismo lector que usará quien lo reciba. Si no se
   abre, responde 404 con el motivo y qué hacer, en vez de entregar bytes
   inservibles.

   Dos cosas quedan fuera de la regla, las dos a propósito:

   - **Un documento ALTERADO se entrega igual**, con la cabecera diciéndolo.
     Que sus bytes ya no coincidan con lo firmado es un hallazgo más grave y
     más preciso que «está dañado», y negar la descarga escondería la
     evidencia justo cuando hay que examinarla.
   - **Las fotos no se comprueban al descargarlas.** Medido el 26-10-2026:
     abrir un PDF cuesta 7-10 ms, porque PDFBox lee la tabla de objetos y no
     las páginas; descodificar una foto de 12 Mpx —las que manda cualquier
     teléfono— cuesta unos 300 ms. La evidencia fotográfica es lo que más se
     baja en el flujo del supervisor, y desde el teléfono: pagar eso en cada
     descarga para cazar una foto antigua ilegible no compensa. Al **cargar**
     sí se descodifica entera, que es donde compensa.
2. **La descarga del catálogo de formatos** no tenía ninguna de las dos redes
   —ni la de contenido vacío, que el expediente sí tenía desde el 28-09—. Se
   le pusieron las mismas.
3. **El guardado en el navegador** liberaba la dirección `blob:` en el mismo
   tic del clic. El clic solo le pide al navegador que empiece la descarga;
   leer los bytes viene después, y liberarla antes se los quita de debajo: el
   archivo sale vacío o a medias. Es exactamente la carrera que ya se había
   corregido en `abrirArchivo` —con el comentario que lo explica— y que en la
   descarga se había quedado sin corregir.

   Es un arreglo preventivo y conviene decirlo así: **no se reprodujo la
   carrera**, que por definición no se reproduce a voluntad; lo que hay es una
   prueba que fija que la dirección ya no se libera antes de tiempo. No afecta
   al APK, que no pasa por ahí: en Android se guarda con los complementos de
   Tauri, y los puntos 4 y 5 de `PRUEBA_MANUAL_APK.md` ya pasaban.

La comprobación «¿esto se abre?» vive en un solo sitio
(`ArchivoValidator.porQueNoSeAbre`, y `…AlEntregarlo` para el lado de la
descarga) y la usan los dos extremos del archivo, la carga y la entrega.

## Estado del resto del repositorio

| Comprobación | Resultado |
| --- | --- |
| Backend · compilación y suite completa | 992 pruebas, 0 fallos |
| Frontend · chequeo de tipos | correcto |
| Frontend · formato (`oxfmt`) | 131 archivos correctos |
| Frontend · pruebas | 343 pruebas en 39 archivos, 0 fallos |
| Frontend · compilación | correcta |
| MCP · compilación | correcta |
| `npm audit` producción (frontend y MCP) | 0 vulnerabilidades |
| `npm audit` árbol completo (MCP) | 0 vulnerabilidades |
| `npm audit` árbol completo (frontend) | 3 moderadas, todas en `vitest` y sus dependencias |

No se ejecutaron localmente **Trivy** (la compuerta real de dependencias, pide
Docker, que no estaba levantado) ni una prueba en vivo contra el entorno
completo, por lo mismo. Las dos corren en CI.

## Lo que queda abierto

- **Las filas que ya están rotas siguen en la base.** La corrección hace que
  SICOT lo *diga* en vez de entregar un archivo inservible, no que las repare:
  cada una hay que cargarla de nuevo, y si estaba firmada, generarla y
  firmarla otra vez. Se descubren al intentar descargarlas, porque comprobarlo
  en el listado obligaría a leer los bytes de todos los documentos del
  contrato — justo lo que la proyección de `DocumentoRepository` evita.

  Para verlas todas de una vez, sin ir una por una, esta consulta saca las
  **candidatas** (es una heurística por tamaño y cabecera; la comprobación de
  verdad es intentar la descarga, que ahora responde con el motivo):

  ```sql
  SELECT d.id, c.numero_contrato, d.nombre, d.tipo,
         length(d.contenido) AS bytes,
         d.firma_id IS NOT NULL AS firmado
    FROM documentos d
    JOIN contratos c ON c.id = d.contrato_id
   WHERE d.contenido IS NULL
      OR length(d.contenido) = 0
      OR (d.tipo = 'PDF' AND (length(d.contenido) < 500
          OR encode(substring(d.contenido FROM 1 FOR 5), 'escape') <> '%PDF-'))
      OR (d.tipo = 'IMAGEN' AND length(d.contenido) < 200)
   ORDER BY c.numero_contrato, d.id;
  ```
- **`vitest` 4.1.11 tiene 3 avisos moderados** (inyección de código por
  registro manual de *mocks*, y un salto del filtro con barras invertidas en
  Windows). Son de desarrollo, no entran al APK ni a la imagen, y la compuerta
  de CI es `high`, así que no bloquean. La versión corregida es la 5, un salto
  de versión mayor del motor de pruebas: no se hizo dentro de la auditoría.
- **El estampado de la firma vuelve a incrustar las fuentes** en lugar de
  reutilizar las que el PDF ya trae: ~12,8 kB más por documento firmado. Con
  un expediente de catorce documentos son unos 180 kB. No afecta a nada hoy.
- **Un `content_type` nulo o ilegible en una fila antigua** haría que el
  archivo se guardara sin extensión y Windows no supiera abrirlo. No se
  observó en ninguno de los archivos descargados, y las filas nuevas siempre
  llevan el MIME canónico; queda anotado por si aparece.
