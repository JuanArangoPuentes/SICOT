# SICOT Backend

Backend del **Sistema Inteligente para la Gestión y Acompañamiento de Contratos** (SENA — Centro Tecnológico del Mobiliario).

Spring Boot 3.5.12 · Java 25 · PostgreSQL · JWT · Flyway · Swagger/OpenAPI.

> ⚠️ **Fase actual:** monolito modular con autenticación JWT, CRUD de usuarios/contratos, flujo de etapas GCCON-P-010, alertas, documentos, auditoría, **Copiloto IA real (Ollama)** — extracción de datos de contrato, chat conversacional con memoria, redacción de documentos formales —, firma electrónica (referencia interna) y entrega de credenciales por correo. Pendiente: integración SECOP II y OCR de documentos escaneados.

---

## 1. Requisitos

| Herramienta | Versión |
|---|---|
| JDK | 25+ (el proyecto compila con `--release 25`) |
| Maven | 3.9+ |
| PostgreSQL | 14+ (probado con 18) |

## 2. Base de datos

En una base nueva, Flyway aplica dos migraciones:

| Archivo | Qué hace |
|---|---|
| `V1__create_sicot_schema.sql` | Línea base completa: tablas, constraints e índices |
| `V9__add_indices_fecha_alertas_registros.sql` | Índices por fecha en `alertas` y `registros` (los usa la paginación) |

La numeración salta de `V1` a `V9` a propósito: las migraciones `V1`–`V8`
originales se consolidaron en la nueva `V1`, y `V9` se conservó porque ya se
había aplicado en bases existentes. **No hay migraciones perdidas.**

No se insertan datos transaccionales de ejemplo ni se ejecutan migraciones
destructivas. Las cuentas demo solo se crean con el perfil `dev` mediante
`DataInitializer`.

Para recrear una base local Docker desde cero, detenga el stack y elimine solo
su volumen local (`docker compose down -v`). Nunca ejecute ese comando sobre
una base productiva.

```sql
-- (con el usuario superusuario de PostgreSQL)
CREATE USER sicot WITH PASSWORD 'sicot_dev_password';
CREATE DATABASE sicot OWNER sicot;
```

El esquema se crea automáticamente con **Flyway** al primer arranque. La línea
base contiene estructura, constraints e índices, pero no inserta ni elimina
datos transaccionales demo.

Los **usuarios** se crean al arrancar (solo si la tabla está vacía) por `DataInitializer` con contraseñas codificadas en BCrypt:

| Email | Rol | Contraseña (solo desarrollo) |
|---|---|---|
| `administrador@soy.sena.edu.co` | ADMINISTRADOR | `Admin123*` |
| `gestion@soy.sena.edu.co` | GESTION | `Gestion123*` |
| `supervisor@soy.sena.edu.co` | SUPERVISOR | `Supervisor123*` |

> ⚠️ **Estas contraseñas son públicas** (están en este archivo, en un repositorio
> compartido) y por eso estas cuentas **solo existen bajo el perfil `dev`**.
> `DataInitializer` no las crea con ningún otro perfil, y `docker-compose.prod.yml`
> fija `SPRING_PROFILES_ACTIVE=prod` de forma literal para que no puedan aparecer
> en un servidor por un `.env` mal copiado. Si alguna vez ve estas cuentas en un
> despliegue real, ese despliegue está corriendo con el perfil equivocado.

## 3. Configuración (variables de entorno)

Copie `.env.example` a un `.env` (o exporte las variables) — los valores por defecto en `application.properties` solo funcionan en desarrollo:

| Variable | Descripción |
|---|---|
| `SPRING_PROFILES_ACTIVE` | `dev` por defecto (siembra los usuarios de prueba de la tabla de abajo). El servidor remoto de producción **debe** fijarlo a otro valor (p. ej. `prod`) para que esas cuentas conocidas nunca se creen ahí |
| `DB_URL` | URL JDBC (por defecto `jdbc:postgresql://localhost:5432/sicot`) |
| `DB_USERNAME` / `DB_PASSWORD` | Credenciales de la base |
| `JWT_SECRET` | Clave HMAC-SHA256 ≥ 32 bytes **en Base64** (`openssl rand -base64 48`) |
| `JWT_EXPIRATION_MS` | Vigencia del token en ms (por defecto 8 h) |
| `CORS_ALLOWED_ORIGINS` | Orígenes permitidos separados por coma |
| `MAIL_HOST` / `MAIL_PORT` / `MAIL_USERNAME` / `MAIL_PASSWORD` / `MAIL_FROM` | Entrega real de credenciales por correo — sin esto, el envío falla honestamente en vez de fingir éxito |
| `OLLAMA_URL` | URL del servidor Ollama (por defecto `http://localhost:11434` — debe correr en la misma máquina que este backend) |
| `OLLAMA_MODEL` | Modelo a usar — ver "Elegir el modelo de Ollama" abajo |
| `OLLAMA_TIMEOUT_SECONDS` | Tiempo máximo de espera por una respuesta de la IA (por defecto 900 = 15 min, para máquinas sin GPU) |
| `PORT` | Puerto HTTP (por defecto 8080) |

