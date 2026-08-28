# Tarea 5 — Cobertura de IDOR y aislamiento · rama `test/cobertura-idor-y-aislamiento`

> Lee primero `.github/copilot-instructions.md` y `.claude/orca/CONTRATO_DEL_AGENTE.md`.

## Objetivo

Demostrar con pruebas que **un supervisor solo alcanza su propio contrato**, por
cada puerta de entrada de la API. Hoy esa regla existe en un solo método
(`SecurityUtils.verificarAccesoAlContrato`) y se aplica en 15 sitios repartidos
por 7 servicios. Nadie ha comprobado que no falte ninguno.

Un IDOR aquí no es un fallo abstracto: significa que el supervisor de un contrato
puede leer los documentos, las alertas, la trazabilidad y el chat de IA del
contrato de otra persona.

## Alcance — archivos que **posee** esta rama

- `backend/src/test/**` — **solo pruebas**. Esta rama no toca ni una línea de
  `src/main`.

## Fuera de alcance

- Arreglar lo que encuentres. Si una prueba revela una brecha real, esta rama la
  **documenta**, no la parchea: el arreglo pertenece a la rama de su área
  (validaciones, transiciones, seguridad IA o consistencia de API) y esa
  separación es lo que permite revisar el hallazgo antes de que se mezcle con su
  solución. Ver "Qué hacer si encuentras una brecha" abajo.

## El mapa que hay que cubrir

`SecurityUtils.verificarAccesoAlContrato` restringe **solo** al rol `SUPERVISOR`:
`GESTION` y `ADMINISTRADOR` pasan siempre, y un `contrato == null` no restringe
nada. Se invoca desde:

| Servicio | Línea aprox. | Puerta HTTP correspondiente |
|---|---|---|
| `ContratoService` | 161 | `GET /api/contratos/{id}` y todo lo que cuelga de él |
| `AlertaService` | 49 | `PATCH /api/alertas/{id}/leida` |
| `DocumentoService` | 95, 106 | descarga y firma de documento |
| `EtapaService` | 64, 74 | `GET /api/etapas/{id}/subetapas`, `PATCH /api/subetapas/{id}/estado` |
| `RegistroService` | 49 | `GET /api/contratos/{id}/registros` |
| `CopilotoChatService` | vía `contratoService.buscar` | `POST /api/contratos/{id}/copiloto/chat` |

Lo interesante son las puertas que **no** aparecen en esa tabla. Recórrelas todas
—los 13 controladores, ~39 endpoints— y por cada una responde: ¿qué pasa si el
supervisor B la llama con un identificador del contrato de A?

Casos que hay que cubrir explícitamente:

1. **Acceso directo por id de recurso hijo**, saltándose el contrato: descargar
   `documento.id` ajeno, marcar `alerta.id` ajena, cambiar `subetapa.id` ajena.
   Son los que un `@PreAuthorize` por rol nunca detecta.
2. **Chat del Copiloto sobre un contrato ajeno** (`CopilotoController`).
3. **Firmas electrónicas**: `GET /api/firmas` vs `GET /api/firmas/mia` — que un
   supervisor no vea las firmas de otros.
4. **Supervisor sin contrato asignado**: no debe recibir 500 ni una lista de
   todos, sino un vacío o un 403 honesto.
5. **`GESTION` y `ADMINISTRADOR` siguen pasando.** Igual de importante: esta
   suite no puede convertirse en una red que estrangule los roles legítimos.
6. **Recurso inexistente vs. recurso ajeno.** Decide y fija con la prueba cuál es
   la respuesta correcta, y sé coherente: un 404 para el contrato ajeno no filtra
   su existencia; un 403 sí lo hace. Cualquiera de las dos es defendible, pero
   tiene que ser **una sola** en toda la API. Coordina con la tarea 4.

## Cómo trabajar

- Sigue el patrón de las pruebas que ya existen: `AuthIntegrationTest`,
  `ContratoIntegrationTest`, `ContratoDeErroresIntegrationTest`,
  `FirmaElectronicaIntegrationTest`. Corren con H2 (`application-test.properties`)
  y `spring-security-test` — sin Docker, sin PostgreSQL.
- El escenario base es siempre el mismo: **dos supervisores, dos contratos**.
  Móntalo una vez, reutilízalo.
- Nombres de prueba en español, que digan la regla:
  `supervisorNoPuedeDescargarDocumentoDeOtroContrato()`.
- Una prueba por regla. Nada de una prueba gigante que recorra veinte endpoints:
  cuando falle, hay que saber qué se rompió sin leer el cuerpo.

## Qué hacer si encuentras una brecha real

1. Escribe la prueba que la demuestra.
2. Márcala `@Disabled` con el motivo: `@Disabled("Brecha real: <descripción>. La
   arregla la rama <cuál>.")`. Así la CI queda verde y la brecha queda escrita en
   el código, no en un chat que se pierde.
3. Repórtala en el resumen final con `archivo:línea`, el rol y la ruta afectada.
4. **No la arregles.** Y no la escondas: una prueba borrada porque "no pasaba" es
   la peor versión posible de esta tarea.

## Criterios de aceptación

- [ ] Cada endpoint que resuelve un recurso de un contrato tiene una prueba de
      acceso cruzado entre supervisores.
- [ ] Cada prueba nueva pasa, o está `@Disabled` con el motivo escrito.
- [ ] Ni una línea modificada en `backend/src/main`.
- [ ] `cd backend; .\mvnw.cmd -B -ntp verify` en verde.
- [ ] El resumen final lista las brechas encontradas y a qué rama corresponden.

---

## Resultado — completada, PR #8 mergeado

Commit `704cd00`. Un solo archivo de pruebas, +622 líneas, **cero líneas de
`src/main`**. `verify` en verde: 83 pruebas, 2 desactivadas a propósito.

Hay una prueba de acceso cruzado por cada puerta —etapas, subetapas, documentos,
alertas, registros, firmas, chat del Copiloto— y también las que comprueban que
`GESTION` y `ADMINISTRADOR` siguen pasando: la suite no puede estrangular los
roles legítimos.

**Encontró dos brechas reales**, las dejó probadas y `@Disabled` con el motivo
escrito, y no las arregló — que era exactamente lo que pedía esta tarea:

1. **Oráculo de enumeración.** Un contrato ajeno respondía 400 y uno inexistente
   404: el supervisor B podía averiguar qué ids existen aunque no fueran suyos.
2. **Orden de comprobaciones en `DocumentoService.firmar`.** El estado de firma
   se comprobaba antes que el acceso, así que el mensaje de error revelaba si un
   documento ajeno estaba firmado.

Las dos las cerró la **tarea 4**, y sus pruebas están habilitadas y en verde.
Separar el hallazgo de su arreglo es lo que permitió revisar cada brecha por su
cuenta antes de que se mezclara con una solución.
