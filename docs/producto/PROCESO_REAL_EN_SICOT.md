# Lo que hace SICOT en cada paso del proceso real

Estado al 22-10-2026, después de aplicar la lista de chequeo que Alex Zapata
documentó el 15-10 y completó el 20-10 ([LISTA_CHEQUEO_ALEX.md](LISTA_CHEQUEO_ALEX.md)). Es la
respuesta a sus columnas «PROCESO EN EL APLICATIVO» y «REQUERIMIENTOS», paso por
paso, y está escrita para que él la valide.

**Lo que cambió el 22-10-2026.** La hoja no se movió desde el 20-10 (se
comparó celda por celda) ni la carpeta tiene material nuevo. Lo que se hizo fue
cerrar lo que la hoja ya pedía y SICOT todavía no hacía: las **fechas de entrega
acordadas** con el contratista (paso 5), los **profesionales de SST y ambiental**
guardados con el contrato (paso 3), el **teléfono del contratista** desde el
registro de proveedores del SECOP II (paso 5) y el **enlace para descargar
SICOT** en el aviso de designación (paso 1).

Todo lo de abajo se probó de punta a punta con los documentos reales del
contrato **CO1.PCCNTR.10057820** (Lote 4, Suministros Maybe S.A.S): el acto de
designación GCCON-F-032 y el acta de inicio firmada.

## Cómo quedó el recorrido del contrato

El contrato tiene 6 etapas. Las dos primeras son ahora el proceso real; las
otras cuatro siguen el procedimiento GCCON-P-010 hasta que Alex documente esa
parte.

| Etapa | Sub-pasos | Quién |
|---|---|---|
| 1. Designación de la supervisión (GCCON-F-032) | 1.1 Contrato publicado en SECOP II · 1.2 Designación del supervisor y del apoyo · 1.3 Registro en SICOT y aviso al supervisor | Contratación, ordenador del gasto y apoyo a la supervisión |
| 2. Acta de inicio y arranque | 2.1 Firma y devolución del acta · 2.2 Aviso a SST y ambiental · 2.3 Verificación en SECOP II · 2.4 Contacto con el contratista | Supervisor |
| 3 a 6 | Inspección, recepción, certificación y cierre (sin cambios) | Supervisor |

Antes eran 27 sub-pasos sacados de los manuales; ahora son 21. La etapa 1 ya no
le pide al supervisor marcar trabajo que no es suyo: nace completa cuando el
contrato llega con supervisor, y él empieza directamente en el acta.

## Paso 1 — Designación de supervisión

**Lo que pide Alex.** El apoyo a la supervisión entra como GESTOR, carga el acto
de designación, el sistema extrae de él el enlace del contrato en el SECOP y
toda la información, y avisa al supervisor que fue designado.

**Lo que hace SICOT hoy.**
- Del acto de designación lee: número de contrato, contratista, objeto, **enlace
  del proceso en el SECOP II**, supervisor, **apoyo a la supervisión** y **fecha
  de la designación**. Con el acto real del Lote 4 tarda unos 4 segundos.
- La designación no trae valor, plazo ni fechas. SICOT los pide a los **datos
  abiertos del SECOP II** con el número del contrato y le dice al gestor qué datos
  vinieron de ahí. Lo leído del documento siempre gana.
- Si además se carga el acta de inicio, salen también la fecha de inicio, el
  registro presupuestal, el lugar y el **correo del contratista**: los 16 datos
  en menos de un segundo.
- Si el supervisor nombrado en la designación es usuario de SICOT, queda
  preseleccionado.
- Al registrar el contrato, el supervisor recibe en su bandeja el aviso: «Fue
  designado supervisor del contrato … Su primer paso: firmar y devolver el Acta
  de Inicio que le envía Contratación, y cargarla aquí con la evidencia del
  envío». El mismo aviso sale por correo cuando el servidor de correo esté
  configurado.
- El **teléfono del contratista**, que la hoja pide junto al correo y ningún
  documento del contrato trae, sale del **registro de proveedores del SECOP II**
  (datos abiertos, con el NIT): para Suministros Maybe, 4489948. Se completa al
  registrar el contrato, y el formulario dice que vino de ahí.
