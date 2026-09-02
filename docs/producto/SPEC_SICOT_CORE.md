# Especificación funcional — SICOT (núcleo del sistema)

**Creado**: 2026-08-25 · **Estado**: descripción retroactiva del sistema tal como existe

## Para qué sirve este documento

Es la **única descripción consolidada de qué hace SICOT**: qué usuarios lo usan, qué debe
cumplir, y dónde están los límites de su alcance. Se escribió *hacia atrás* — a partir del código
real, no de un diseño previo — para poder medir el sistema construido contra un enunciado
explícito de lo que debería hacer.

Sus fuentes son documentación e inventario reales (`.github/copilot-instructions.md`,
`README.md`, `backend/README.md`, y el inventario de controladores/endpoints del código). **No
inventa alcance nuevo**: todo lo que aquí se afirma es verificable contra el repositorio.

> La autoridad normativa del proyecto sigue siendo [`.github/copilot-instructions.md`](../../.github/copilot-instructions.md).
> Este documento describe *qué hace* el sistema; aquel define *cómo se debe trabajar* sobre él.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - GESTIÓN crea y asigna contratos (Priority: P1)

Un usuario con rol GESTIÓN crea un contrato en el sistema, opcionalmente extrayendo los datos
desde un PDF real vía IA, y lo asigna a un supervisor.

**Why this priority**: Sin un contrato creado y asignado, ningún otro flujo (supervisión, IA,
alertas) tiene con qué operar. Es el punto de entrada de todo el sistema.

**Independent Test**: Login como `gestion@soy.sena.edu.co`, crear un contrato (con o sin
extracción PDF), asignarle un supervisor real, confirmar que aparece en `GET /api/contratos`.

**Acceptance Scenarios**:

1. **Given** un PDF real de un contrato, **When** GESTIÓN lo sube a extracción IA, **Then** el
   sistema devuelve los campos propuestos sin persistir nada hasta confirmación manual.
2. **Given** un contrato creado, **When** GESTIÓN le asigna un supervisor, **Then** el contrato
   pasa a tener 6 etapas/27 subetapas GCCON-P-010 con la subetapa 1.1 en `EN_CURSO`.

---

### User Story 2 - SUPERVISOR ejecuta el flujo GCCON-P-010 con el Copiloto IA (Priority: P1)

Un usuario con rol SUPERVISOR avanza su contrato asignado a través de las 6 etapas del proceso
GCCON-P-010, generando y firmando electrónicamente los documentos formales que corresponde a
cada etapa, y consultando al Copiloto IA (chat real sobre Ollama) para dudas del proceso.

**Why this priority**: Es el uso diario real del sistema — sin esto, SICOT es solo un CRUD de
contratos sin el acompañamiento que le da su nombre.

**Independent Test**: Login como `supervisor@soy.sena.edu.co`, avanzar un contrato real por sus
subetapas, generar y firmar un documento IA (ej. Acta de Inicio 2.7), y hacer una pregunta al
chat del Copiloto que no debe alucinar funciones inexistentes.

**Acceptance Scenarios**:

1. **Given** una subetapa de verificación (no documento formal), **When** el supervisor la marca
   como completada, **Then** el backend recalcula el estado/porcentaje de la etapa automáticamente.
2. **Given** una subetapa que genera un documento formal (2.7, 3.4, 4.3, 5.3, 6.3), **When** el
   supervisor la alcanza, **Then** el Copiloto genera el documento vía Ollama y el supervisor solo
   lo firma con su firma electrónica real asignada.
3. **Given** el supervisor llega al último sub-paso pendiente de una etapa, **When** intenta
   cerrarla, **Then** el Copiloto pide una descripción de lo verificado y da una revisión asesora
   (nunca bloqueante) antes de permitir la confirmación humana final.
4. **Given** una pregunta directa sobre cómo registrar una verificación, **When** el supervisor
   pregunta al chat, **Then** el Copiloto NUNCA sugiere subir/cargar archivos en un sub-paso que
   no lo soporta — solo existe "Marcar completado".

---

### User Story 3 - ADMINISTRADOR gestiona usuarios, formatos y firmas (Priority: P2)

