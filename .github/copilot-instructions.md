# SICOT — Instrucciones de desarrollo para GitHub Copilot Agent

## 1. Identidad del proyecto

SICOT significa:

**Sistema Inteligente para la Gestión y Acompañamiento de Contratos**

Es una plataforma para gestionar el ciclo de vida de contratos, documentación, etapas, alertas, registros y posteriormente capacidades de inteligencia artificial.

El proyecto está dividido en:

```text
SICOT
├── frontend
├── backend
└── mcp
```

El workspace contiene los tres proyectos y deben tratarse como un único sistema.
Además hay documentación en `docs/`.

---

# 2. Regla principal

## ESTABILIDAD > VELOCIDAD

El sistema ya tiene una integración funcional entre frontend y backend.

Antes de modificar cualquier cosa:

1. Inspeccionar.
2. Comprender.
3. Identificar dependencias.
4. Determinar impacto.
5. Proponer el cambio.
6. Esperar aprobación cuando el cambio sea arquitectónico, destructivo o afecte varios módulos.
7. Implementar el cambio mínimo necesario.
8. Ejecutar pruebas.
9. Revisar el diff.

Nunca modificar por comodidad algo que ya funciona.

---

# 3. Tecnologías oficiales

## Frontend

* React 19
* ReactDOM 19
* TypeScript
* Vite
* Tailwind CSS v4
* React Router (ver §5)
* Recharts
* Fetch API
* Node.js 22
* puerto de desarrollo: 8443

> **Las versiones exactas no se copian aquí.** La lista de arriba se quedó
> anunciando Spring Boot 3.5.12 meses después de que el pom declarara otra, y un
> agente que se fía de este archivo razona sobre una versión que no existe en el
> proyecto. La fuente de verdad es `frontend/package.json` y `backend/pom.xml`:
> léalos cuando la versión importe.

## Backend

* Java 25
* Spring Boot 3
* Spring Web
* Spring Data JPA
* Hibernate
* Spring Security
* JWT
* BCrypt
* Flyway
* Maven
* PostgreSQL 18

## Base de datos

Base:

```text
sicot
```

Backend:

```text
http://localhost:8080
```

Frontend:

```text
http://localhost:8443
```

---

# 4. No cambiar tecnologías

No agregar ni reemplazar:

* Axios
* React Query
* Redux
* Zustand
* Prisma
* Node backend
* otro ORM
* otro framework frontend

salvo que exista una razón técnica real y esté escrita en un ADR.

React Router **sí** está en el proyecto desde
[ADR-007](../docs/decisiones/ADR-007-enrutado-y-enlaces-profundos.md): no es una
dependencia que haya que quitar ni evitar (ver §5).

La capa HTTP del frontend utiliza `fetch`.

---

# 5. Frontend

La vista activa vive en la URL, con **React Router**
([ADR-007](../docs/decisiones/ADR-007-enrutado-y-enlaces-profundos.md),
aceptada). `frontend/src/main.tsx` monta `<BrowserRouter>` y
`frontend/src/App.tsx` declara las rutas:

| Ruta | Quién entra | Vistas |
| --- | --- | --- |
| `/login` | cualquiera | — |
| `/supervisor/:vista` | SUPERVISOR | `bandeja`, `contrato`, `alertas`, `documentos`, `registros` |
| `/gestion` | GESTION | una sola pantalla, sin sub-vista en la URL |
| `/admin/:vista` | ADMINISTRADOR | `dashboard`, `seguimiento`, `documentos`, `usuarios`, `firmas` |

Reglas que ya están implementadas y hay que conservar:

1. `/supervisor` y `/admin` redirigen a su vista por defecto (`bandeja`,
   `dashboard`); la raíz y cualquier ruta desconocida van al panel del rol de la
   sesión, o a `/login` si no hay sesión.
2. Entrar a la ruta de otro rol **redirige**, no muestra un error. La URL no es
   un control de acceso: la autoridad sobre permisos es el backend.
3. Una `:vista` desconocida cae en la vista por defecto en vez de romper
   (`vistaValida` en `App.tsx`).
4. `BrowserRouter` y no `HashRouter`: nginx sirve `index.html` para cualquier
   ruta profunda (`try_files` en `frontend/nginx.conf.template`), así que entrar
   directo a una URL funciona. Si se añade una ruta, esa configuración tiene que
   seguir cubriéndola.

