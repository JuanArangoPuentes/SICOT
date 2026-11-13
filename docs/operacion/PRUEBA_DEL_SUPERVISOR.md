# Prueba de SICOT con el supervisor

Hoja para acompañar una prueba de SICOT hecha por un supervisor de contratos
frente al equipo. Está escrita para leerse en voz alta o tenerla al lado: cada
prueba dice **qué hacer** y **qué debería pasar**, para que quien prueba sepa
si lo que ve está bien o está mal.

Preparada el **09-11-2026** sobre la **versión 0.9.0**.

---

## 1. Prender el servidor

1. Encienda el equipo **JUAN** (es el que tiene SICOT instalado).
2. Abra la carpeta `Downloads\Proyecto SICOT`.
3. Doble clic en **`Prender SICOT.bat`**.
4. Se abre una ventana negra que va contando lo que hace. Tarda entre uno y
   tres minutos la primera vez del día. **No la cierre** hasta que diga
   «SICOT ESTÁ FUNCIONANDO».

La ventana hace tres cosas que importan y que un arranque a mano no hace:

- **Espera a Docker.** Recién encendido el equipo, el motor donde corre SICOT
  tarda en estar listo; antes de eso cualquier intento falla sin explicar por qué.
- **Libera memoria.** Pregunta si cierra los programas que arrancan solos
  (Discord, Spotify, Teams, juegos). Conviene decir que sí: la IA necesita unos
  5 GB y, sin ellos cerrados, un informe que debería tardar 50 segundos puede
  tardar 4 minutos.

  > El equipo tiene 16 GB y el modelo ocupa casi 5. **Antes de la prueba
  > conviene cerrar también las ventanas de desarrollo y las pestañas del
  > navegador que no se usen**: eso es lo que más memoria devuelve. Si al
  > terminar el arranque la ventana dice que quedan menos de 6 GB libres, la
  > IA va a ir lenta.
- **Deja la IA cargada.** Así la primera respuesta no se lleva la espera de
  cargar el modelo.

Al final muestra la dirección para entrar. Al terminar la prueba, doble clic en
**`Apagar SICOT.bat`** (los datos no se borran).

### Para saber si está prendido

Doble clic en **`Estado de SICOT.bat`**. En unos segundos dice, pieza por
pieza, si SICOT está prendido, apagado o a medias, y vuelve a mostrar la
dirección para entrar. **Solo mira**: no prende ni apaga nada, no cierra
programas y no carga la IA.

Sirve para las tres dudas que aparecen en medio de una prueba, sin tener que
volver a prender:

- **«¿Quedó prendido?»**, al volver al equipo después de un rato.
- **«¿Cuál era la dirección?»**, si el portátil cambió de red.
- **«¿Por qué la IA va lenta?»**. Dice si el modelo sigue cargado en memoria o
  si salió de ella, que es la causa más común y no se ve en ninguna pantalla
  de SICOT. Si salió, `Prender SICOT.bat` lo vuelve a cargar.

### Si quiere probar desde otro computador

La ventana muestra también una dirección con números, del tipo
`http://192.168.x.x:8443`. Para que funcione hacen falta dos cosas:

- Que los dos equipos estén en la **misma red**. Ojo con la wifi del Centro:
  suele aislar los equipos entre sí y entonces no se ven por mucho que todo
  esté bien. Una zona wifi compartida desde un teléfono es más confiable.
- Que el cortafuegos de Windows **deje entrar por el puerto 8443 en esa red**.
  No es igual en todas: depende de si Windows tiene la red marcada como
  privada o como pública. En unas ya entra por la regla que Docker Desktop
  dejó al instalarse, y en otras hace falta una regla propia. No hay que
  adivinarlo: las dos ventanas (`Prender` y `Estado`) lo leen para la red del
  momento y lo dicen. Si falta, `Prender SICOT.bat` muestra la línea que hay
  que pegar en PowerShell **como administrador**, ya con el tipo de red que
  corresponde; es un cambio en la configuración del equipo, así que lo hace su
  dueño, no el programa.

La dirección con números **cambia cada vez que el portátil se conecta a otra
red**, y con la red puede cambiar también lo que deja pasar el cortafuegos. No
la escriba en un papel: léala de la ventana cada vez.

---

## 2. Entrar

Desde el mismo equipo: **http://localhost:8443**

Cuentas de prueba (son de desarrollo, no son de nadie real):

| Rol | Correo | Contraseña |
|---|---|---|
| Supervisor | `supervisor@soy.sena.edu.co` | `Supervisor123*` |
| Gestión | `gestion@soy.sena.edu.co` | `Gestion123*` |
| Administrador | `administrador@soy.sena.edu.co` | `Admin123*` |

La prueba de verdad es la del **Supervisor**: es el rol para el que se hizo
SICOT. Las otras dos sirven para ver de dónde salen los contratos y cómo se
vigila el avance.

---

## 3. El contrato con el que conviene probar