Un usuario con rol ADMINISTRADOR crea/activa/desactiva usuarios, envía credenciales reales,
administra el catálogo de formatos documentales oficiales, y asigna firmas electrónicas a las
cuentas.

**Why this priority**: Soporta la operación del sistema (altas de usuarios, formatos vigentes)
pero no bloquea el uso diario de GESTIÓN/SUPERVISOR.

**Independent Test**: Login como `administrador@soy.sena.edu.co`, crear un usuario, enviarle
credenciales reales, subir un formato documental, asignar una firma electrónica.

**Acceptance Scenarios**:

1. **Given** un usuario nuevo, **When** el administrador envía credenciales, **Then** se entregan
   por un canal real (no simulado).
2. **Given** una firma electrónica asignada a una cuenta, **When** un documento IA se firma,
   **Then** usa esa firma real — nunca un código aleatorio del lado del cliente.

---

### User Story 4 - Autenticación y seguridad por rol (Priority: P1)

Cualquier usuario inicia sesión con JWT y solo accede a lo que su rol permite; el sistema se
protege contra fuerza bruta y valida archivos por contenido real, no solo extensión.

**Why this priority**: Es prerrequisito transversal — ninguna otra historia funciona sin esto.

**Independent Test**: Login con cada uno de los 3 roles, confirmar que cada uno solo ve/hace lo
que su rol permite (ej. un SUPERVISOR no puede consultar el Copiloto de un contrato ajeno).

**Acceptance Scenarios**:

1. **Given** varios intentos de login fallidos, **When** se supera el límite, **Then** el email
   queda bloqueado temporalmente.
2. **Given** un archivo renombrado con extensión falsa, **When** se sube como documento/formato,
   **Then** el sistema lo rechaza por sniffing real de contenido (Apache Tika).

### Edge Cases

- ¿Qué pasa si un SUPERVISOR no tiene ningún contrato asignado? → estado vacío honesto, sin
  asumir con `!` que existe una etapa.
- ¿Qué pasa si Ollama no está disponible cuando se pide generar un documento o chatear? →
  `IaNoDisponibleException`, error honesto, nunca un resultado inventado.
- ¿Qué pasa si un contrato antiguo no tiene etapas (creado antes del fix de auto-siembra)? →
  auto-sanación al leerlo (`EtapaService.listarPorContrato`).

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema DEBE autenticar usuarios vía `POST /api/auth/login` devolviendo JWT y
  restringir cada endpoint por rol (`ADMINISTRADOR`, `GESTION`, `SUPERVISOR`).
- **FR-002**: El backend DEBE ser la única autoridad de permisos, roles, estados y reglas de
  negocio — el frontend no infiere ni inventa nada de esto.
- **FR-003**: GESTIÓN DEBE poder crear contratos (`POST /api/contratos`), listar/filtrar
  (`GET /api/contratos`), asignar supervisor (`PATCH /api/contratos/{id}/supervisor`) y cambiar
  estado (`PATCH /api/contratos/{id}/estado`).
- **FR-004**: Al crear un contrato, el sistema DEBE sembrar automáticamente las 6 etapas / 27
  subetapas reales de GCCON-P-010 (`GcconP010Plantilla`), con auto-sanación para contratos que
  quedaron sin etapas.
- **FR-005**: GESTIÓN DEBE poder extraer datos de un contrato real desde un PDF vía IA
  (`POST /api/ia/extraer-contrato`) sin que el sistema persista nada hasta confirmación manual.
- **FR-006**: SUPERVISOR DEBE poder generar documentos formales vía IA
  (`POST /api/contratos/{id}/documentos/generar`) y firmarlos con su firma electrónica real
  (`POST /api/contratos/{id}/documentos/{id}/firmar`), nunca con un código simulado.
- **FR-007**: El sistema DEBE exponer un chat conversacional real sobre Ollama
  (`POST /api/contratos/{id}/copiloto/chat`), anclado a los datos reales del contrato y sus
  etapas, restringido al supervisor asignado o a ADMINISTRADOR.
- **FR-008**: El Copiloto NUNCA DEBE sugerir una función de carga de archivos por sub-paso que no
  existe — la única acción en sub-pasos de verificación es "Marcar completado".