- **Desde el 22-10-2026 el correo lleva el enlace para descargar SICOT**, como
  pide la hoja: la última versión publicada (instalador de Windows y APK de
  Android). Y como el instalador es uno solo para todos los Centros y no trae
  dentro la dirección del servidor, el correo le dice también cuál escribir en
  Configuración al abrirlo por primera vez (en producción sale sola de
  `SICOT_DOMINIO`). Quien ya tiene SICOT instalado lee en el mismo correo que el
  contrato nuevo aparece en el selector de su panel.

**Cómo quedó la comunicación entre los dos sistemas.** La hoja pide que el del
gestor y el del supervisor estén «descargados de forma LOCAL, pero se transfieren
información por la web». Así funciona hoy: el instalador es el mismo para los
dos, cada uno entra con su rol, y los dos hablan con el servidor de SICOT del
Centro. Lo que carga el gestor le aparece al supervisor en cuanto abre la
aplicación con conexión. **Lo que no hace es trabajar sin conexión**: si en ese
momento el supervisor no tiene internet, no ve lo nuevo hasta que la tenga. Que
eso baste o no es la pregunta 1 de abajo; sincronizar sin conexión es mucho más
trabajo, y no se construye sin saber que hace falta.

**Respuesta a su pregunta** («¿qué pasa si el supervisor tiene instalado el
sistema y realizará un nuevo contrato?»): el contrato nuevo se suma a los que ya
tiene. El supervisor elige en qué contrato trabaja desde un selector en la parte
superior del panel, y SICOT recuerda su elección. El aviso de designación se lo
dice así.

## Paso 2 — Firma del acta de inicio

**Lo que pide Alex.** El paso queda esperando hasta que el supervisor confirma
que devolvió el acta firmada; carga el acta firmada y la evidencia del envío; y
el sistema le explica qué se hace en este paso.

**Lo que hace SICOT hoy.**
- **SICOT ya no genera el acta de inicio.** La elabora Contratación, como en la
  realidad. El paso 2.1 tiene dos casillas: el acta firmada (PDF) y la evidencia
  de que se devolvió (el pantallazo o el PDF del correo respondido).
- El paso **no se puede cerrar** hasta tener las dos. Eso lo exige el servidor,
  no solo la pantalla.
- El Copiloto explica qué hacer: firmar el acta, devolverla respondiendo el
  correo de Contratación y cargar las dos cosas.

**Probado:** con el acta real del Lote 4 cargada y sin evidencia, el sistema no
deja cerrar el paso y dice qué falta. Con las dos cargadas, el paso cierra y el
Copiloto pasa al 2.2.

## Paso 3 — Obligaciones de SST y ambientales

**Lo que pide Alex.** Identificar a los profesionales de SST y ambiental (sus
contactos están en el estudio previo, en el SECOP) y escribirles que el
contrato inició.

**Lo que hace SICOT hoy.**
- **Guarda a los profesionales de SST y ambiental con el contrato** (desde el
  22-10-2026). El supervisor los registra una vez en el paso 2.2: rol, nombre si
  lo tiene, y correo.
- **Los busca en el estudio previo.** El supervisor lo abre en el SECOP con el
  enlace del paso 2.3, lo descarga y lo carga en el paso; SICOT le propone los
  correos que encuentra, con el fragmento donde aparece cada uno (para leer el
  nombre) y el rol que sugiere el texto de alrededor: la mención de SST o de
  ambiental más cercana antes del correo. **Es una sugerencia**: el supervisor
  elige con un botón. El archivo no se guarda —el estudio previo ya está en el
  SECOP— y un PDF escaneado se explica en vez de callar. El formato del estudio
  previo del Centro no lo conocemos (el del Lote 4 no se puede bajar desde un
  programa), así que la lectura no depende de la posición de ninguna tabla.
- Arma el borrador del correo con los datos reales del contrato (número,
  contratista, objeto y vigencia), **ya dirigido a ellos**, para que el
  supervisor lo envíe desde su correo.
- **Cumple lo que ese correo promete.** Decía «les compartiré las fechas de
  entrega en cuanto queden acordadas»; ahora, cuando quedan registradas en el
  paso 2.4, SICOT arma el segundo correo con esas fechas (ver paso 5), y si ya
  estaban cuando se escribe el primero, el primero las lleva.
