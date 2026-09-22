# Instalación de SICOT — base del proyecto v0.3.0

Este paquete es el **árbol completo del proyecto**, listo para levantarlo. Solo
lleva lo versionado: no incluye `.env`, ni dependencias descargadas, ni nada
compilado. Todo eso se genera en el primer arranque.

Si lo que busca es entender qué hace SICOT antes de instalarlo, empiece por
[`README.md`](README.md); esta guía asume que ya decidió montarlo.

---

## Lo único que hay que tener instalado

**Docker Desktop**, abierto y corriendo. Nada más: ni Java, ni Node, ni
PostgreSQL. Las tres cosas viven dentro de los contenedores.

Para desarrollar sobre el código sí hacen falta Java 25 y Node 22 — eso está en
[`backend/README.md`](backend/README.md) y [`frontend/README.md`](frontend/README.md).

---

## Opción A — Probarlo en una sola máquina

Es el entorno con el que trabaja el equipo. Desde esta carpeta:

```bash
docker compose up -d --build
```

La primera vez tarda varios minutos: compila el backend y el frontend. Cuando
termine:

| Dirección | Qué es |
| --- | --- |
| http://localhost:8443 | La aplicación |
| http://localhost:8080/swagger-ui.html | La API, documentada y navegable |
| http://localhost:8081 | Adminer, para mirar la base de datos |

Las cuentas de prueba están en [`backend/README.md`](backend/README.md). **Solo
existen en este modo** y sus contraseñas son públicas: no sirven para un
despliegue real, y el sistema está hecho para que no puedan colarse en uno.

Para apagarlo: `docker compose down`. Para apagarlo y borrar también los datos:
`docker compose down -v`.

---

## Opción B — Instalarlo en el servidor del Centro

Aquí sí hay que configurar antes de arrancar, y el sistema **se niega a arrancar
si falta algo**. Es deliberado: un servidor institucional en pie al que nadie
puede entrar, o que firma sesiones con una clave publicada en internet, es peor
que un arranque que falla diciendo qué falta.

### 1. Cree el archivo `.env`

Copie `.env.example` a `.env` en esta misma carpeta y rellénelo. Los valores que
no puede dejar como están:

| Variable | Qué poner |
| --- | --- |
| `SPRING_PROFILES_ACTIVE` | `prod` |
| `DB_PASSWORD` | Una contraseña nueva, no la de desarrollo |
| `JWT_SECRET` | Genérela: `openssl rand -base64 32` |
| `SICOT_DOMINIO` | El dominio real del servidor |
| `VITE_API_URL` y `CORS_ALLOWED_ORIGINS` | La dirección real, **nunca** `localhost` |
| `SICOT_ADMIN_EMAIL` y `SICOT_ADMIN_PASSWORD` | La primera cuenta de administrador |
| `RESPALDO_DIRECTORIO` | La carpeta **del servidor** donde se guardarán los respaldos. Créela antes de arrancar (`mkdir -p /ruta/a/respaldos`): sin ella el sistema no arranca (ver paso 4) |

Lo de `VITE_API_URL` merece un aviso, porque es el error que más tiempo cuesta
diagnosticar: el frontend se compila con esa dirección **incrustada dentro**. Si
queda `localhost`, la aplicación carga pero no habla con el backend desde
ninguna otra máquina, y no da ningún error que apunte a la causa.

### 2. Levántelo con el archivo de producción

```bash
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --build
```

Frente a la opción A, esto cambia tres cosas: PostgreSQL deja de publicar su
puerto al exterior, Adminer no arranca, y un proxy Caddy termina TLS delante de
todo (ver [`docs/decisiones/ADR-009-terminacion-tls.md`](docs/decisiones/ADR-009-terminacion-tls.md)).

### 3. Entre y cambie la contraseña

Con la base vacía, el backend crea la cuenta que declaró en `SICOT_ADMIN_*`.
Entre con ella y **cámbiela desde el panel de administración**. Después puede
retirar esas dos variables del `.env`: solo se usan cuando no hay ningún usuario.

### 4. Programe el respaldo

Esto no es opcional y el sistema no lo hace por usted. `scripts/respaldo-sicot.sh`
está probado, pero alguien tiene que programarlo:

```bash
crontab -e
# 0 2 * * *  /ruta/a/scripts/respaldo-sicot.sh /ruta/a/respaldos
```