**Ya no existe el tipo `Screen`** ni la navegación por estado que describía la
versión anterior de esta sección. Si encuentra una instrucción que diga «no
existe react-router» o que mande quitar rutas, está leyendo documentación vieja:
mándese por ADR-007.

---

# 6. Diseño visual

El frontend existente se considera diseño aprobado.

NO:

* rediseñar componentes;
* cambiar colores;
* cambiar tipografía;
* cambiar layouts;
* cambiar tamaños;
* eliminar tarjetas;
* eliminar botones;
* convertir pantallas en interfaces genéricas;
* simplificar la UI;
* reemplazar componentes visuales innecesariamente.

Las transformaciones backend → frontend deben realizarse preferentemente en:

```text
src/services/
```

o funciones de mapeo.

La UI debe recibir modelos adaptados.

---

# 7. Backend como autoridad funcional

El backend define:

* permisos;
* roles;
* estados;
* validaciones;
* contratos API;
* reglas de negocio;
* datos reales.

El frontend NO debe inventar estados ni reglas de negocio.

El frontend puede adaptar la representación visual.

---

# 8. Roles

Los únicos roles oficiales son:

```text
ADMINISTRADOR
GESTION
SUPERVISOR
```

No inventar otros roles.

---

# 9. Usuarios de desarrollo

Usuarios de desarrollo actuales:

```text
administrador@soy.sena.edu.co
Admin123*
ADMINISTRADOR
```

```text
gestion@soy.sena.edu.co
Gestion123*
GESTION
```

```text
supervisor@soy.sena.edu.co
Supervisor123*
SUPERVISOR
```

Son exclusivamente credenciales de desarrollo/demo.

No mostrarlas en la interfaz.

No enviarlas a producción.

No modificarlas sin aprobación.

---

# 10. Autenticación

Login:

```http
POST /api/auth/login
```

Request:

```json
{
  "email": "usuario@soy.sena.edu.co",
  "password": "..."
}
```

Response:

```json
{
  "token": "...",
  "usuarioId": 1,
  "nombre": "...",
  "email": "...",
  "rol": "..."
}
```

Las peticiones protegidas usan:

```http
Authorization: Bearer <token>
```

La sesión está persistida actualmente en:

```text
localStorage
```

La clave utilizada por el frontend es:

```text
sicot.session
```

No almacenar contraseñas.

No imprimir JWT completo en consola.

---

# 11. Endpoints existentes

## Auth

```http
POST /api/auth/login
```

## Usuarios

```http
GET /api/usuarios
GET /api/usuarios/{id}
POST /api/usuarios
PUT /api/usuarios/{id}
PATCH /api/usuarios/{id}/estado
POST /api/usuarios/{id}/enviar-credenciales
```

Los endpoints administrativos están protegidos por rol.

---

## Contratos

```http
GET /api/contratos
GET /api/contratos/{id}
POST /api/contratos
PUT /api/contratos/{id}
PATCH /api/contratos/{id}/supervisor
PATCH /api/contratos/{id}/estado
```

El listado soporta filtros:

```text
supervisorId
estado
```

---

## Etapas

```http
GET /api/contratos/{contratoId}/etapas
GET /api/contratos/{contratoId}/etapas/{etapaId}
```

---

## Subetapas

```http
GET /api/etapas/{etapaId}/subetapas
PATCH /api/subetapas/{id}/estado
```

El backend recalcula automáticamente el porcentaje/estado de la etapa.

---

## Documentos

```http
GET /api/contratos/{contratoId}/documentos
POST /api/contratos/{contratoId}/documentos                  (multipart, GESTION/ADMINISTRADOR)
GET /api/contratos/{contratoId}/documentos/{id}/archivo      (descarga real)
POST /api/contratos/{contratoId}/documentos/generar          (SUPERVISOR/ADMINISTRADOR, vía IA)
POST /api/contratos/{contratoId}/documentos/{id}/firmar      (solo el SUPERVISOR asignado; lo generado exige ?huellaRevisada=)
```

---

## Copiloto IA

```http
POST /api/contratos/{contratoId}/copiloto/chat
POST /api/contratos/{contratoId}/copiloto/precalentar
```

