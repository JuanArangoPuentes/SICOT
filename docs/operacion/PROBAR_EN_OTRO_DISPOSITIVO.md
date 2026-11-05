# Probar SICOT en otro teléfono o computador

El APK de Android, el instalador de Windows y el navegador son la **ventana**
de SICOT: los datos, la firma y la IA viven en el **servidor** (Docker con el
backend, la base y Ollama). Para probar en otro dispositivo hace falta,
entonces, que ese dispositivo **llegue al servidor**. Hoy el único servidor con
la IA es el portátil de desarrollo, así que el otro dispositivo tiene que estar
**en la misma red wifi** que el portátil.

Esto es para pruebas. El despliegue del Centro, con su dominio y su
certificado, está en `INSTALACION.md`.

## Lo que hay que descargar

Nada, si el otro equipo es un computador y basta con el navegador: eso es el
[camino A](#a-solo-el-navegador) del paso 2. Lo de abajo es para probar las
aplicaciones instaladas, que es lo que usará un supervisor de verdad.

Todo está en las versiones publicadas de GitHub (**Releases** del
repositorio). **Use la v0.8.0 o una posterior**: la v0.7.0 no trae el trabajo
sin red (ADR-015) ni que los equipos se vean entre sí (ADR-016), así que con ella
las pruebas de abajo no tienen nada que mostrar. La pantalla de acceso dice qué
versión está instalada («SICOT v0.8.0»).

| Archivo | Para qué |
| --- | --- |
| `SICOT_<versión>_x64-setup.exe` | El instalador de Windows (escritorio del supervisor). |
| `SICOT_<versión>_android-arm64.apk` | La aplicación de Android **oficial**, firmada. Solo habla por `https://`. |
| `SICOT_<versión>_android-arm64-pruebas-red-local.apk` | La misma aplicación en su compilación de **pruebas**: acepta `http://`, para hablar con el portátil en la red local sin certificado. No es la que se instala a los supervisores. |
| `documentos-de-prueba-SICOT.zip` | Actas de inicio (GCCON-F-018) de seis contratos ficticios y una muestra de cada formato que firma el supervisor. |

## 1. En el portátil (el servidor)

1. Docker Desktop abierto y la pila arriba, **reconstruida**:
   `docker compose up -d --build` en la carpeta del proyecto. Ollama abierto
   (ícono de la llama junto al reloj).

   El `--build` no es opcional después de traer cambios. La dirección de la API
   y la configuración de nginx viven **dentro** de la imagen del frontend, y
   `docker compose up -d` a secas reutiliza la que ya hay: con una imagen
   anterior al 05-11-2026 el otro equipo carga la página y no deja entrar
   (ver la tabla del final).
2. La dirección del portátil en la red: en PowerShell, `ipconfig`, y anotar la
   «Dirección IPv4» del adaptador wifi (por ejemplo `192.168.1.50`). No la
   escriba en ningún archivo del proyecto: nada la necesita, y cambia cuando el
   portátil cambia de red.
3. **Un solo puerto, el 8443**, para todo: el navegador, el instalador y el APK.
   Con Docker Desktop suele no hacer falta tocar el firewall (el porqué está en
   el camino A, abajo); si el otro equipo no llega, en PowerShell **como
   administrador**:

   ```powershell
   New-NetFirewallRule -DisplayName "SICOT pruebas (8443)" -Direction Inbound -Protocol TCP -LocalPort 8443 -Action Allow -Profile Private,Public
   ```

   `Public` va incluido porque la wifi de un Centro o una zona wifi de teléfono
   suelen quedar como red pública en Windows, y con solo `Private` la regla no
   aplicaría justo ahí. Por eso mismo, **quítela al terminar la prueba**:
   `Remove-NetFirewallRule -DisplayName "SICOT pruebas (8443)"`.

   El 8080 (el backend directo, con Swagger) sigue publicado, pero ningún
   camino de esta guía lo necesita. La base (5433) y Adminer (8081) solo
   escuchan en el propio portátil (`127.0.0.1`): desde otro equipo no se
   alcanzan, aunque el firewall los dejara.

> El perfil de desarrollo trae cuentas de prueba con contraseñas conocidas. Por
> eso esto se hace solo en una red de confianza y por el tiempo de la prueba.
> **No** se expone el puerto a Internet.

## 2. En el otro computador (Windows)

Hay dos caminos. El **A** no instala nada y sirve para probar en cinco minutos;
el **B** es el que usará un supervisor de verdad.

### A. Solo el navegador

El otro computador abre `http://192.168.1.50:8443` y ya está. No hace falta
instalar nada: ni el certificado, ni el instalador, ni tocar la pila. Es el
camino bueno cuando el otro equipo es institucional y no deja instalar.

**No hay ninguna dirección que acertar.** La aplicación pide la API por
`/api/...` sobre la misma dirección desde la que se abrió, y el nginx de la
imagen la reenvía al backend — igual que hace Caddy en el Centro. Así que el
mismo `docker compose up -d --build` de siempre sirve para este equipo y para
cualquier otro, y nada se rompe cuando el portátil cambia de red y le dan otra
IP.

> Hasta el 03-11-2026 no era así: `VITE_API_URL` se horneaba en el paquete y
> había que reconstruir con la IP del portátil, cuadrándola además con la CSP
> de nginx y con los orígenes de CORS del backend. Tres sitios que tenían que
> decir lo mismo, y un cambio de red que lo tiraba todo sin avisar. Ver
> [`ADR-016`](../decisiones/ADR-016-propagacion-entre-equipos.md).
>
> Y hasta el 05-11-2026 faltaba una pieza: el nginx reenviaba la API con
> `Host: backend:8080`, el backend veía el `Origin` del otro equipo como de
> otro sitio y rechazaba el inicio de sesión con **403**. En el propio portátil
> no se notaba (`localhost` sí está en la lista de orígenes). Para comprobarlo
> desde el otro equipo, sin abrir la aplicación:
>
> ```powershell
> curl.exe -s -o NUL -w "%{http_code}" -X POST http://192.168.1.50:8443/api/auth/login -H "Origin: http://192.168.1.50:8443" -H "Content-Type: application/json" -d "{}"
> ```
>
> Tiene que dar `400` o `401`. Un `403` quiere decir que el portátil corre una
> imagen vieja del frontend: `docker compose up -d --build`.

**Con Docker Desktop no suele hacer falta tocar el firewall**, y conviene saber
por qué para no perder el tiempo ahí. Comprobado el 03-11-2026: en el puerto
publicado escuchan dos procesos distintos, y solo uno atiende a los de fuera.

```powershell
Get-NetTCPConnection -State Listen | Where-Object LocalPort -eq 8443 |
  ForEach-Object { "{0}  <- {1}" -f $_.LocalAddress, (Get-Process -Id $_.OwningProcess).ProcessName }
```

En `::1` (solo este equipo) escucha `wslrelay`; en `::` —que con doble pila es
«cualquier dirección», por donde entra el otro computador— escucha
`com.docker.backend`, **que ya trae su propia regla de entrada para cualquier
puerto**. Por eso el otro equipo pasa sin que nadie cree nada.

Si aun así no carga, la regla explícita del paso 1.3 no estorba. Y si tampoco
con ella, deje de mirar el portátil: lo más probable es la red —ver *«Si el
otro equipo no llega, y no es el firewall»* al final.

### B. Con el instalador de escritorio

1. Instalar `SICOT_<versión>_x64-setup.exe`. Windows avisará de un «editor
   desconocido»: **Más información → Ejecutar de todas formas**.
2. Abrir SICOT, pulsar la dirección que aparece junto a **Servidor:** (abajo, en
   la pantalla de acceso) y escribir `http://192.168.1.50:8443` con la IP del
   paso 1.2 —el mismo puerto que el navegador—. Guardar.
3. Entrar con una cuenta de prueba (están en `backend/README.md`).

Por este camino **no** hace falta reconstruir nada con la IP: la dirección del
servidor se escribe en la aplicación y se guarda en el equipo, que es justo
para lo que está ese campo.

## 3. En el otro teléfono (Android)

1. Si ya tiene instalado **cualquier** SICOT —el oficial o un APK de pruebas
   anterior—, desinstálelo primero. El de pruebas está firmado con la llave de
   depuración de la máquina que lo compiló, distinta de la oficial y distinta
   entre dos compilaciones de pruebas, y Android no instala encima de una firma
   distinta: dice «La aplicación no se instaló» sin más. Desinstalar borra la
   dirección del servidor y lo guardado sin red de ese teléfono.
2. Descargar `SICOT_<versión>_android-arm64-pruebas-red-local.apk` en el
   teléfono, abrirlo y permitir **instalar aplicaciones de fuentes
   desconocidas**. Play Protect puede advertir que no conoce la aplicación: es
   esperable, SICOT no se publica en Google Play.
3. Abrir SICOT, pulsar la dirección junto a **Servidor:** y escribir
   `http://192.168.1.50:8443`. Entrar con una cuenta de prueba.

   El 8443 y no el 8080: desde que nginx reenvía la API, los dos sirven, pero
   usando el mismo que el navegador basta con **un** puerto abierto en el
   portátil y hay una cosa menos que explicar. Por ese camino pasa también la
   respuesta en vivo del Copiloto (nginx no la guarda en un búfer).

Para probar el APK **oficial** hace falta `https://` con un certificado en el que
el teléfono confíe: el montaje con Caddy y la raíz instalada en el teléfono está
en `INSTALACION.md`, apartado *SICOT en el teléfono*, y en
`PRUEBA_MANUAL_APK.md`.

## 4. Qué probar

- **Gestión** (`gestion@…`): «Cargar nueva ficha» con una de las actas de
  `documentos-de-prueba-SICOT.zip`. Todos los campos tienen que salir del
  documento en uno o dos segundos (número, objeto, contratista, NIT,
  representante, valor, fechas, lugar, registro presupuestal, tipo, supervisor
  designado y correo). Elegir el supervisor y crear el contrato.
- **Supervisor**: abrir el contrato, preguntarle al Copiloto (las sugerencias
  responden al instante; una pregunta abierta tarda la primera vez uno o varios
  minutos según el portátil, y se ve escribir), cargar el acta firmada y la
  evidencia en el 2.1, y en el 3.4 «Firmar documento» para ver el Informe de
  Supervisión con el formato oficial y descargarlo.

### Que los dos equipos se vean (ADR-016)

Esto es lo que se prueba con **dos equipos a la vez**, y es lo que no se puede
comprobar con uno solo. Hacen falta dos ventanas de SICOT sobre el **mismo
contrato**: el portátil y el otro computador, o el portátil y el teléfono. Da
igual cuáles, mientras hablen con el mismo servidor.

1. En los dos, entre como `supervisor@…` y abra el **mismo contrato**. Deje las
   dos pantallas quietas: no toque nada en la que va a mirar.
2. En el equipo **A**, cierre un sub-paso (el 2.2 no exige documentos), o
   marque una alerta como leída.
3. Mire el equipo **B** sin tocarlo. En unos segundos —cinco con la ventana
   delante— el avance, el paso siguiente y las alertas se ponen al día solos.
4. Pruebe también al revés: deje B en segundo plano (cambie de ventana), haga
   el cambio en A, y vuelva a B. Se actualiza **en el acto** al volver, sin
   esperar los cinco segundos.

Lo que se está viendo es un sondeo, no un canal abierto: B le pregunta al
servidor «¿pasó algo desde la última vez?» y, cuando le dicen que sí, vuelve a
pedir las pantallas enteras. El porqué de esa forma y no de un WebSocket está
en [`ADR-016`](../decisiones/ADR-016-propagacion-entre-equipos.md).

**La lista de contratos va por otro camino, y conviene saberlo antes de
probarlo.** El sondeo pregunta **por un contrato**, así que no puede traer uno
que todavía no está en la lista. Si Gestión crea un contrato o designa a un
supervisor desde el otro equipo, lo que pone la lista al día es **volver a la
ventana**: cambie a otra aplicación y vuelva, y el contrato nuevo aparece en el
selector (o en la tabla de Gestión). Sin ese gesto no aparece solo, y es a
propósito: una lista de contratos no cambia cada cinco segundos.

### Trabajar sin red (ADR-015)

Todo lo de arriba ocurre en la red local, sin Internet: el servidor es el
portátil. Lo que sigue prueba además que el supervisor trabaja **sin llegar a
ese servidor**, como en bodega o en obra, y que su trabajo llega al volver.

1. En el dispositivo del supervisor, entre **con** red y abra el contrato. El
   equipo guarda una copia de lo que ve y, si la cuenta es de supervisor, una
   huella de la contraseña para entrar sin red durante 72 horas. Desde una
   dirección `http://` en un navegador no hay acceso sin red; en la app
   instalada y en el APK, sí.
2. Corte el camino al servidor: apague la wifi del dispositivo, o detenga el
   backend en el portátil (`docker compose stop backend`).
3. Sin red, en el 2.2 agregue un contacto de SST y cierre el sub-paso. Arriba
   tiene que decir «Sin conexión con el servidor», debajo «2 cambios guardados
   en este equipo, pendientes de enviar», y el 2.2 queda con el rótulo
   «Pendiente de enviar».
4. Vuelva a dar el camino. Antes de 30 segundos la franja desaparece y el
   expediente queda al día. En **Registros**, cada cambio dice a qué hora lo
   hizo el equipo y la fecha de la columna es cuándo llegó al servidor.
5. Para el caso del token vencido (más de 8 horas sin entrar): con el camino
   cortado, la pantalla de acceso deja entrar con la misma contraseña. Al volver
   la red avisa «Volvió la conexión con el servidor» y, al entrar de nuevo, se
   envía lo guardado.

Firmar, generar y cargar documentos siguen necesitando red, y el aviso lo dice.

## Si no conecta

| Lo que se ve | Qué revisar |
| --- | --- |
| «Esta instalación todavía no tiene la dirección del servidor» | Falta el paso 2.2 / 3.3. |
| «No se pudo conectar con el servidor» | Que el portátil y el dispositivo estén en la misma wifi, la IP (cambia si el portátil se reconecta), la regla del firewall y que `docker compose ps` muestre el backend `healthy`. Desde el navegador del otro dispositivo, `http://<IP>:8443/actuator/health` tiene que responder `{"status":"UP"}`: es el mismo camino que usa la aplicación. |
| La página carga, pero al entrar dice «Error 403» | El portátil corre una imagen del frontend anterior al 05-11-2026, que no reenvía el `Host` y hace que el backend rechace al otro equipo. `docker compose up -d --build` en el portátil. La comprobación con `curl` está en el camino A. |
| «Demasiados intentos» en **todos** los equipos a la vez | Por el 8443 el backend ve a todos los navegadores con la dirección del nginx, así que los 20 intentos fallidos que tolera cuentan entre todos. Es una limitación del montaje de desarrollo (en el Centro, Caddy pasa la dirección real). Espere a que venza el bloqueo o reinicie el backend: `docker compose restart backend`. |
| Por el camino A, la página carga pero se queda vacía o no deja entrar | Alguien fijó a mano una dirección de servidor en ese navegador. Mire la que sale junto a **Servidor:** en la pantalla de acceso: si apunta a `localhost` o a una IP vieja, bórrela desde ahí para que vuelva a usar el mismo origen. |
| Por el camino A no carga nada | Primero el firewall (la regla del 8443 del paso 1.3). Si `ping` a la IP del portátil tampoco contesta desde el otro equipo, no es el firewall sino la red — ver *«Si el otro equipo no llega, y no es el firewall»*. |
| Un equipo no ve lo que hizo el otro | Que los dos estén en el **mismo contrato** (el sondeo es por contrato) y que la sesión sea con red — sin token no se sondea, a propósito. Si uno de los dos está en sesión sin conexión, lo que ve es la copia guardada y lo dice en pantalla. |
| En el APK oficial, «Android bloquea las conexiones sin cifrar» | Es lo esperado con `http://`: use el APK de pruebas o monte `https://`. El APK de pruebas no muestra ese aviso, porque él sí conecta por `http://`; si lo muestra, el instalado es el oficial. |

## Si el otro equipo no llega, y no es el firewall

Antes de seguir peleando con el portátil, descarte la red. Muchas wifi
institucionales —y algunas de invitados— tienen **aislamiento de clientes**: dos
equipos conectados al mismo punto de acceso tienen internet pero **no se ven
entre sí**, por diseño y sin nada que configurar en Windows. El síntoma es
exactamente el de un firewall cerrado, y ninguna regla del portátil lo arregla.

Cómo distinguirlo, desde el otro equipo:

```powershell
ping 192.168.1.50
```

Si el `ping` tampoco contesta, no es el puerto: es la red. La salida es
conectar los dos equipos a una red que usted controle —la zona wifi de un
teléfono sirve— y repetir con la IP nueva (que será otra: `ipconfig` de nuevo).

## Y fuera de la red local

Para que un dispositivo **fuera** de la wifi (con datos móviles) llegue al
portátil haría falta exponerlo a Internet con un túnel. No se hizo: deja
accesible desde Internet un servidor de desarrollo con cuentas de prueba
conocidas, y es una decisión de quien administra el equipo, no del software. Si
se decide hacerlo para una prueba puntual, `cloudflared tunnel --url
http://localhost:8080` (gratuito, sin cuenta) da una dirección `https://` con un
certificado público que el APK oficial acepta sin instalar nada; se cierra con
Ctrl+C al terminar. Una respuesta del Copiloto que tarda más de 100 s pasa sin
cortarse porque llega en vivo.