- Si el SECOP registra **obligación ambiental**, el paso 2.3 lo señala.

**Pendiente.** Comprobar la lectura con un estudio previo real del Centro: la
del Lote 4 se probó con un documento armado con la misma forma (cargo, nombre y
correo), no con el original.

## Paso 4 — Verificar las especificaciones del contrato

**Lo que pide Alex.** Con el enlace del SECOP que trae la designación, verificar
cantidades de suministros, especificaciones y valores.

**Lo que hace SICOT hoy.**
- Guarda el enlace del proceso y lo ofrece en la ficha del contrato y en el paso.
- El sitio del SECOP no se puede leer desde un programa (pide un reCAPTCHA),
  pero parte de la información está en los **datos abiertos del SECOP II**, que
  son públicos y gratuitos. SICOT trae de ahí: estado del contrato, valor,
  plazo, fechas de firma, terminación y liquidación, obligación ambiental, lo
  pagado y lo pendiente, y **las pólizas**. Del Lote 4 vienen la ficha y dos
  pólizas.
- Desde el 22-10-2026 muestra también **cómo está inscrito el contratista en el
  registro de proveedores** del SECOP II: teléfono y correo, que es lo que pide
  la hoja. El registro publica además la dirección y el correo y el teléfono
  personales del representante legal, y nada de eso se lee: cuando el
  contratista es una persona natural, son su domicilio y sus datos personales.
  El registro es texto libre («604.440.9977», «2120777 ext 1», «0»): SICOT
  normaliza lo que se puede usar y descarta lo que no es un teléfono.
- Compara con lo registrado en SICOT y **señala lo que no coincide**. En el Lote
  4, todo coincide.
- La verificación sigue siendo del supervisor: SICOT no decide si una diferencia
  es un error.

**Corrección al corte del 15-10 (comprobada el 16-10-2026).** Ese corte decía
que SICOT trae también «el índice de los documentos del expediente». **No lo
trae, y no puede.** El conjunto de datos de archivos del SECOP II
(`dmgg-8hin`, «Archivos Descarga Desde 2025») tiene 1.000 filas y ninguna
cargada después del **30-12-2021**: para el contrato del Lote 4 y para su
proceso `CO1.BDOS.10711126` devuelve cero, y los otros conjuntos de archivos
—histórico, 2022, 2023 y 2024— tampoco lo tienen. Además, el enlace de descarga
que publicarían (`RetrieveFile/Index?DocumentId=…`) responde **403** a un
programa. El panel escondía la sección cuando venía vacía, de modo que el paso
2.3 no decía nada de los documentos y el 2.4 le pedía al supervisor copiar las
cantidades de la oferta económica sin decirle de dónde sacarla. Ahora el paso
2.3 dice por qué no hay índice y lo remite al **enlace del proceso**, que es la
vía real; la consulta se mantiene —corregida, porque el expediente se archiva
bajo el proceso de compra y no bajo el número de contrato— para los contratos
antiguos que sí están publicados.

**Y una corrección a la corrección (23-10-2026).** El mismo conjunto de datos
**sí trae ahora el expediente del Lote 4**: 110 archivos del proceso
`CO1.BDOS.10711126` —los de los 9 lotes—, entre ellos «6. ESTUDIO PREVIO.pdf»
(5,4 MB) y «8. ECONOMICA LOTE 4 AJUSTADA.pdf». No estaba muerto: publica la lista
días después que la ficha. El paso 2.3 ya los muestra, con el enlace de cada uno
para abrirlo en el navegador, y el aviso de cuando no hay lista dice eso en vez
de «no se actualiza desde 2021». Lo que no cambió: el enlace de descarga
responde 403 a un programa —el SECOP filtra lo que no es un navegador— y SICOT
no se hace pasar por uno. Por eso el estudio previo lo abre el supervisor y lo
carga en el 2.2.