Solo el supervisor asignado al contrato o un ADMINISTRADOR pueden consultarlo (también el
precalentado, que lo comprueba antes de encolar).

`ChatResponse` lleva `respuesta`, `fuente` (`SISTEMA` si la armó SICOT sin modelo, `MODELO` si
la escribió Ollama) y `accion`: la pantalla que el supervisor puede abrir desde la respuesta
(`IR_A_PASO`, `IR_A_SUBPASO`, `ABRIR_DOCUMENTO`, `ABRIR_EVIDENCIA`, `MOSTRAR_ALERTAS`,
`MOSTRAR_DOCUMENTOS`, `DESCARGAR_DOCUMENTO`, `IR_A_CONFIGURACION`), o `null`. **Ninguna acción
firma, marca, genera ni modifica nada**: la interfaz la muestra como un botón y solo abre esa
pantalla cuando él lo toca. `ChatRequest` admite `idSolicitud` (el reintento recibe la respuesta
del primer intento) y `revisarPaso` (la revisión antes de cerrar un paso, armada en el servidor).

---

## IA (extracción)

```http
POST /api/ia/extraer-contrato    (multipart PDF, GESTION/ADMINISTRADOR)
```

Devuelve los campos propuestos. **Nunca persiste** — requiere confirmación manual.

---

## Firmas electrónicas

```http
GET /api/firmas
GET /api/firmas/mia
POST /api/firmas
PATCH /api/firmas/{id}/estado
```

---

## Formatos documentales

```http
GET /api/formatos
POST /api/formatos                  (multipart, ADMINISTRADOR)
GET /api/formatos/{id}/archivo      (descarga real)
DELETE /api/formatos/{id}           (ADMINISTRADOR)
```

---

## Alertas

```http
GET /api/contratos/{contratoId}/alertas
GET /api/alertas
PATCH /api/alertas/{id}/leida
```

---

## Registros

```http
GET /api/contratos/{contratoId}/registros
GET /api/registros
```

---

## Listas de chequeo

```http
GET /api/listas-chequeo
GET /api/listas-chequeo/{codigo}
```

Catálogo de solo lectura de las listas de chequeo documentales oficiales del SENA (GCCON por modalidad de selección y GRF-F-088 para trámite de pago).

---

# 12. Estados oficiales

## Contrato

Los estados provienen del backend.

No inventar nuevos valores.

## Etapa/Subetapa

Backend:

```text
PENDIENTE
EN_CURSO
COMPLETADA
```

Frontend puede mapearlos visualmente a:

```text
pending
active
completed
```

No cambiar esta representación visual si el mapeo existente funciona.

---

# 13. DTOs

Antes de construir cualquier request o response:

INSPECCIONAR el DTO real del backend.

Nunca inferir campos.

Nunca inventar nombres.

Nunca duplicar DTOs inconsistentes.

---

# 14. Base de datos

PostgreSQL:

```text
database: sicot
```

Las migraciones son administradas por Flyway.

NO:

* ejecutar DROP DATABASE;
* eliminar migraciones;
* modificar tablas manualmente sin necesidad;
* cambiar IDs existentes;
* borrar datos seed arbitrariamente.

Toda modificación estructural debe hacerse mediante migración Flyway.

---

# 15. Datos iniciales

**No existe seed de datos transaccionales.** Una base nueva arranca vacía: sin
contratos, sin etapas, sin documentos, sin alertas, sin registros. Las
migraciones `V1`/`V9` solo crean estructura.

Lo único que se crea automáticamente son las **tres cuentas de desarrollo** de
§9, y solo bajo el perfil `dev`, mediante `DataInitializer` y únicamente si la
tabla `usuarios` está vacía.

Las etapas de un contrato tampoco vienen de un seed: las genera
`GcconP010Plantilla` cuando el contrato se crea (las mismas 6 etapas y 27
sub-etapas para todos, sin depender del tipo de contrato).

---

# 16. Datos de prueba temporales

Cualquier contrato o documento que aparezca en una base local fue creado usando
la aplicación, no por una migración. Antes de borrar algo:

1. verificar IDs;
2. comprobar relaciones;
3. comprobar foreign keys;
4. confirmar que la base es local y no la de un servidor.

