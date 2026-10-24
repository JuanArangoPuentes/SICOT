# Lista de chequeo del proceso real — material de Alex Zapata

Alex Zapata (supervisor del contrato, `afzapatar@sena.edu.co`) está documentando el
proceso real de supervisión en una hoja de Google que **edita en vivo**. Este archivo
es la copia versionada de esa hoja: existe para que `git diff` muestre qué cambió
el cliente entre una revisión y la siguiente, porque la hoja no guarda historial
que podamos consultar desde aquí.

**No se edita a mano.** Lo reescribe la revisión periódica (tarea programada
`revisar-lista-chequeo-alex`) cada vez que detecta un cambio, y el commit
correspondiente explica qué movió Alex.

## Dónde vive el material

Carpeta compartida «MATERIALES PARA LA FORMACION»
(`1X1IFKwga8wNP6bmfHOCjJjPTRBgfApAO`), compartida el 15-10-2026.

| Archivo | Id | Subido | Qué es |
|---|---|---|---|
| `lISTA DE CHEQUEO PROCESO` | `1VvUiYqUvMjkjSxv49UCfWisxCuZsJBNIISztd8woZyM` | 14:48, editada 16:16 | La hoja viva. Es la fuente de requisitos. |
| `1-GCCON-F-032Designaciondelsupervisor (Lote 4).pdf` | `17DqAfEoIMB6czuinmpPQT-Mwl6UHrXAN` | 14:53 | Acto administrativo de designación. Entrada del paso 1. |
| `1. Acta de inicio (Lote 4)_signed.pdf` | `14N6O-lQl679m0m0HKSZvbUNWdNAdcG5_` | 14:56 | Acta firmada. Recurso del paso 2. |
| `enlace SECOP.png` | `1kB-DEfrEQmhd0NX-H2Rsex7sId3n8Iad` | 15:04 | Captura del F-032 con recuadro rojo sobre la fila «Link». |
| `Solicitud_Maybe.xlsx` | `1Ay3YMf8lj8JylnqyCnAni3S2FfExeDfL` | 20-10-2026 | **La solicitud de pedido del Lote 4**, el formato real que el supervisor le adjunta al contratista. Recurso del paso 5. |

> **Trampa conocida.** El conector de Google Drive **no indexa** esta carpeta: sus
> búsquedas solo devuelven la carpeta y la hoja. Los tres archivos restantes existen,
> cuelgan de ella y son invisibles para `search_files`; solo se alcanzan si ya se
> conoce su id. El 15-10-2026 eso costó declarar «no hay material nuevo» cuando sí
> lo había (`enlace SECOP.png`). **Para inventariar la carpeta hay que abrirla en el
> navegador**, no preguntarle al conector.

## El contrato del caso

Contrato `CO1.PCCNTR.10057820`, Lote 4 de un proceso de 9 lotes
(`SASI-ANT-CTM-001-2026`, selección abreviada por subasta inversa).
Contratista SUMINISTROS MAYBE S.A.S (NIT 800154801-6), representante legal
Luz Marina Mejía Pérez, `maybesuministros@gmail.com`. Valor $45.900.300,
plazo 67 días (14-10-2026 a 15-12-2026), registro presupuestal 123726.
Supervisor Alex Fernando Zapata Ríos; apoyo a la supervisión Yeison Dariel
Zapata Monsalve; ordenador Carlos Mario Castañeda Monsalve.
Enlace del proceso en SECOP II: `noticeUID=CO1.NTC.10815297` — **no es derivable**
del número de contrato, son identificadores distintos.

## Lo que cambió el 20-10-2026

Alex completó el **paso 5**, que el 15-10 solo tenía la columna «PROCESO»: le
agregó el recurso (`Solicitud_Maybe.xlsx`), el «PROCESO EN EL APLICATIVO» y los
«REQUERIMIENTOS». El resto de la hoja quedó igual, celda por celda. Eso es lo
que SICOT construyó el 21-10-2026 (ver [PROCESO_REAL_EN_SICOT.md](PROCESO_REAL_EN_SICOT.md),
paso 5).

## La hoja, al 20-10-2026 13:15

Columnas: PASO · ACTIVIDAD · PROCESO · RECURSO · PROCESO EN EL APLICATIVO ·
REQUERIMIENTOS · ESTADO. Las tres últimas no existían cuando Alex tomó la captura
que mandó por Slack a las 11:03; aparecieron después.

### Paso 1 — Designación de supervisión · ESTADO: EN PROCESO

