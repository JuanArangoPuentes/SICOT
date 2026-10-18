# Probar SICOT en otro teléfono o computador, solo con el instalador

El APK de Android y el instalador de Windows son la **ventana** de SICOT: los
datos, la firma y la IA viven en el **servidor** (Docker con el backend, la base
y Ollama). Para probar en otro dispositivo hace falta, entonces, que ese
dispositivo **llegue al servidor**. Hoy el único servidor con la IA es el
portátil de desarrollo, así que el otro dispositivo tiene que estar **en la
misma red wifi** que el portátil.

Esto es para pruebas. El despliegue del Centro, con su dominio y su
certificado, está en `INSTALACION.md`.

## Lo que hay que descargar

Todo está en la última versión publicada de GitHub (**Releases** del
repositorio):

| Archivo | Para qué |
| --- | --- |
| `SICOT_<versión>_x64-setup.exe` | El instalador de Windows (escritorio del supervisor). |
| `SICOT_<versión>_android-arm64.apk` | La aplicación de Android **oficial**, firmada. Solo habla por `https://`. |
| `SICOT_<versión>_android-arm64-pruebas-red-local.apk` | La misma aplicación en su compilación de **pruebas**: acepta `http://`, para hablar con el portátil en la red local sin certificado. No es la que se instala a los supervisores. |
| `documentos-de-prueba-SICOT.zip` | Actas de inicio (GCCON-F-018) de seis contratos ficticios y una muestra de cada formato que firma el supervisor. |

## 1. En el portátil (el servidor)

1. Docker Desktop abierto y la pila arriba: `docker compose up -d` en la carpeta
   del proyecto. Ollama abierto (ícono de la llama junto al reloj).
2. La dirección del portátil en la red: en PowerShell, `ipconfig`, y anotar la
   «Dirección IPv4» del adaptador wifi (por ejemplo `192.168.1.50`).
3. Que el firewall de Windows deje entrar al puerto 8080 desde la red local. La
   primera vez Windows puede preguntarlo; si no, en PowerShell como
   administrador:

   ```powershell
   New-NetFirewallRule -DisplayName "SICOT pruebas (8080)" -Direction Inbound -Protocol TCP -LocalPort 8080 -Action Allow -Profile Private
   ```

   La regla solo vale en redes **privadas** (la de la casa u oficina, no una
   red pública). Para quitarla después:
   `Remove-NetFirewallRule -DisplayName "SICOT pruebas (8080)"`.

> El perfil de desarrollo trae cuentas de prueba con contraseñas conocidas. Por
> eso esto se hace solo en una red de confianza y con la regla en el perfil
> privado. **No** se expone el puerto a Internet.

## 2. En el otro computador (Windows)

1. Instalar `SICOT_<versión>_x64-setup.exe`. Windows avisará de un «editor
   desconocido»: **Más información → Ejecutar de todas formas**.
2. Abrir SICOT, pulsar la dirección que aparece junto a **Servidor:** (abajo, en
   la pantalla de acceso) y escribir `http://192.168.1.50:8080` con la IP del
   paso 1.2. Guardar.
3. Entrar con una cuenta de prueba (están en `backend/README.md`).

## 3. En el otro teléfono (Android)

1. Si ya tiene instalado el SICOT oficial, desinstálelo primero: el de pruebas
   está firmado con otra llave y Android no lo instala encima.
2. Descargar `SICOT_<versión>_android-arm64-pruebas-red-local.apk` en el
   teléfono, abrirlo y permitir **instalar aplicaciones de fuentes
   desconocidas**. Play Protect puede advertir que no conoce la aplicación: es
   esperable, SICOT no se publica en Google Play.
3. Abrir SICOT, pulsar la dirección junto a **Servidor:** y escribir
   `http://192.168.1.50:8080`. Entrar con una cuenta de prueba.

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

## Si no conecta

| Lo que se ve | Qué revisar |
| --- | --- |
| «Esta instalación todavía no tiene la dirección del servidor» | Falta el paso 2.2 / 3.3. |
| «No se pudo conectar con el servidor» | Que el portátil y el dispositivo estén en la misma wifi, la IP (cambia si el portátil se reconecta), la regla del firewall y que `docker compose ps` muestre el backend `healthy`. Desde el navegador del otro dispositivo, `http://<IP>:8080/actuator/health` tiene que responder `{"status":"UP"}`. |
| En el APK oficial, «Android bloquea las conexiones sin cifrar» | Es lo esperado con `http://`: use el APK de pruebas o monte `https://`. |

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