Nunca se borran datos con SQL directo: se borra usando la aplicación, que deja
su registro de auditoría (ver §33).

---

# 17. Funcionalidades realmente conectadas

Actualmente están conectadas al backend:

* login JWT;
* sesión;
* logout;
* contratos;
* detalle de contrato;
* etapas;
* subetapas (incluida la siembra automática de las 6 etapas/27 subetapas GCCON-P-010 al crear
  un contrato, con auto-sanación de contratos antiguos sin etapas);
* cambio de estado;
* alertas;
* marcar alertas;
* documentos (listar, **subir**, **descargar**);
* registros;
* Gestión de contratos;
* creación de contratos;
* usuarios;
* activación/desactivación;
* autorización por roles (incluido el control de acceso por contrato: un SUPERVISOR solo
  alcanza los datos del contrato que tiene asignado);
* **copiloto conversacional** (chat real sobre Ollama, anclado al contrato y sus etapas reales),
  con **órdenes** («llévame al paso 3», «genera el acta de inicio») que se atienden sin modelo y
  devuelven el botón para abrir la pantalla, nunca para firmar ni marcar;
* **tutorial guiado** de los 6 pasos, con gate de revisión IA asesor (nunca bloqueante) antes
  de cerrar un paso;
* **documentos formales armados por SICOT** con los datos exactos del contrato
  (`RedactorDeDocumentos`; el modelo solo redacta el apartado de observaciones);
* **evidencia fotográfica de la entrega** en los sub-pasos 3.1 y 3.2 (`EvidenciaFotografica`;
  el backend lee de la foto la fecha y el lugar de la toma);
* **firma electrónica** de referencia interna (usa la firma real asignada a la cuenta, nunca un
  valor generado en el cliente);
* **firmas electrónicas administrativas** (asignación por parte del ADMINISTRADOR);
* **lectura/extracción real de ficha PDF** (Apache PDFBox + Ollama; PDFs con texto);
* **catálogo documental del administrador** (formatos: subida, descarga y borrado reales);
* **notificación/entrega real de credenciales por correo**;
* **alerta de cronograma tipo semáforo**, computada en vivo desde las fechas reales del
  contrato;
* **enrutado por URL con enlaces profundos compartibles** (React Router, ADR-007 — ver §5);
* **empaquetado de escritorio y de Android con Tauri 2**: `frontend/src-tauri` está
  versionado, el instalador de Windows se construye con `npm run tauri:build`
  (destinos `nsis` y `msi` en `tauri.conf.json`) y el APK se arma y se firma en
  `.github/workflows/android.yml` y `publicar-apk.yml`. Decidido en
  [ADR-012](../docs/decisiones/ADR-012-aplicacion-movil.md) y
  [ADR-013](../docs/decisiones/ADR-013-firma-y-distribucion-del-apk.md), las dos
  aceptadas. **No desaconseje tocar `src-tauri` ni diga que la aplicación de
  escritorio no existe.**

---

# 18. Funcionalidades todavía NO implementadas

Estas funcionalidades NO tienen backend funcional y NO deben fingirse como reales:

* integración SECOP II;
* OCR de documentos escaneados sin texto legible (la extracción actual requiere un PDF con
  texto — un escaneo puro no se lee);
* extracción desde DOCX (hoy solo PDF; `ExtraccionContratoService` rechaza otros formatos con
  un mensaje claro en vez de fingir que los leyó);
* RAG normativo / detección automática de inconsistencias documentales;
* gráfica avanzada de actividad del panel de administrador (no existe endpoint de estadísticas
  agregadas; el panel muestra un estado vacío honesto, no números inventados);
* carga de archivos en los sub-pasos de verificación del supervisor (**no existe**, con una sola
  excepción: en 3.1 y 3.2 se cargan **fotos de la entrega** con «Cargar evidencia». En todos los
  demás la única acción es "Marcar completado" — el Copiloto tiene prohibido explícitamente
  sugerir lo contrario);
* firma electrónica con un proveedor PKI externo real (lo actual es una referencia interna de
  SICOT, no una integración con infraestructura nacional de firma);
