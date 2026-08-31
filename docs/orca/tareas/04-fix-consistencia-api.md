# Tarea 4 — Consistencia de la API · rama `fix/consistencia-api`

> Lee primero `.github/copilot-instructions.md` (§11, §13, §23) y
> `.claude/orca/CONTRATO_DEL_AGENTE.md`.

## Objetivo

Que la API se comporte igual en todas partes: mismos códigos HTTP para las mismas
situaciones, mismas formas de respuesta, mismos nombres, y una documentación que
describa lo que el código hace de verdad. El frontend consume esta API con
`fetch` a mano (§4): cada inconsistencia se paga con un caso especial en
`frontend/src/services/`.

## Alcance — archivos que **posee** esta rama

- `backend/src/main/java/co/sena/sicot/controller/**` — firmas de método, códigos
  de estado, rutas, anotaciones OpenAPI. **No** las anotaciones de validación
  (tarea 1) ni la lógica de negocio.
- `backend/src/main/java/co/sena/sicot/exception/**`
- `backend/src/main/java/co/sena/sicot/config/OpenApiConfig.java`
- `backend/src/main/java/co/sena/sicot/service/DocumentoService.java` — **solo** el
  orden de comprobaciones dentro de `firmar` (ver brecha 2 abajo). Nada más de ese
  archivo.
- La sección §11 de `.github/copilot-instructions.md` y el `README.md` del
  backend, **solo** para alinearlos con la realidad del código.
- Pruebas nuevas bajo `backend/src/test/**` (ya existe
  `ContratoDeErroresIntegrationTest.java`, que es el patrón a seguir).

## Fuera de alcance

- Servicios, `ia/**`, DTOs de request (salvo renombrar un campo, y eso se avisa
  antes porque rompe al frontend), transiciones de estado, borrado de código.
- **No cambies un contrato que el frontend ya consume** sin dejarlo escrito y
  señalado en el resumen. La compatibilidad de API está por encima de la
  elegancia (§31): si un endpoint es feo pero funciona y el frontend depende de
  él, se documenta, no se "arregla".

## Hallazgos ya verificados (punto de partida, no lista cerrada)

1. **La paginación documentada no existe.** `backend/README.md` justifica la
   migración `V9__add_indices_fecha_alertas_registros.sql` diciendo que los
   índices por fecha "los usa la paginación", pero **ningún controlador acepta
   `Pageable`**: `GET /api/alertas` y `GET /api/registros` devuelven
   `List<...>` completa. En una base con historial real eso es una respuesta sin
   techo. Decide: o se implementa la paginación (cambia el contrato — hay que
   avisar al frontend), o se corrige la documentación. Lo que no puede quedar es
   la contradicción.
2. **Las creaciones no son coherentes entre sí** — corregido 2026-08-28, la
   versión anterior de este brief decía "todo devuelve 200" y era falso.
   Lo real, comprobado en el código: `POST /api/contratos`, `POST /api/usuarios`
   y `POST /api/firmas` **ya devuelven 201**; `POST /api/formatos` y
   `POST /api/contratos/{id}/documentos` devuelven **200**. La inconsistencia es
   entre ellas, no un 200 generalizado. Ninguna emite cabecera `Location`.
   Alinear las dos que faltan es seguro para el cliente: `apiFetch` decide el
   éxito con `res.ok` (`frontend/src/services/api/client.ts:50`), que acepta
   cualquier 2xx — así que pasar de 200 a 201 no rompe ninguna pantalla.
   Aun así, dilo en el resumen.
3. **`@PreAuthorize` desigual.** `EtapaController` y `ListaChequeoController` no
   declaran ninguno; `AlertaController` protege el listado global pero no el
   listado por contrato. En varios casos la protección real ocurre más abajo
   (`SecurityUtils.verificarAccesoAlContrato` dentro del servicio) y **está
   bien** — pero entonces el controlador debe decirlo en un comentario, para que
   el siguiente que lo lea no "arregle" un hueco que no existe ni asuma una
   protección que falta. Mapea los 13 controladores y deja la tabla en el
   resumen. La tarea 5 escribirá las pruebas de esa misma tabla: compártela.
4. **Formas de respuesta desiguales.** Unos endpoints devuelven la entidad
   actualizada, otros `void`; unos `List<T>`, otros un objeto envolvente. Alinea
   lo que puedas sin romper al frontend, y documenta el resto.
5. **`GlobalExceptionHandler` es la parte buena.** Ya cubre 400/403/404/405/409/
   415/503/500 con un cuerpo `{message, fieldErrors}` uniforme (fue un arreglo
   deliberado, commit `8796fd0`). Úsalo como referencia de lo que "consistente"
   significa aquí, y comprueba que ningún controlador se salte ese contrato
   devolviendo un error a mano.
6. **OpenAPI incompleto.** Casi todos los métodos tienen `@Operation`, pero
   ninguno declara `@ApiResponse` para los errores. Swagger promete hoy un
   camino feliz que no refleja el contrato real de errores.

## Brechas heredadas de la tarea 5 — trabajo ya asignado a esta rama