### Elegir el modelo de Ollama

SICOT nunca llama a un servicio de IA pago — todo corre en un Ollama local, en la misma
máquina que este backend. Qué modelo usar depende del hardware de esa máquina:

| Máquina | Modelo recomendado | Por qué |
|---|---|---|
| Servidor sin GPU (uso remoto de Gestión/Administrador — extracción ocasional de datos) | `qwen2.5-coder:7b` (por defecto) | Chico, corre aceptable en CPU; la extracción es una acción esporádica, no interactiva |
| Máquina del Supervisor con GPU potente (chat interactivo del Copiloto) | `qwen2.5:32b` | Modelos de ~30-40B siguen instrucciones de forma mucho más confiable que los de 7B — necesario para el chat conversacional, no solo para extraer datos |

Para cambiar de modelo: `ollama pull <modelo>` en esa máquina, y ajustar `OLLAMA_MODEL` en
su `.env`. No requiere ningún cambio de código — el backend es agnóstico al modelo.

## 4. Ejecutar

```bash
mvn spring-boot:run
# o
mvn clean package && java -jar target/sicot-backend-0.1.0.jar
```

- API: <http://localhost:8080/api>
- Swagger UI: <http://localhost:8080/swagger-ui.html>
- Actuator: <http://localhost:8080/actuator/health>

### Problemas frecuentes al arrancar

| Síntoma | Causa | Solución |
|---|---|---|
| `Fatal error compiling: error: release version 25 not supported` | La máquina tiene un JDK anterior (p. ej. 21). El proyecto compila con `--release 25` | Instalar JDK 25 (`winget install EclipseAdoptium.Temurin.25.JDK`) y apuntar `JAVA_HOME` ahí. El build vía Docker no se ve afectado: usa su propia imagen JDK 25 |
| `Found more than one migration with version 1` | `target/classes` conserva una migración Flyway vieja que ya se eliminó del código fuente (Maven no borra artefactos huérfanos al renombrar un archivo) | `mvn clean` antes de volver a arrancar. **Correr `mvn clean` siempre después de un pull que consolide o renombre migraciones** |
| `Migration checksum mismatch` / `Detected applied migration not resolved locally` | La base local trae un historial de Flyway anterior a la consolidación de migraciones | Coordinar con quien administra la base antes de tocar `flyway_schema_history`; sobre una base local desechable, lo más simple es recrearla (`docker compose down -v`) |
| `IaNoDisponibleException` al usar el Copiloto | Ollama no está corriendo | `ollama serve` en la misma máquina que el backend, y verificar `OLLAMA_URL` |

## 5. Autenticación

1. `POST /api/auth/login` con `{"email": "...", "password": "..."}` → devuelve el `token`.
2. Enviar el token en cada petición: `Authorization: Bearer <token>`.

## 6. Endpoints principales

