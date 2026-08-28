# Tarea 1 — Validaciones de entrada · rama `fix/validaciones-de-entrada`

> Lee primero `.github/copilot-instructions.md` y `.claude/orca/CONTRATO_DEL_AGENTE.md`.

## Objetivo

Que **ningún** dato llegue a la capa de servicio o a la base sin haber pasado por
una restricción declarada. Hoy varias entradas se validan por accidente (porque
la columna de la base las rechaza) en vez de por diseño, y eso convierte un error
del usuario en un 409 genérico o en un dato basura persistido.

## Alcance — archivos que **posee** esta rama

- `backend/src/main/java/co/sena/sicot/dto/**` — todas las anotaciones de
  validación (`jakarta.validation`), **salvo `dto/ia/**`** (es de la tarea 3).
- La adición de `@Valid` / `@Validated` / restricciones sobre `@RequestParam` y
  `@PathVariable` en `controller/**`. Solo esas líneas.
- `backend/src/main/java/co/sena/sicot/service/ArchivoValidator.java` si el
  arreglo lo exige (coordina: la tarea 3 lo *usa*, no lo modifica).
- Pruebas nuevas bajo `backend/src/test/**` que cubran lo que arregles.

## Fuera de alcance

- Lógica de negocio en los servicios, transiciones de estado (tarea 2), el
  paquete `ia/**` y `dto/ia/**` (tarea 3), códigos HTTP y forma de las respuestas
  (tarea 4), borrado de código (tarea 6).
- **Migraciones Flyway.** Si una validación revela que una columna necesita otra
  longitud o un `NOT NULL`, **no la cambies**: repórtalo y sigue.

## Hallazgos ya verificados (punto de partida, no lista cerrada)

1. **`dto/contrato/CrearContratoRequest.java`** — 14 campos, 5 restricciones.
   `objeto` tiene `@NotBlank` pero **ningún `@Size`**, y los 8 campos opcionales
   (`tipoContrato`, `contratista`, `contratistaNit`, `representanteLegal`,
   `lugarEjecucion`, `numeroRegistroPresupuestal`, `centroCosto`…) no tienen
   ninguna. Un texto largo llega a la base y se convierte en 409 en vez de en un
   400 con `fieldErrors` que le diga al usuario qué campo corregir.
2. **`dto/contrato/ActualizarContratoRequest.java`** — mismo patrón (13 campos,
   5 restricciones). Las dos deben quedar coherentes entre sí: el mismo campo no
   puede aceptar 200 caracteres al crear y 2000 al actualizar.
3. **Sin validación entre campos**: se acepta `fechaFin` anterior a
   `fechaInicio`, y `fechaRegistroPresupuestal` sin relación con la vigencia.
   Es una regla aritmética, no institucional — no estás inventando proceso.
   Cualquier regla que **sí** sea institucional (qué campos son obligatorios en
   qué modalidad) va marcada `PENDIENTE_DE_DEFINIR`, no adivinada.
4. **`controller/FormatoDocumentalController.java:40-42`** — `POST /api/formatos`
   recibe `codigo` y `nombre` como `@RequestParam String` crudos, sin `@NotBlank`
   ni `@Size`. Un `codigo` en blanco entra al catálogo documental del
   administrador.
5. **`controller/DocumentoController.java:46-48`** — `nombre` sin límite de
   longitud; se persiste y luego se emite en el header `Content-Disposition` de
   la descarga.
6. **`@Validated` ausente**: hoy ningún controlador lo declara a nivel de clase,
   así que las restricciones sobre `@RequestParam`/`@PathVariable` que añadas
   **no se ejecutarán** si no lo agregas. Verifícalo con una prueba, no de vista.
7. `dto/firma/CrearFirmaRequest`, `dto/etapa/ActualizarEstadoSubetapaRequest`,
   `dto/usuario/*` — revísalos; los de usuario ya están bien cubiertos (9-10
   restricciones), sirven de referencia de estilo.

## Cómo trabajar

1. Inventaria: para cada DTO de request y cada `@RequestParam`, anota campo,
   tipo, si es obligatorio y qué lo limita hoy (base, servicio, nada).
2. Arregla por grupos coherentes, un commit por grupo (contratos, formatos,
   documentos, …).
3. Mensajes de error **en español, dirigidos al usuario**, en el mismo tono que
   los existentes ("El valor debe ser mayor a cero."). Nunca expongas nombres de
   columna ni detalles internos.
4. Cada restricción nueva necesita una prueba que demuestre el 400 con su
   `fieldErrors`. `GlobalExceptionHandler` ya devuelve ese contrato — úsalo, no
   lo cambies (eso es tarea 4).

## Criterios de aceptación

- [ ] Toda entrada de escritura tiene restricción declarada o una razón escrita
      de por qué no la lleva.
- [ ] Ningún límite nuevo es más estricto que la columna de la base (eso rompería
      datos existentes) ni más laxo (eso deja pasar el 409).
- [ ] Las restricciones sobre parámetros realmente se disparan (probado).
- [ ] `cd backend; .\mvnw.cmd -B -ntp verify` en verde.
- [ ] Ninguna migración tocada.