La rama `test/cobertura-idor-y-aislamiento` encontró dos brechas reales de control
de acceso, las dejó probadas y `@Disabled` en
`backend/src/test/java/co/sena/sicot/AislamientoEntreSupervisoresIntegrationTest.java`,
y **las arregla esta rama**. Las dos están verificadas contra el código: no son
hipótesis.

### Brecha 1 — oráculo de enumeración de contratos (decisión ya tomada)

Un contrato ajeno responde **400** (`BusinessException` de
`SecurityUtils.verificarAccesoAlContrato`) y uno inexistente responde **404**
(`ResourceNotFoundException`). La diferencia deja que un supervisor averigüe qué
ids de contrato existen aunque no sean suyos.

**Decisión tomada por Juan: se unifica a 404 en toda la API**, porque no filtra
existencia. No la re-discutas; impleméntala. Es un cambio de contrato, así que:

- Recorre todos los puntos que hoy devuelven 400 por acceso denegado a un
  contrato, no solo `GET /api/contratos/{id}`.
- `SecurityUtils.verificarAccesoAlContrato` es el punto único donde vive la
  regla: probablemente el cambio sea ahí, pero comprueba que no rompa los casos
  en que `GESTION`/`ADMINISTRADOR` sí deben ver un 403 real.
- Revisa qué hace el frontend hoy con el 400 de ese caso
  (`frontend/src/services/`) y dilo en el resumen.
- Quita el `@Disabled` de `contratoAjenoYContratoInexistenteDebenResponderElMismoCodigo`
  y déjalo pasando.

### Brecha 2 — orden de comprobaciones en `DocumentoService.firmar`

`DocumentoService.firmar` comprueba `documento.getFirmaId() != null`
(`DocumentoService.java:103`) **antes** de
`SecurityUtils.verificarAccesoAlContrato` (`DocumentoService.java:106`). Un
supervisor que intenta firmar un documento de otro contrato recibe "Este
documento ya fue firmado." si lo está, y el mensaje de acceso denegado si no:
puede enumerar qué documentos ajenos están firmados.

El arreglo es mover la comprobación de acceso **antes** de cualquier
comprobación de estado del documento. Aprovecha y revisa si el mismo patrón se
repite en otros servicios: la regla general es que la autorización va primero,
siempre, antes de cualquier validación que pueda revelar estado.

Quita el `@Disabled` de `firmarUnDocumentoAjenoNoDebeRevelarSiYaEstaFirmado` y
déjalo pasando.

## Cómo trabajar

1. Empieza por el inventario: los 13 controladores, sus ~39 endpoints, y para
   cada uno — método, ruta, rol exigido, dónde se aplica realmente el control de
   acceso, código de éxito, forma de respuesta. Ese inventario es medio entregable.
2. Contrasta cada fila con `frontend/src/services/` **leyendo**, nunca editando.
3. Arregla primero lo que no rompe al cliente (OpenAPI, comentarios, la
   documentación contradictoria), y deja lo que sí rompe como propuesta explícita
   al final del resumen, para que Juan decida.

## Criterios de aceptación

- [ ] Las dos pruebas `@Disabled` de `AislamientoEntreSupervisoresIntegrationTest`
      están habilitadas y pasan.
- [ ] Existe el inventario completo de endpoints y no queda ninguna
      contradicción entre documentación y código.
- [ ] Ningún endpoint devuelve un error fuera del contrato de
      `GlobalExceptionHandler`.
- [ ] Todo cambio que afecte al frontend está señalado uno por uno en el resumen.
- [ ] `cd backend; .\mvnw.cmd -B -ntp verify` en verde, y si tocaste el frontend,
      `cd frontend; npm.cmd run build` también.

---

## Resultado — completada, PR #9 mergeado

Commits `5f5e305`, `7264014`, `5be7037`, `b67c523`, más un merge de `develop`.
`verify` en verde: **125 pruebas, 0 fallos, 0 desactivadas**.

- **Las dos brechas heredadas, cerradas.** `SecurityUtils` lanza ahora
  `AccesoDenegadoException` (404) con un mensaje que no distingue entre "no
  existe" y "no es tuyo"; en `DocumentoService.firmar` la comprobación de acceso
  pasó delante de la de estado. Las dos pruebas `@Disabled` quedaron habilitadas
  y pasan.
- **201 alineado** en las dos creaciones que devolvían 200: subida de documento y
  subida de formato.
- **197 `@ApiResponse`** en los 13 controladores, y Javadoc en cada uno
  explicando dónde se aplica realmente la autorización — varios no declaran
  `@PreAuthorize` porque la regla vive en el servicio, y dejarlo escrito evita
  que alguien "arregle" un hueco inexistente.
- **`docs/INVENTARIO_ENDPOINTS.md`**: los 39 endpoints con rol, control de acceso
  real, código de éxito y forma de respuesta.

Cambiar el contrato obligó a tocar tres archivos de prueba de otras ramas
(`isOk()` → `isCreated()`, `isBadRequest()` → `isNotFound()`). Se actualizaron
con el motivo escrito: **ninguna assertion borrada ni debilitada**.

Comprobado que el cambio de 400 a 404 no afecta a la interfaz: `apiFetch` solo
ramifica en 401 y 204, y decide el éxito con `res.ok`.