| Método | Ruta | Rol |
|---|---|---|
| POST | `/api/auth/login` | público |
| GET/POST/PUT | `/api/usuarios`, `/api/usuarios/{id}`, `PATCH /api/usuarios/{id}/estado` | ADMINISTRADOR |
| GET | `/api/contratos` (`?supervisorId=&estado=`) | autenticados |
| GET | `/api/contratos/{id}` | autenticados |
| POST/PUT | `/api/contratos`, `/api/contratos/{id}` | GESTION, ADMINISTRADOR |
| PATCH | `/api/contratos/{id}/supervisor` · `/api/contratos/{id}/estado` | GESTION, ADMINISTRADOR |
| GET | `/api/contratos/{id}/etapas`, `/api/etapas/{id}/subetapas` | autenticados |
| PATCH | `/api/subetapas/{id}/estado` | SUPERVISOR, GESTION, ADMINISTRADOR |
| GET | `/api/contratos/{id}/documentos` | autenticados |
| POST | `/api/contratos/{id}/documentos` (multipart, carga real) | GESTION, ADMINISTRADOR |
| GET | `/api/contratos/{id}/documentos/{docId}/archivo` (descarga real) | autenticados |
| POST | `/api/contratos/{id}/documentos/generar` (redacta el documento con IA) | SUPERVISOR, ADMINISTRADOR |
| POST | `/api/contratos/{id}/documentos/{docId}/firmar` | SUPERVISOR asignado, ADMINISTRADOR |
| POST | `/api/contratos/{id}/copiloto/chat` (chat real sobre Ollama) | SUPERVISOR asignado, ADMINISTRADOR |
| POST | `/api/ia/extraer-contrato` (multipart PDF; propone campos, **no persiste**) | GESTION, ADMINISTRADOR |
| GET/PATCH | `/api/contratos/{id}/alertas`, `/api/alertas/{id}/leida` | autenticados |
| GET | `/api/contratos/{id}/registros`, `/api/registros` | autenticados (todos: ADMINISTRADOR) |
| GET | `/api/formatos` | autenticados |
| POST | `/api/formatos` (multipart: `codigo`, `nombre`, `archivo`) | ADMINISTRADOR |
| GET | `/api/formatos/{id}/archivo` (descarga real del archivo) | autenticados |
| DELETE | `/api/formatos/{id}` | ADMINISTRADOR |
| GET | `/api/listas-chequeo` (`?tipo=MODALIDAD_SELECCION\|TRAMITE_CONTRACTUAL\|TRAMITE_PAGO`) | autenticados |
| GET | `/api/listas-chequeo/{codigo}` (ej. `GCCON-F-053`) | autenticados |
| GET | `/api/firmas` · `/api/firmas/mia` | ADMINISTRADOR · autenticados |
| POST/PATCH | `/api/firmas`, `/api/firmas/{id}/estado` | ADMINISTRADOR |
| POST | `/api/usuarios/{id}/enviar-credenciales` | ADMINISTRADOR |

**Reglas de negocio destacadas:**
- Al cambiar el estado de una subetapa se recalcula automáticamente el porcentaje y estado de su etapa (todas COMPLETADA → etapa COMPLETADA 100%).
- Solo usuarios con rol SUPERVISOR pueden ser asignados como supervisores.
- Número de contrato y email de usuario son únicos.
- Toda operación de trámite queda registrada en la tabla `registros` (auditoría).
- El catálogo de listas de chequeo es de solo lectura y se sirve desde
  `src/main/resources/listas-chequeo/`: es el texto de un formato institucional, no un dato
  transaccional. Ver [docs/producto/LISTAS_DE_CHEQUEO.md](../docs/producto/LISTAS_DE_CHEQUEO.md).

## 7. Estructura del proyecto

```
co.sena.sicot
├── config/      → OpenAPI, DataInitializer (usuarios de desarrollo)
├── controller/  → REST + Swagger
├── dto/         → request/response (validación con Bean Validation)
├── entity/      → JPA + enums (Rol, EstadoContrato, EstadoEtapa, …)
├── exception/   → errores y manejador global (@RestControllerAdvice)
├── ia/          → Copiloto IA: OllamaClient (único punto de salida hacia Ollama),
│                  ExtraccionContratoService, GeneracionDocumentoService,
│                  CopilotoChatService, PlantillaDocumentoIA, PdfTextExtractor,
│                  SimplePdfWriter
├── mapper/      → entidad ↔ DTO
├── repository/  → Spring Data JPA
├── security/    → JWT, filtro, CORS, BCrypt, autorización por rol y por contrato
└── service/     → lógica de negocio (incluye GcconP010Plantilla: las 6 etapas/27 subetapas,
                   y ListaChequeoService: catálogo de listas de chequeo oficiales)

src/main/resources/listas-chequeo/  → las 8 listas de chequeo oficiales en JSON
tools/extraer_listas_chequeo.py     → regenera ese catálogo desde los .xlsx de CompromISO
```

## 8. Pruebas

```bash
mvn test
```

Las pruebas de integración usan **H2 en memoria** (perfil `test`); las migraciones Flyway se validan contra PostgreSQL real al ejecutar la aplicación.

## 9. Pendiente (fases siguientes)

- Integración SECOP II (consulta de procesos)
- Firma electrónica con proveedor PKI real (hoy es una referencia interna registrada en el
  sistema, no una integración con un proveedor externo de firma)
- OCR de documentos escaneados sin texto legible (PaddleOCR)
- RAG (base vectorial) para que el Copiloto consulte documentos largos en vez de solo el
  contexto que ya recibe en el prompt
- Despliegue del lado "remoto" (servidor de la sala) con HTTPS y dominio propio