**`CO1.PCCNTR.7788991`** — Suministro de mobiliario escolar y de taller para
las aulas del Centro Tecnológico del Mobiliario.

> **Hay que elegirlo a mano.** Al entrar sale otro contrato. Arriba, en
> **«Contrato en supervisión»**, despliegue la lista y escoja el
> `CO1.PCCNTR.7788991`. Toda la pantalla cambia con él.

Es el que tiene historia detrás: las etapas 1, 2 y 3 cerradas, 17 de 27
subpasos completados (63 % de avance), una quincena de documentos y 10 avisos
de plazo, 2 de ellos críticos. Está parado en la **etapa 4 (Recepción),
subpaso 4.1 — Verificación de aportes a seguridad social (PILA)**, así que hay
un paso abierto para cerrar en vivo.

Los demás contratos de la lista están casi vacíos, o marcados «(borrador)» y
«(cancelado)»: sirven para ver cómo se ve uno que acaba de empezar, no para
recorrer el proceso.

También hay un botón **«Tutorial»** arriba a la derecha, por si prefiere que el
propio programa le explique el recorrido antes de empezar.

---

## 4. Las pruebas, en orden

> **Antes de empezar con la IA, deje pasar dos minutos.** Cuando se abre un
> contrato por primera vez, SICOT prepara por detrás el contexto de ese
> contrato para que el Copiloto responda rápido después, y eso ocupa la IA
> cerca de dos minutos. Si pregunta justo en ese momento, la respuesta puede
> tardar mucho o cancelarse. Aproveche para hacer antes las pruebas 1 y 2, que
> no usan IA.

### Prueba 1 — La bandeja de entrada
**Haga:** entre como Supervisor y mire la primera pantalla.
**Debería ver:** la lista de sus contratos y los avisos de plazo con colores de
semáforo. Los avisos **están calculados** con las fechas reales del contrato,
no puestos a mano.
**Mire si:** algún contrato que debería estar no aparece, o un color no
corresponde a lo que usted sabe del plazo.

### Prueba 2 — El paso a paso del contrato
**Haga:** abra el contrato `7788991` y recorra las seis etapas.
**Debería ver:** las etapas 1, 2 y 3 cerradas, la 4 en curso y las 5 y 6
pendientes. Cada subpaso se despliega y dice qué hay que verificar.
**Mire si:** el orden de las etapas, los nombres de los formatos
(GCCON-F-018, GCCON-F-031, GIL-F-010, GCCON-F-030) o lo que pide cada subpaso
no coincide con el procedimiento real.

> Este es el punto más importante de toda la prueba. El resto es software; esto
> es si el software entendió el proceso. Si algo del proceso está mal, dígalo
> con detalle: es más valioso que cualquier otra observación de hoy.

### Prueba 3 — Cerrar un subpaso
**Haga:** en el subpaso 4.1 cargue un documento cualquiera como evidencia y
ciérrelo.
**Debería ver:** que antes de cerrarlo la IA opina sobre lo que cargó. Esa
opinión es **un consejo, no un candado**: usted puede cerrar el paso igual
aunque la IA tenga dudas. Al cerrarse, el 4.2 queda en curso.
**Mire si:** le deja cerrar algo que no debería, o le impide cerrar algo que sí.

### Prueba 4 — El Copiloto
**Haga:** en la pantalla del contrato, abajo está el Copiloto. Pregúntele algo
en sus propias palabras («¿qué me falta en este paso?», «¿para qué sirve el
GCCON-F-031?»).
**Debería ver:** una respuesta con los datos de **este** contrato, y un botón
para ir directo al subpaso del que habla.

Que casi todo responda **al instante** no es un truco: las preguntas sobre
pasos, subpasos y formatos las contesta el propio SICOT con los datos del
contrato, sin molestar a la IA. Solo lo que de verdad hay que redactar pasa por
el modelo, y eso sí se nota en el tiempo. Está hecho así a propósito, para que
el programa sirva en un computador modesto.

**Mire si:** se inventa datos. Si dice una cifra o una fecha que no está en el
contrato, es un fallo grave y hay que anotarlo.

### Prueba 5 — Generar un documento con IA
**Haga:** pida el Informe de Supervisión (GCCON-F-031). Escriba sus
observaciones en lenguaje corriente, como se las contaría a alguien.
**Debería ver:** el documento armado con los datos del contrato en el formato
institucional, y sus observaciones pasadas a lenguaje formal. **Tarda unos 45
segundos** — está redactando en este equipo, sin internet.
**Mire si:** el formato no es el que usted usa, o si algún campo quedó
inventado. Lo que no sepa debe salir como «[dato pendiente]», nunca relleno.

> Comprobado el 09-11-2026: con la nota «llegaron las 40 mesas y los 80 bancos,
> todo en buen estado, falta la estantería metálica» el informe salió en 44
> segundos diciendo «Se revisó el mobiliario en bodega; se encontraron 40 mesas
> y 80 bancos en buen estado. No se localizó la estantería metálica.» Sin
> añadir nada que no estuviera en la nota.