* funcionamiento sin conexión de la aplicación de escritorio o del APK: las dos hablan
  con el backend por HTTP y no funcionan sin red, por decisión de
  [ADR-001](../docs/decisiones/ADR-001-bifurcamiento-de-despliegue.md) (el
  empaquetado en sí **sí** está hecho, ver §17);
* que el Copiloto **ejecute** algo: no firma, no genera documentos, no marca sub-pasos ni cambia
  ajustes o datos. Una orden en el chat solo devuelve una acción que abre la pantalla donde el
  supervisor decide; el prompt le prohíbe al modelo decir que hizo algo.

NO inventar endpoints para ellas.

---

# 19. Riesgos conocidos

## Riesgo 1

Supervisor sin contrato ACTIVO o sin etapas podría causar errores si el código asume que existe una etapa determinada.

Debe manejarse con estados vacíos.

Nunca utilizar `!` de forma insegura sobre datos backend.

---

## Riesgo 2

No mostrar alertas mock como si fueran alertas reales.

Si backend devuelve:

```text
[]
```

eso significa:

```text
No hay alertas reales.
```

No reemplazar automáticamente con datos inventados.

---

## Riesgo 3

Producción.

Los secretos de:

```text
DB_PASSWORD
JWT_SECRET
```

deben venir de variables de entorno.

Nunca incluir secretos de producción en Git.

---

## Riesgo 4

Credenciales de desarrollo.

Los usuarios seed son únicamente para DEV/DEMO.

---

# 20. Mapeo de datos

Preferir:

```text
Backend DTO
    ↓
Mapper / Service
    ↓
Frontend Model
    ↓
UI
```

No poner transformaciones complejas dentro de JSX.

---

# 21. Fechas

Backend utiliza ISO.

Frontend puede mostrar:

```text
dd/mm/aaaa
```

Utilizar una función común.

No usar múltiples formatos distintos.

---

# 22. Moneda

Los valores económicos deben mostrarse correctamente en COP.

No cambiar valores numéricos por strings antes de realizar cálculos.

Formatear únicamente en presentación.

---

# 23. Manejo de errores

El backend utiliza respuestas JSON con:

```text
message
fieldErrors
```

El frontend debe interpretar:

```text
400
401
403
404
409
500
```

No mostrar stack traces al usuario.

Los errores de autenticación deben limpiar sesión cuando corresponda.

---

# 24. CORS

Frontend:

```text
http://localhost:8443
```

Backend debe aceptar ese origen.

No solucionar CORS haciendo:

```text
*
```

sin una razón concreta.

---

# 25. Comandos oficiales

Frontend:

```powershell
npm.cmd install
npm.cmd run dev
npm.cmd run build
```

TypeScript:

```powershell
npx tsc --noEmit
```

Backend:

```powershell
.\mvnw.cmd clean test
```

Ejecutar backend:

```powershell
.\mvnw.cmd spring-boot:run
```

---

# 26. Verificación antes de terminar cualquier tarea

Siempre que se modifique frontend:

```text
npm.cmd run typecheck
npm.cmd run build
```

Siempre que se modifique backend:

```text
.\mvnw.cmd clean verify
```

Si se modifican ambos: ejecutar las dos cosas.

`npm run build` ya incluye el chequeo de tipos (`tsc --noEmit && vite build`),
y `mvn verify` incluye las pruebas — no hace falta correrlas aparte.

## Pruebas end-to-end (Playwright)

```text
npm.cmd run test:e2e
```

**No corren en CI** y no forman parte de la verificación obligatoria: necesitan
el backend levantado, PostgreSQL y las cuentas sembradas del perfil `dev`.
Ejecutarlas a mano cuando se toque el flujo de autenticación o la navegación
entre paneles. Los specs están en `frontend/e2e/specs/`.

## Integración continua

`.github/workflows/ci.yml` corre estas mismas verificaciones en cada PR hacia
`develop` o `master`. Un PR con la CI en rojo no se mergea.

---

# 27. Regla de cambios mínimos

Antes de modificar un archivo:

* leerlo;
* buscar dependencias;
* entender su responsabilidad;
* modificar solamente lo necesario.

No reemplazar archivos completos cuando un cambio localizado sea suficiente.

---

# 28. Cambios arquitectónicos

Si una tarea requiere:

* nuevo módulo;
* nueva tabla;
* nueva migración;
* nuevo endpoint;
* cambio de DTO;
* cambio de seguridad;
* nueva dependencia;
* cambio de arquitectura;

primero explicar:

1. por qué;
2. impacto;
3. alternativas;
4. riesgo.

Luego esperar aprobación.

---

# 29. IA

La IA **ya está integrada** y forma parte del núcleo funcional. La arquitectura obligatoria,
ya implementada, es:

```text
React
 ↓
Spring Boot
 ↓
servicio de IA  (paquete co.sena.sicot.ia)
 ↓
Ollama (local)
```

El frontend **nunca** llama a Ollama directamente. Ollama **nunca** toca PostgreSQL. El backend
es el único responsable del contexto y de los permisos.

Piezas reales del paquete `co.sena.sicot.ia`:

* `OllamaClient` — único punto de salida hacia Ollama. Si Ollama no está disponible lanza
  `IaNoDisponibleException`: falla honestamente, **nunca fabrica un resultado**.
* `ExtraccionContratoService` — extrae datos de un contrato desde un PDF real (Apache PDFBox).
* `GeneracionDocumentoService` + `PlantillaDocumentoIA` + `RedactorDeDocumentos` — arman los
  documentos formales con los datos del contrato; el modelo solo redacta el apartado de
  observaciones.
* `CopilotoChatService` — chat conversacional anclado al contrato y a sus etapas reales.
* `OrdenDelSupervisor`, `FichaDeDocumentoFormal`, `GuiaDelPasoActual`, `FichaDelContrato` —
  contestan sin modelo lo que tiene respuesta fija, en ese orden: las órdenes (con la acción
  que abre la pantalla), qué es cada documento formal (código, sub-paso, quién firma), el paso
  en el que va el supervisor o el paso, sub-paso o documento que nombre, y los datos del
  contrato (valor, fechas, días que quedan, `Cronograma`). `CopilotoChatService` las consulta
  antes de llamar a Ollama. El flujo de firma lo dice `FlujoDeFirma`, un solo texto para la
  ficha, la guía, las órdenes y el prompt.
* `PdfTextExtractor`, `PdfInstitucional` — lectura y escritura real de PDF, las dos en
  `co.sena.sicot.ia`. `PdfInstitucional` sustituyó al antiguo `SimplePdfWriter`: arma el PDF
  con la cabecera, el pie y las tablas del formato institucional, no un texto suelto.

Reglas al trabajar sobre la IA:

* SICOT no usa ningún servicio de IA de pago — todo corre en un Ollama local
  (`OLLAMA_URL`/`OLLAMA_MODEL`). Cambiar de modelo es configuración, no código.
* La IA es **asesora, nunca autoridad**: su revisión antes de cerrar un paso no bloquea al
  supervisor; la decisión final siempre es de una persona.
* Los prompts describen la interfaz **tal como es** (ver §18): ni afirman capacidades que no
  tiene ni niegan las que sí tiene. El modelo ya alucinó una vez una función de carga de
  archivos inexistente, y por eso `CopilotoChatService.CONOCIMIENTO_PROCESO` prohíbe sugerir
  cargas fuera de las fotos de 3.1 y 3.2. No debilitar esa prohibición.
* Cuando la interfaz gana o pierde una capacidad, se actualizan **a la vez** el prompt, §18 y
  las guías deterministas (`frontend/src/data/guiaSubPaso.ts`, `GuiaDelPasoActual`). La carga
  de fotos de 3.1-3.2 existió una semana mientras el prompt seguía negándola, y el modelo
  contradecía a la guía del tutorial en el mismo sub-paso.
* Lo que tiene respuesta fija no se le pide al modelo (ADR-006, ADR-008): se compone con código
  y se prueba con el texto exacto. Al modelo le quedan las preguntas abiertas.

---

# 30. Regla de oro — NO INVENTAR

Es la regla más importante del proyecto. SICOT es un sistema institucional de
contratación pública: un dato falso presentado como real puede llevar a una
decisión equivocada sobre un contrato del Estado.

## 30.1 Qué prohíbe, en concreto

**Nunca mostrar como real algo que el sistema no sabe.** Formas concretas que
esto ha tomado en el pasado, todas encontradas en auditorías reales:

