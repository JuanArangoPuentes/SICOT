# SICOT Backend

Backend del **Sistema Inteligente para la Gestión y Acompañamiento de Contratos** (SENA — Centro Tecnológico del Mobiliario).

Spring Boot 3.x · Java 21 · PostgreSQL · JWT · Flyway · Swagger/OpenAPI.

> ⚠️ **Fase actual:** monolito modular con autenticación JWT, CRUD de usuarios/contratos, flujo de etapas GCCON-P-010, alertas, documentos, auditoría, **Copiloto IA real (Ollama)** — extracción de datos de contrato, chat conversacional con memoria, redacción de documentos formales —, firma electrónica (referencia interna) y entrega de credenciales por correo. Pendiente: integración SECOP II y OCR de documentos escaneados.

---

## 1. Requisitos

| Herramienta | Versión |
|---|---|
| JDK | 21+ (el proyecto compila con `--release 21`) |
| Maven | 3.9+ |
| PostgreSQL | 14+ (probado con 18) |

## 2. Base de datos

```sql
-- (con el usuario superusuario de PostgreSQL)
CREATE USER sicot WITH PASSWORD 'sicot_dev_password';
CREATE DATABASE sicot OWNER sicot;
```

El esquema se crea automáticamente con **Flyway** al primer arranque:
- `V1__create_initial_schema.sql` — esquema (usuarios, contratos, etapas, subetapas, documentos, alertas, registros)
- `V2__seed_dev_data.sql` — datos de desarrollo (3 contratos de ejemplo con el flujo GCCON-P-010)
- `V3__clear_demo_transactional_data.sql` — limpia los datos transaccionales de ejemplo (deja el esquema y los usuarios)
- `V4__create_formatos_documentales.sql` — catálogo de formatos documentales oficiales (carga real de archivos)

Los **usuarios** se crean al arrancar (solo si la tabla está vacía) por `DataInitializer` con contraseñas codificadas en BCrypt:

| Email | Rol | Contraseña (solo desarrollo) |
|---|---|---|
| `administrador@soy.sena.edu.co` | ADMINISTRADOR | `Admin123*` |
| `gestion@soy.sena.edu.co` | GESTION | `Gestion123*` |
| `supervisor@soy.sena.edu.co` | SUPERVISOR | `Supervisor123*` |

## 3. Configuración (variables de entorno)

Copie `.env.example` a un `.env` (o exporte las variables) — los valores por defecto en `application.properties` solo funcionan en desarrollo:

| Variable | Descripción |
|---|---|
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
| GET | `/api/contratos/{id}/documentos` | autenticados (carga real de archivos por contrato en fase posterior) |
| GET/PATCH | `/api/contratos/{id}/alertas`, `/api/alertas/{id}/leida` | autenticados |
| GET | `/api/contratos/{id}/registros`, `/api/registros` | autenticados (todos: ADMINISTRADOR) |
| GET | `/api/formatos` | autenticados |
| POST | `/api/formatos` (multipart: `codigo`, `nombre`, `archivo`) | ADMINISTRADOR |
| GET | `/api/formatos/{id}/archivo` (descarga real del archivo) | autenticados |
| DELETE | `/api/formatos/{id}` | ADMINISTRADOR |

**Reglas de negocio destacadas:**
- Al cambiar el estado de una subetapa se recalcula automáticamente el porcentaje y estado de su etapa (todas COMPLETADA → etapa COMPLETADA 100%).
- Solo usuarios con rol SUPERVISOR pueden ser asignados como supervisores.
- Número de contrato y email de usuario son únicos.
- Toda operación de trámite queda registrada en la tabla `registros` (auditoría).

## 7. Estructura del proyecto

```
co.sena.sicot
├── config/      → OpenAPI, DataInitializer (usuarios de desarrollo)
├── controller/  → REST + Swagger
├── dto/         → request/response (validación con Bean Validation)
├── entity/      → JPA + enums (Rol, EstadoContrato, EstadoEtapa, …)
├── exception/   → errores y manejador global (@RestControllerAdvice)
├── mapper/      → entidad ↔ DTO
├── repository/  → Spring Data JPA
├── security/    → JWT, filtro, CORS, BCrypt, autorización por rol
└── service/     → lógica de negocio
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