**Proceso.** Llega correo al supervisor del contrato con un acto administrativo, y se
identifica número de contrato en el SECOP, VALOR, CONTRATISTA, DURACIÓN, TIPOLOGÍA
CONTRACTUAL Y OBJETO. // EL APLICATIVO EXTRAE DEL DOCUMENTO EL LINK DEL CONTRATO Y
TODA LA INFORMACIÓN PARA ALIMENTAR EL PROCESO // aquí también se especifica quien
será el apoyo a la supervisión.

**Recurso.** El PDF de designación (F-032).

**En el aplicativo.** El apoyo a la supervisión entra con el rol de GESTOR y carga el
documento de designación; el sistema recopila toda la información del contrato y
notifica al supervisor que ha sido designado. Al supervisor le llega además un correo
directamente del subdirector, que *no* lo manda el sistema. El sistema envía la
notificación con el enlace para descargar el sistema e iniciar la supervisión.

**Requerimientos.** Se debe comunicar el sistema del GESTOR con el sistema del
SUPERVISOR; **ambos deben estar descargados de forma LOCAL**, pero se transfieren
información por la web. Queda por definir cómo se comunican. Pregunta abierta de
Alex: ¿qué pasa si el supervisor ya tiene instalado el sistema y le llega un
contrato nuevo?

### Paso 2 — Firma del acta de inicio

**Proceso.** Se recibe un correo con el acta de inicio en PDF, se firma y se responde
al correo con el acta firmada.

**Recurso.** El acta firmada (F-018 **V.06**).

**En el aplicativo.** El sistema pasa a este paso y se queda esperando hasta que el
supervisor le confirme que ya devolvió el acta firmada. El supervisor debe cargar la
evidencia del envío y el acta firmada. El sistema debe explicarle al supervisor qué
se hace en este paso.

> Implicación directa: **el sistema no genera el acta de inicio, la recibe.** Hoy
> SICOT la genera, y además en la versión V.04.

### Paso 3 — Identificar obligaciones de SST y ambientales

**Proceso.** Identificar a los profesionales SST y ambiental y contactarlos; su
información de contacto está en el estudio previo, que reposa en el SECOP. Se les
escribe un correo informando que se dio inicio al contrato e invitándolos a las
entregas o ejecución de actividades según las obligaciones.

Sin recurso, sin proceso en el aplicativo, sin requerimientos.

### Paso 4 — Verificar las especificaciones del contrato

**Proceso.** Acceder al SECOP. En el PDF de designación va el enlace del contrato;
con ese enlace se alimenta el sistema. Luego verificar cantidades de suministros,
especificaciones y valores.

Sin recurso, sin proceso en el aplicativo, sin requerimientos.

### Paso 5 — Contacto a contratista

**Proceso.** En el acta de inicio debe reposar un correo de contacto y un número de
teléfono; se contacta por correo al contratista para presentarse y acordar el inicio,
y se establecen las fechas de entrega de común acuerdo respetando el inicio y fin del
contrato. Si es suministro o compras, en la preforma del correo se deben especificar
las cantidades exactas de los bienes a adquirir.

**Recurso** (agregado el 20-10-2026). `Solicitud_Maybe.xlsx`: la solicitud de pedido
del Lote 4. Encabezado con el contratista y el Centro, el objeto del contrato, el
proponente con el valor de su propuesta, y la tabla de 35 ítems —ITEMS · DESCRIPCIÓN ·
CANTIDAD · VALOR UNITARIO CON IVA · VALOR TOTAL CON IVA— con las fichas técnicas
completas de cada bien. A la derecha, el control de presupuesto: ASIGNADO
$45.900.300, COMPROMETIDO, QUEDA $863,58. Abajo, el subtotal ($45.899.436,42) y la
firma «ALEX FERNANDO ZAPATA — SUPERVISOR». Varios ítems van en cantidad 0: estaban en
la oferta y no se piden.

**En el aplicativo** (agregado el 20-10-2026). «Se debe generar una preforma de
correo, lo debe hacer el asistente, dónde se relaciona un formato con el listado de
partes y suministros a recibir, este será un documento de excel adjunto en el correo.»

**Requerimientos** (agregados el 20-10-2026). «1 - Crear preforma de correo.
2 - Generar y adjuntar el documento excel con la lista de materiales solicitado
(definir cómo será este proceso y dónde se encontrará esta documentación).»

> El acta de inicio **no trae teléfono del contratista**, solo el correo. Este paso
> asume un dato que el documento real no tiene.

## Lo que la lista todavía no cubre

El propio acto administrativo de designación enumera obligaciones que van mucho más
allá del paso 5: informes de supervisión, publicación en SECOP II, aprobación de
pagos, exigencia de aportes a seguridad social, informe final, certificación de
cumplimiento y acta de liquidación. La lista de Alex llega hasta «contacto a
contratista»; el resto del ciclo está sin documentar por el cliente.