1. **Derivar un dato que no existe.** Se inventaban acuses de recibo
   ('Entregado' / 'Leído') a partir del nombre de una acción de auditoría.
   SICOT no rastrea entregas ni lecturas.
2. **Afirmar una acción que no ocurre.** Se decía "el supervisor ha sido
   notificado" cuando no se envía ningún aviso.
3. **Atribuir a la IA un trabajo humano.** "El Copiloto procesó y asignó
   automáticamente" cuando una persona llenó el formulario.
4. **Un control que aparenta hacer algo.** Un botón "Revisión IA" que no llamaba
   a ninguna IA; un "Guardar cambios" que no guardaba nada.
5. **Presentar un fallo como un estado sano.** Lo más grave: si falla la
   consulta de alertas, NO mostrar "sin alertas" en verde; si falla la del
   contrato, NO mostrar "no tiene contrato asignado"; si fallan las métricas, NO
   mostrar `0`. Una caída debe verse como una caída.
6. **Inventar reglas del proceso institucional.** Códigos de formato,
   secuencias de etapas o firmantes que no estén confirmados en la
   documentación real del SENA.

## 30.2 Qué hacer en su lugar

* Si el dato no existe: **no mostrar el campo**.
* Si la consulta falló: **decir que falló**, y ofrecer reintentar.
* Si la función no está implementada: **deshabilitar el control con un `title`**
  que lo explique (patrón ya usado en "Conectar SECOP II").
* Si un dato institucional no está confirmado: marcarlo
  `PENDIENTE_DE_DEFINIR`, nunca adivinarlo.
* Un estado vacío honesto siempre es preferible a un dato inventado.

## 30.3 Cómo verificarlo

Antes de dar una tarea por terminada, **levantar la aplicación con el backend
apagado** y recorrer las pantallas afectadas. Si alguna muestra un verde, un
cero o un vacío tranquilizador en vez de un error, hay una violación de esta
regla.

## 30.4 Cuando algo no esté claro

Inspeccionar código, buscar el DTO, buscar el endpoint, buscar la migración,
revisar el modelo, comprobar el comportamiento real. Si aun así no está claro:
**detenerse y preguntar.**

---

# 31. Prioridades

Orden de prioridad:

1. Seguridad
2. Correctitud de datos
3. Integridad de backend
4. Integridad de frontend
5. Compatibilidad API
6. Tests
7. Rendimiento
8. Refactorización
9. Nuevas funcionalidades

---

# 32. Objetivo del agente

El agente debe comportarse como un ingeniero senior trabajando sobre un sistema existente.

NO como un generador que rehace el proyecto desde cero.

El código existente tiene valor.

Las funcionalidades existentes tienen valor.

La UI existente tiene valor.

La estabilidad tiene prioridad sobre cualquier mejora estética o refactorización.

---

# 33. Áreas que piden cuidado aparte

SICOT lo construye una sola persona: aquí no hay responsables a quienes pedir
permiso ni equipo con quien coordinar. Estas áreas piden cuidado por lo que pasa
si se tocan mal, no por de quién son.

| Área | Por qué | Regla |
|---|---|---|
| `backend/src/main/resources/db/migration/` | Una migración aplicada sobre datos reales no se deshace, y Flyway rechaza el arranque si cambia el checksum de una ya aplicada | Nunca se edita ni se renombra una migración existente: los cambios de esquema entran como una migración nueva. Tampoco SQL directo contra la base |
| `docs/decisiones/` | Un ADR fija el rumbo; contradecirlo en el código deja al proyecto con dos verdades | Si el trabajo necesita desviarse de un ADR, se escribe un ADR que lo sustituya; no se cambia el código y se deja el ADR viejo en pie |
| `.github/workflows/` | Son la única comprobación automática antes de mergear | Un workflow que se desactiva «para que pase» deja de proteger justo cuando hace falta |

Estas tres rutas están además en `.github/CODEOWNERS`, así que un PR que las
toque pide revisión explícita.

Si un trabajo necesita un cambio de esquema, se hace con una migración nueva y
se dice en el PR qué cambia y por qué.

Configuración personal (preferencias del editor, ajustes de herramientas de IA)
va en archivos locales ignorados por git, nunca en archivos versionados.

FIN DE LAS INSTRUCCIONES.