- **FR-009**: Antes de cerrar el último sub-paso de una etapa, el sistema DEBE pedir una revisión
  asesora de IA (no bloqueante) — la confirmación final siempre la da un humano.
- **FR-010**: El sistema DEBE calcular una alerta de cronograma tipo semáforo (verde/amarillo/
  rojo) en vivo a partir de fechas reales del contrato, sin infraestructura de scheduling.
- **FR-011**: ADMINISTRADOR DEBE poder gestionar usuarios (`/api/usuarios`), enviar credenciales
  reales (`POST /api/usuarios/{id}/enviar-credenciales`), administrar el catálogo de formatos
  documentales (`/api/formatos`) y asignar firmas electrónicas (`/api/firmas`).
- **FR-012**: El sistema DEBE bloquear temporalmente un email tras varios intentos de login
  fallidos (fuerza bruta).
- **FR-013**: El sistema DEBE validar archivos subidos por su contenido real (no solo extensión)
  antes de aceptarlos.
- **FR-014**: Toda modificación estructural de base de datos DEBE hacerse vía migración Flyway
  versionada — nunca SQL manual fuera de ese mecanismo.
- **FR-015**: El sistema DEBE registrar en auditoría (`/api/registros`) los cambios de estado
  relevantes (contrato, etapa, subetapa).

### Key Entities

- **Usuario**: cuenta con rol (`ADMINISTRADOR`/`GESTION`/`SUPERVISOR`), credenciales, estado
  activo/inactivo.
- **Contrato**: número, objeto, valor, fechas, contratista, supervisor asignado, estado.
- **Etapa / Subetapa**: las 6 etapas / 27 subetapas reales de GCCON-P-010, con estado
  (`PENDIENTE`/`EN_CURSO`/`COMPLETADA`) recalculado automáticamente por el backend.
- **Documento**: generado por IA o subido, con estado, firma asociada, contenido real (bytea).
- **FirmaElectronica**: firma "de referencia" asignada por un administrador a una cuenta.
- **FormatoDocumental**: catálogo de formatos oficiales administrado por ADMINISTRADOR.
- **Alerta**: alertas reales persistidas + alerta de cronograma computada en vivo.
- **Registro**: auditoría de cambios de estado.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Los 3 roles pueden iniciar sesión y solo acceder a las capacidades de su rol
  (verificado con pruebas de acceso cruzado).
- **SC-002**: Un contrato nuevo tiene 6 etapas / 27 subetapas reales inmediatamente después de
  crearse, sin pasos manuales adicionales.
- **SC-003**: El Copiloto responde a preguntas trampa sobre "dónde subir" un archivo en un
  sub-paso de verificación sin alucinar una función inexistente (re-verificado 2026-08-24,
  ver Linear MDL-42).
- **SC-004**: Cada documento formal generado por IA queda firmado con la firma electrónica real
  de la cuenta del supervisor — nunca con un valor simulado del lado del cliente.
- **SC-005**: `mvn clean test` pasa en verde sobre el código backend actual.

## Assumptions

- El proceso GCCON-P-010 de 6 etapas/27 subetapas ya está corregido según los 3 hallazgos de
  `[[project-sicot-gccon-p010-grounded]]` (F-030 = Informe Final no Acta de Liquidación, GRF-F-089
  no lo firma el supervisor, "ESUCON" sin código oficial confirmado).
- Ollama corre localmente (`qwen2.5:7b` en esta máquina de desarrollo — ver docs/decisiones/ADR-006) — no hay dependencia
  de un servicio de IA de pago, por la regla de herramientas gratuitas del proyecto.
- La subida de documentos de asignación de contrato (Carta de notificación, GCCON-F-031, Acta de
  Inicio real que SENA envía al designar supervisor) está **fuera de alcance** de este spec —
  identificada pero no construida (backlog Linear MDL-61/MDL-47), requiere aprobación explícita
  antes de implementarse.
- El empaquetado de escritorio para SUPERVISOR (Tauri) y la decisión online/offline están fuera
  de alcance — bloqueados en una decisión de arquitectura pendiente (Linear MDL-59/MDL-66).
