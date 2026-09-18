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

Defina también `RESPALDO_DIRECTORIO` en el `.env`, apuntando a esa misma carpeta.
Con eso el backend vigila la antigüedad del último respaldo y avisa en el log
cuando supera las 24 horas comprometidas. Sin esa variable no vigila nada, y el
compromiso de recuperación queda siendo una intención.

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

**Estado a día de hoy: la interfaz ya funciona en una pantalla de teléfono y el
proyecto de Android existe y compila. No hay todavía un instalador publicado.**
Conviene leer esa frase entera antes de prometerle nada a nadie.

Lo que sí se puede hacer hoy:

- **Abrir SICOT en el navegador del teléfono**, apuntando a la dirección del
  servidor del Centro. Funciona: la aplicación se adapta a pantalla estrecha —la
  navegación pasa abajo, al alcance del pulgar, y las tablas se convierten en
  fichas con sus campos etiquetados en vez de recortarse—.
- **Compilar un APK de depuración** desde el repositorio, para probarlo en un
  teléfono propio. Exige la cadena de herramientas de Android (SDK, NDK y JDK
  21), y está documentado en [`frontend/README.md`](frontend/README.md).

Lo que **no** hay, y por qué:

| Qué falta | Por qué |
| --- | --- |
| Un APK firmado y publicado | La firma de distribución es una decisión de cuenta institucional, no de código. La compilación de publicación ya se probó el 17 de septiembre de 2026 y pesa **12 MB** frente a los 132 MB de la de depuración, pero sale **sin firmar** y por eso no se puede instalar |
| Versión para iPhone | Exige macOS con Xcode, que el equipo no tiene, y un programa de desarrollador de pago |
| Funcionar sin conexión | Igual que el escritorio, exige red. Es una decisión tomada a conciencia, no una carencia |

Y un aviso que ahorra un diagnóstico difícil: **Android bloquea el tráfico sin
cifrar en las compilaciones de publicación**. Si el servidor del Centro se sirve
por `http://` y no por `https://`, las peticiones de una aplicación publicada no
saldrán. Está comprobado en el manifiesto de las dos compilaciones, no deducido.

Desde el 17 de septiembre de 2026 **la aplicación lo dice antes de que ocurra**:
si la dirección configurada empieza por `http://` y se está ejecutando dentro de
la aplicación de Android, sale un aviso en la pantalla de acceso y en
Configuración. Antes, el único síntoma era una aplicación que no respondía, y el
diagnóstico natural —«el servidor está caído»— era falso.

Eso no desbloquea nada: para autorizar un servidor concreto sin TLS hace falta
su nombre, que nadie ha dado todavía. El detalle está en
[`ADR-012`](docs/decisiones/ADR-012-aplicacion-movil.md).
