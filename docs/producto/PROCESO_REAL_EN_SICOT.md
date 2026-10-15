# Lo que hace SICOT en cada paso del proceso real

Estado al 15-10-2026, después de aplicar la lista de chequeo que Alex Zapata
documentó ese mismo día ([LISTA_CHEQUEO_ALEX.md](LISTA_CHEQUEO_ALEX.md)). Es la
respuesta a sus columnas «PROCESO EN EL APLICATIVO» y «REQUERIMIENTOS», paso por
paso, y está escrita para que él la valide.

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

**Pendiente.**
- El aviso puede llevar el enlace para descargar SICOT, como pide Alex, pero
  todavía no hay un instalador publicado. En cuanto lo haya, basta con
  configurar `SICOT_ENLACE_DESCARGA_SUPERVISOR`; mientras tanto el correo no
  ofrece un enlace que no lleve a ninguna parte.
- El requerimiento de que **el sistema del gestor y el del supervisor sean dos
  instalaciones locales que se pasan la información por la web** no está
  construido. Depende de una respuesta de Alex: ¿la información tiene que
  llegarle al supervisor aunque en ese momento esté sin internet, o se puede
  dar por hecho que los dos están en línea? Lo primero exige sincronización
  sin conexión; lo segundo es mucho más simple.

**Respuesta a su pregunta** («¿qué pasa si el supervisor tiene instalado el
sistema y realizará un nuevo contrato?»): el contrato nuevo se suma a los que ya
tiene. El supervisor elige en qué contrato trabaja desde un selector en la parte
superior del panel, y SICOT recuerda su elección. Hasta hoy el panel mostraba
solo uno, y los demás quedaban fuera de alcance.

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
- Arma el borrador del correo con los datos reales del contrato (número,
  contratista, objeto y vigencia) para que el supervisor lo envíe desde su
  correo.
- Avisa dónde están los destinatarios: en el estudio previo, que SICOT lista
  con su enlace en el paso 2.3.
- Si el SECOP registra **obligación ambiental**, el paso 2.3 lo señala.

**Pendiente.** SICOT no conoce todavía los correos de SST y ambiental: están
dentro del estudio previo (un PDF de más de 5 MB en el SECOP). Por eso el
borrador sale sin destinatarios y lo dice.

## Paso 4 — Verificar las especificaciones del contrato

**Lo que pide Alex.** Con el enlace del SECOP que trae la designación, verificar
cantidades de suministros, especificaciones y valores.

**Lo que hace SICOT hoy.**
- Guarda el enlace del proceso y lo ofrece en la ficha del contrato y en el paso.
- El sitio del SECOP no se puede leer desde un programa (pide un reCAPTCHA),
  pero la misma información está en los **datos abiertos del SECOP II**, que son
  públicos y gratuitos. SICOT trae de ahí: estado del contrato, valor, plazo,
  fechas de firma, terminación y liquidación, obligación ambiental, lo pagado y
  lo pendiente, las pólizas y **el índice de los documentos del expediente**.
- Compara con lo registrado en SICOT y **señala lo que no coincide**. En el Lote
  4, todo coincide.
- La verificación sigue siendo del supervisor: SICOT no decide si una diferencia
  es un error.

**Pendiente.** Las **cantidades y especificaciones** están en la oferta
económica («8. ECONOMICA LOTE 4 AJUSTADA.pdf») y en el estudio previo. SICOT los
lista con su enlace para que el supervisor los abra, pero no los lee todavía.

## Paso 5 — Contacto al contratista

**Lo que pide Alex.** Escribirle al contratista (su correo está en el acta de
inicio) para presentarse y acordar las entregas; en suministros, con las
cantidades exactas.

**Lo que hace SICOT hoy.**
- Toma el correo del contratista del acta de inicio.
- Arma el borrador dirigido a él, con el contrato, la vigencia y el
  representante legal, listo para enviarse desde el correo del supervisor.
- En un suministro, el borrador deja el espacio para las cantidades exactas y
  avisa que hay que copiarlas de la oferta económica.

**Pendiente.** Las cantidades, por lo mismo del paso 4. Y el acta del Lote 4 no
trae teléfono del contratista, aunque la lista de Alex dice que debería: SICOT
no inventa uno.

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

## Notas para quien mantenga esto

- La migración es la **V20**: la rama `integracion/auditoria-2-oct` trae una
  V19 propia (versión de credenciales). Conviene fusionar esa rama primero; si
  una base recibe la V20 antes que esa V19, Flyway no aplicará la V19 después.
- En el perfil de desarrollo, Flyway tolera migraciones aplicadas por otras
  ramas, porque la base de desarrollo es compartida. En producción no.
- Los contratos creados antes del 15-10-2026 conservan sus 27 sub-pasos.