**Pendiente.** Las **cantidades y especificaciones** están en la oferta
económica («8. ECONOMICA LOTE 4 AJUSTADA.pdf») y en el estudio previo, y hay que
abrirlas en el SECOP a mano. SICOT no las lee, y por el 403 no puede bajarlas
del SECOP: la vía posible es que el supervisor **cargue esos dos PDF** en el
paso, como ya hace con el acta firmada, y que SICOT los lea. Desde el 22-10-2026
ese puente existe para el **estudio previo**: el supervisor lo carga en el paso
2.2 y SICOT lee de él los correos de SST y ambiental (ver paso 3). Para la
**oferta económica** todavía no: leer la tabla de cantidades exige conocer su
formato, y la del Lote 4 no la tenemos.

Desde el 21-10-2026 las cantidades sí tienen dónde vivir, aunque se sigan
transcribiendo: el supervisor las **pega** en la solicitud de pedido del paso 5,
y de ahí sale el Excel que se le adjunta al contratista. Lo que falta es que
SICOT las lea del PDF, no que el dato se pierda.

## Paso 5 — Contacto al contratista

**Lo que pide Alex.** Escribirle al contratista (su correo está en el acta de
inicio) para presentarse y acordar las entregas; en suministros, con las
cantidades exactas.

**Lo que pidió además el 20-10-2026.** Que el correo lleve adjunto «un formato
con el listado de partes y suministros a recibir, […] un documento de excel», y
dejó el formato que usa hoy: `Solicitud_Maybe.xlsx`, la solicitud de pedido del
Lote 4. El «cómo será este proceso y dónde se encontrará esta documentación» lo
dejó por definir.

**Lo que hace SICOT hoy.**
- Toma el correo del contratista del acta de inicio.
- Arma el borrador dirigido a él, con el contrato, la vigencia y el
  representante legal, listo para enviarse desde el correo del supervisor.
- **Arma la solicitud de pedido y la entrega en Excel**, con la estructura del
  formato real: encabezado con el contratista y el Centro, el objeto, la tabla
  (ITEMS · DESCRIPCIÓN · CANTIDAD · VALOR UNITARIO CON IVA · VALOR TOTAL CON
  IVA), el bloque de presupuesto, el subtotal y la firma del supervisor. El
  borrador del correo ya no deja el hueco de las cantidades: anuncia el adjunto.
- **El control de presupuesto es la parte que no es solo una tabla.** ASIGNADO
  es el valor del contrato, COMPROMETIDO la suma del pedido y QUEDA la
  diferencia, como en el archivo de Alex, donde de $45.900.300 quedaban $863,58.
  Si el pedido se pasa, se dice en la pantalla y en rojo dentro del archivo,
  pero no se bloquea: el supervisor puede estar a mitad de cargar la tabla, y es
  él quien sabe si lo que no cuadra es una cantidad o el valor registrado.

**Cómo será el proceso, que era lo que quedaba por definir.** Los bienes se
**pegan** desde la oferta económica adjudicada, que el supervisor ya tiene
abierta, y quedan guardados con el contrato. No los puede traer SICOT por lo del
paso 4 (el expediente del SECOP II no es alcanzable desde un programa), y
capturar 35 ítems con fichas técnicas de 900 caracteres no lo haría nadie dos
veces. SICOT lee lo pegado tolerando lo que sale de una hoja de cálculo —«$»,
separador de miles, el encabezado y el subtotal, la columna del total— y dice
cuántas filas reconoció antes de guardar nada. El archivo se genera cada vez que
se pide y no se guarda: uno almacenado quedaría viejo en cuanto se corrija una
cantidad.

**El teléfono del contratista (22-10-2026).** La hoja dice que en el acta debe
reposar «un correo de contacto + número de teléfono», y el acta del Lote 4 no
trae teléfono. SICOT no inventa uno: lo toma del **registro de proveedores del
SECOP II**, con el NIT. Los contratos nuevos lo traen desde el registro; en los
que ya estaban, el paso 2.4 muestra el correo y el teléfono del contratista y,
si falta el teléfono, un botón para traerlo. Solo llena lo vacío: el correo del
acta nunca se reemplaza por el del registro.

**Las fechas de entrega acordadas (22-10-2026).** La hoja dice que en este paso
«se establecen fechas de entrega o la prestación del servicio, las fechas se
definen de común acuerdo con el contratista respetando los tiempos de inicio y
fin del contrato». Hasta ahora SICOT armaba el correo que propone acordarlas,
pero el acuerdo no quedaba en ninguna parte y el paso se cerraba sin una sola
fecha. Ahora:
- Debajo del correo y del Excel, **«Fechas de entrega acordadas»**: el
  supervisor registra cada fecha con lo que se entrega ese día («entrega
  total», «ítems 1 a 20»). SICOT las numera por fecha.