La carpeta del cron tiene que ser la misma `RESPALDO_DIRECTORIO` del paso 1.
El backend la ve en solo lectura y, al arrancar y una vez al día, avisa en el log
si el respaldo más reciente supera las 24 horas comprometidas, o si no hay
ninguno. Hasta que el cron haga el primero, ese aviso de «no hay NINGÚN
respaldo» es correcto: describe exactamente lo que está pasando.

Por eso `RESPALDO_DIRECTORIO` es obligatoria y el despliegue de producción no
arranca sin ella. Antes era opcional y, además, Compose no se la pasaba al
backend: aunque se definiera, el sistema avisaba de que faltaba y no vigilaba
nada. El compromiso de recuperación quedaba siendo una intención.

El detalle de restauración está en
[`docs/operacion/BACKUP_Y_RESTAURACION.md`](docs/operacion/BACKUP_Y_RESTAURACION.md).

---

## Sobre el asistente de IA

El copiloto usa **Ollama** corriendo en la misma máquina, gratuito y sin enviar
nada fuera. Es **opcional**: si no está instalado, SICOT funciona completo y las
funciones de IA responden con un error honesto en vez de fingir.

Si lo quiere, instale [Ollama](https://ollama.com) y descargue el modelo:

```bash
ollama pull qwen2.5:7b
```

Dos cosas medidas que conviene saber antes de prometerle nada a nadie:

- Sobre CPU, **la primera pregunta sobre un contrato tarda más de dos minutos**.
  Las siguientes sobre el mismo contrato bajan a segundos, porque el contexto
  queda cacheado — y la aplicación lo precalienta al abrir el contrato para que
  esa espera ocurra mientras el supervisor lee la ficha.
- Las preguntas de «¿en qué paso voy?» **no pasan por el modelo**: se responden
  con los datos del contrato, al instante y sin posibilidad de equivocarse.

El porqué de las dos decisiones, con las mediciones, está en
[`docs/decisiones/ADR-006-modelo-de-ia.md`](docs/decisiones/ADR-006-modelo-de-ia.md).

---

## Si algo no arranca

| Síntoma | Dónde mirar |
| --- | --- |
| El backend no arranca y el log habla de `JWT_SECRET` | Falta esa variable en el `.env`, o no es Base64 de 32 bytes |
| El backend no arranca y el log habla de migraciones | Volumen de PostgreSQL de una versión anterior. `docker compose down -v` lo borra (**se llevará los datos**) |
| La aplicación carga pero no muestra nada | `VITE_API_URL` quedó en `localhost`, o el cortafuegos bloquea 8443/8080 |
| Las funciones de IA dan 503 | Ollama no está corriendo, o el modelo no está descargado |

Para ver qué pasa: `docker compose ps` y `docker compose logs -f backend`.

---

## La aplicación de escritorio del supervisor

Este paquete monta el **servidor**. El supervisor no lo necesita: a él se le
entrega el instalador `SICOT_0.3.0_x64-setup.exe`, publicado como asset aparte
en esta misma versión.

Esa aplicación es la misma SICOT en su propia ventana, y habla con el servidor
que usted acaba de montar. **Requiere conexión**: no funciona sin red, y es una
decisión tomada a conciencia en
[`ADR-001`](docs/decisiones/ADR-001-bifurcamiento-de-despliegue.md), no una
carencia pendiente.

Lo único que hay que decirle al supervisor después de instalarla es **la
dirección de este servidor**, que él escribe una vez en *Configuración →
Servidor*. La dirección no viaja dentro del instalador a propósito: así el
mismo archivo sirve para cualquier Centro, y mover el servidor no obliga a
volver a publicarlo ni a reinstalar nada en los equipos.

Windows advertirá de un «editor desconocido» al instalarlo. Es esperable: el
instalador todavía no está firmado con un certificado de código, que es de
pago. Se continúa con *Más información → Ejecutar de todas formas*.

Para compilarlo usted mismo, el empaquetado vive en `frontend/src-tauri/` y
está documentado en [`README.md`](README.md); exige la cadena de Rust y las
*Build Tools* de Visual Studio, que este paquete no necesita.

## SICOT en el teléfono

**Estado desde la versión 0.4.0: hay una aplicación de Android instalable, firmada
y publicada en esta misma página de versiones.** Se probó el trabajo completo del
supervisor dentro de ella —entrar, bandeja, contrato, copiloto, descargar un acta
firmada, exportar registros— en un emulador de Android 15. Lo que no se ha hecho
todavía es probarla en un teléfono de verdad, y conviene decirlo así antes de
prometerle nada a nadie.

La otra forma sigue valiendo: **abrir SICOT en el navegador del teléfono**,
apuntando a la dirección del servidor del Centro.

### Para el supervisor: instalarla

1. Descargue `SICOT_<versión>_android-arm64.apk` de la versión publicada.
2. Ábralo. Android pedirá permiso para **instalar aplicaciones de fuentes
   desconocidas** desde el navegador o el gestor de archivos: concédalo para esa
   aplicación. Play Protect puede avisar de que no conoce la aplicación; es
   esperable, porque no se publica en Google Play (ADR-013), y es el mismo tipo
   de aviso que el de «editor desconocido» del instalador de escritorio.
3. Al abrir SICOT, pulse la dirección que aparece junto a **Servidor:**, al pie
   de la pantalla de acceso, y escriba la dirección `https://…` del servidor de
   su Centro. Su área de sistemas se la dará.

Las actualizaciones se instalan encima, sin desinstalar, y conservan la
dirección del servidor. Eso funciona porque todas las versiones van firmadas con
la misma llave; el porqué está en
[`ADR-013`](docs/decisiones/ADR-013-firma-y-distribucion-del-apk.md).

### Para el área de sistemas: el certificado del Centro

Si el servidor se monta como describe ADR-009 **sin un dominio público** —el
caso más probable en un Centro—, Caddy cifra con un certificado de su propia
autoridad local. Un navegador pide confiar en él una vez. **La aplicación no:
sin instalar esa autoridad en el teléfono, no podrá conectarse**, y lo dirá en
la pantalla de acceso.

1. Saque la raíz de Caddy del servidor:
   ```bash
   docker compose -f docker-compose.yml -f docker-compose.prod.yml \
     cp proxy:/data/caddy/pki/authorities/local/root.crt ./raiz-sicot.crt
   ```
2. Pásela al teléfono (cable, correo institucional, memoria USB).
3. En el teléfono: **Ajustes → Seguridad y privacidad → Más ajustes de seguridad
   → Cifrado y credenciales → Instalar un certificado → Certificado de CA**.
   Android avisará de que «sus datos no serán privados» y pedirá el bloqueo de
   pantalla: es el aviso estándar de cualquier certificado de organización. Elija
   el archivo.
4. Queda en *Credenciales de confianza → Usuario*.

Solo hay que hacerlo una vez por teléfono. Si el Centro tiene un dominio con
certificado público, nada de esto hace falta.

### Lo que no hay, y por qué

| Qué falta | Por qué |
| --- | --- |
| Publicación en Google Play | Exige una cuenta de pago a nombre de alguien: decisión institucional, prohibida por ADR-012 sin ella |
| Teléfonos de 32 bits | Se publica para arm64, que cubre los teléfonos de los últimos años |
| Versión para iPhone | Exige macOS con Xcode, que el equipo no tiene, y un programa de desarrollador de pago |
| Funcionar sin conexión | Igual que el escritorio, exige red. Es una decisión tomada a conciencia (ADR-001), no una carencia |
| Seguir esperando al copiloto con la aplicación cerrada | Android corta las conexiones de las aplicaciones en segundo plano. Si el supervisor sale mientras el copiloto piensa, SICOT lo dice y vuelve a preguntar al volver |

### Dos avisos que ahorran diagnósticos difíciles

**`http://` no funciona en la aplicación.** Android bloquea el tráfico sin cifrar
en las compilaciones de publicación. La aplicación lo avisa en cuanto se escribe
una dirección `http://`, antes de intentar nada.

**«No se pudo conectar con el servidor» con una dirección `https://`** casi
siempre es el certificado del apartado anterior sin instalar. La propia pantalla
de acceso lo indica.

La lista de comprobación manual antes de publicar una versión —lo que las
pruebas automáticas no pueden ver en un teléfono— está en
[`docs/operacion/PRUEBA_MANUAL_APK.md`](docs/operacion/PRUEBA_MANUAL_APK.md).