### Prueba 6 — Firmar y verificar
**Haga:** revise el borrador, fírmelo y después pida verificarlo.
**Debería ver:** que **no le deja firmar sin haber visto el borrador** —es a
propósito: un documento que redacta SICOT solo se firma junto con la huella del
borrador que se le mostró, para que nadie firme a ciegas algo que cambió—, que
queda firmado con su nombre y que la verificación dice **ÍNTEGRO**, es decir
que el documento no se tocó después de la firma.
**Mire si:** firma sin pedir nada, o la verificación no responde.

### Prueba 7 — Lo que ve el Administrador
**Haga:** salga, entre como Administrador.
**Debería ver:** el avance de los supervisores, la lista de usuarios y las
firmas. **Nada más**: el Administrador vigila, no opera contratos. No hay
panel de control ni edición de formatos, y eso es a propósito.
**Mire si:** hay algo que un administrador no debería poder hacer.

### Prueba 8 — De dónde salen los contratos
**Haga:** salga, entre como Gestión.
**Debería ver:** la lista de contratos y la posibilidad de crear uno cargando
un documento, del que la IA extrae los datos.
**Mire si:** los campos que extrae no son los que de verdad hacen falta.

---

## 5. Lo que hoy no se puede probar

Dicho antes para que no parezca un fallo encontrado en medio de la prueba:

- **Invitar a un usuario nuevo por correo.** El envío de correo no está
  configurado en este equipo, así que el paso va a fallar con un mensaje
  claro. No es un error del programa: es que no hay buzón conectado.
- **Entrar por internet.** `sicot.medialab-sena.com` no responde: el servidor
  del Centro está apagado. Hoy la prueba es en este equipo o en la red local.
- **Bajar los archivos de un contrato del SECOP.** Los datos abiertos dan la
  ficha, las pólizas, los proveedores y la lista de archivos, pero la descarga
  está cerrada del lado del SECOP (responde 403) y no se rodea.
- **Leer un PDF escaneado (OCR).** Si el documento es una foto del papel, la
  IA no lo lee todavía. Con PDF de texto sí.
- **El teléfono.** Hay un APK, pero la IA no corre en un teléfono: lo que se
  prueba hoy en el computador es la versión completa.

---

## 6. Si algo falla

| Lo que pasa | Qué hacer |
|---|---|
| La ventana negra dice que Docker no arrancó | Abra Docker Desktop desde el menú Inicio, espere a que diga «Engine running» y vuelva a dar doble clic en `Prender SICOT.bat` |
| No sabe si quedó prendido, o perdió la dirección para entrar | Doble clic en `Estado de SICOT.bat`: dice qué está corriendo y vuelve a mostrar la dirección, sin prender ni apagar nada |
| La página no carga en `localhost:8443` | Doble clic en `Estado de SICOT.bat` para ver qué pieza falta, y vuelva a prender: `Prender SICOT.bat` levanta lo que falte sin tocar lo que ya corre |
| Los botones de IA dicen que no está disponible | Ollama no está arrancado. Vuelva a prender: el script lo levanta |
| La IA tarda muchísimo (más de dos minutos) | Falta memoria. Cierre pestañas del navegador y programas, y vuelva a prender diciendo **sí** a cerrar lo que no hace falta |
| «La IA tardó más de 240 segundos y la petición se canceló» | Pasa cuando el modelo **no quedó precalentado** y el equipo está ocupado: cargarlo y responder a la vez no cabe en la memoria. **Vuelva a intentarlo**: la segunda vez el modelo ya está cargado y responde en unos 45 segundos. Si se repite, apague y vuelva a prender con `Prender SICOT.bat`, que lo deja cargado antes de empezar |
| Desde otro computador no carga | `Estado de SICOT.bat` dice si el cortafuegos deja entrar por el 8443 en esa red. Si dice que sí y aun así no carga, es la red la que aísla los equipos (ver el final del punto 1) |

Para mirar qué pasó por dentro, en una ventana de PowerShell dentro de la
carpeta del proyecto:

```
docker compose logs --tail 40 backend
```

---

## 7. Cómo anotar lo que encuentre

Lo más útil de una prueba así no es «funciona» o «no funciona», sino **dónde el
programa se aparta del procedimiento real**. Para cada observación, tres
líneas bastan:

1. Dónde estaba (pantalla, contrato, subpaso).
2. Qué esperaba que pasara, según el procedimiento.
3. Qué pasó.

Las observaciones sobre el **proceso** (etapas, formatos, qué se verifica en
cada paso) valen más que las de apariencia: el proceso es lo difícil de
corregir después.

---

Relacionado: [`PROBAR_EN_OTRO_DISPOSITIVO.md`](PROBAR_EN_OTRO_DISPOSITIVO.md),
[`PRUEBA_MANUAL_APK.md`](PRUEBA_MANUAL_APK.md),
[`PRUEBA_LOCAL_SIN_SERVIDOR.md`](PRUEBA_LOCAL_SIN_SERVIDOR.md).