- **Respetar el plazo lo exige el servidor**, no solo la pantalla: una fecha
  antes del inicio o después del fin del contrato se rechaza diciendo cuál y
  cuál es el plazo (para entregar después del fin hace falta una prórroga).
- **El paso 2.4 no se cierra sin al menos una fecha**, igual que el 2.1 no se
  cierra sin el acta. Se aplica también a los contratos que ya tenían el 2.4
  abierto; los que ya lo habían cerrado quedan como estaban.
- **SICOT le recuerda cada entrega** en la bandeja: tres días antes —para
  preparar la verificación y recordarles la fecha a SST y ambiental— y el mismo
  día. Dos entregas el mismo día van en un solo aviso.
- Con las fechas guardadas aparecen **dos borradores**: la confirmación de las
  fechas al contratista y el aviso a SST y ambiental (el que prometía el correo
  del paso 3).
- Queda constancia en el expediente cada vez que el acuerdo cambia.

**Pendiente.** Que SICOT lea las cantidades de la oferta económica en vez de que
se peguen: depende de que el supervisor cargue el PDF, como ya hace con el
estudio previo para los contactos, y de conocer el formato de la tabla de la
oferta, que no tenemos. Y la inspección (etapa 3) todavía no marca qué entrega
acordada se recibió: esa etapa sigue el manual hasta que Alex la documente.

## Lo que hay que preguntarle a Alex

1. **Dos sistemas locales:** ¿la información del gestor tiene que llegarle al
   supervisor aunque esté sin internet en ese momento?
2. **Cuántos contratos** lleva a la vez un supervisor, para dimensionar el
   selector (hoy funciona con cualquier número).
3. **Los pasos siguientes** (entregas, pagos, informe final, liquidación): la
   lista llega hasta el contacto con el contratista.
4. **Las pantallas del SECOP** por dentro, para saber qué se publica en cada
   momento del contrato.
5. **La plantilla vigente del GCCON-F-018** solo hace falta si el sistema vuelve
   a generar el acta; con el proceso real, la recibe.
6. **El estudio previo** de un contrato del Centro, para comprobar que la
   lectura de los correos de SST y ambiental funciona con el documento real.
7. **Los recordatorios de las entregas** (tres días antes y el mismo día): si le
   sirve otro momento, o si cada entrega debería ir por ítems de la solicitud de
   pedido y no con una descripción libre.

## Notas para quien mantenga esto

- La migración es la **V20**: la rama `integracion/auditoria-2-oct` trae una
  V19 propia (versión de credenciales). Conviene fusionar esa rama primero; si
  una base recibe la V20 antes que esa V19, Flyway no aplicará la V19 después.
- En el perfil de desarrollo, Flyway tolera migraciones aplicadas por otras
  ramas, porque la base de desarrollo es compartida. En producción no.
- Los contratos creados antes del 15-10-2026 conservan sus 27 sub-pasos.
- La **V22** (22-10-2026) crea las fechas de entrega acordadas, los contactos de
  SST y ambiental y el teléfono del contratista, y le pone al 2.4 la marca
  `ENTREGAS_ACORDADAS`; la **V23** se la pone también a los 2.4 que se cerraron
  antes, por si se reabren. Ninguna otra rama usa la V21, la V22 ni la V23.
- **Orden de despliegue.** El 2.4 de los contratos abiertos pasa a exigir fechas
  registradas, y la aplicación de escritorio o Android instalada no se
  actualiza sola: con la 0.5.0 el supervisor no ve «Fechas de entrega
  acordadas» y el paso le queda trabado (el mensaje le dice que actualice).
  Publicar la versión nueva del cliente antes o junto con este backend, para
  que el enlace del aviso (`releases/latest`) la traiga.
- La edición de un contrato desde Gestión (`PUT /api/contratos/{id}`) ya no
  reemplaza el correo ni el teléfono del contratista si no vienen: también los
  llena el supervisor, y una lista abierta antes no debe borrarlos.
