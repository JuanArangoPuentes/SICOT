# Tarea 2 — Transiciones de estado · rama `fix/transiciones-de-estado`

> Lee primero `.github/copilot-instructions.md` y `.claude/orca/CONTRATO_DEL_AGENTE.md`.

## Objetivo

Que los estados de SICOT solo cambien por caminos válidos. Hoy el backend acepta
cualquier estado destino que le manden, así que el "estado" de una subetapa, una
etapa o un contrato es un campo libre disfrazado de máquina de estados — y el
backend es la **autoridad funcional** del sistema (§7): si él no lo impide, no lo
impide nadie.

## Alcance — archivos que **posee** esta rama

- `backend/src/main/java/co/sena/sicot/service/EtapaService.java`
- `backend/src/main/java/co/sena/sicot/service/ContratoService.java` — **solo** lo
  relativo a cambio de estado del contrato.
- `backend/src/main/java/co/sena/sicot/entity/enums/Estado*.java` — solo si hace
  falta acompañar el enum con sus transiciones permitidas.
- Una clase nueva si la regla merece vivir sola (p. ej.
  `service/TransicionesDeEstado.java`), en el mismo paquete.
- Pruebas nuevas bajo `backend/src/test/**` (ya existen
  `service/EtapaServiceTest.java` y `service/ContratoServiceTest.java`).

## Fuera de alcance

- Anotaciones de validación en los DTOs (tarea 1), el paquete `ia/**` (tarea 3),
  firmas de controlador y códigos HTTP (tarea 4), tests de aislamiento (tarea 5).
- **Migraciones.** Si necesitas persistir la transición (una tabla de historial),
  no la crees: ya existe `RegistroService` para dejar traza, y una tabla nueva
  requiere a Juliana.
- **No inventes el proceso institucional.** Las transiciones válidas de
  `EstadoContrato` que no estén confirmadas en la documentación real del SENA se
  marcan `PENDIENTE_DE_DEFINIR` y se dejan permitidas, no se adivinan. Lo que sí
  puedes cerrar sin inventar nada es lo aritméticamente imposible y lo que
  contradice el propio código.

## Hallazgos ya verificados (punto de partida, no lista cerrada)

1. **`EtapaService.cambiarEstadoSubetapa` (líneas ~70-79)** — asigna
   `subetapa.setEstado(nuevoEstado)` sin comprobar **nada**. Se puede pasar una
   subetapa de `COMPLETADA` a `PENDIENTE` y el porcentaje de la etapa retrocede
   sin dejar constancia de quién ni por qué. `PATCH /api/subetapas/{id}/estado`
   está abierto a `SUPERVISOR`, `GESTION` y `ADMINISTRADOR`.
2. **El estado del contrato nunca se recalcula.** `recalcularEtapa` actualiza la
   etapa y su porcentaje, pero cuando las 6 etapas del flujo GCCON-P-010 quedan
   `COMPLETADA` el `EstadoContrato` sigue igual. Decide y **documenta** si eso es
   correcto (el cierre de un contrato del Estado probablemente sea un acto
   humano, no automático) — si lo es, deja el porqué escrito en el código en vez
   de dejar el hueco mudo.
3. **`EtapaService.listarPorContrato` es `@Transactional` de escritura**: un
   `GET /api/contratos/{id}/etapas` siembra las etapas si no existen. Es
   deliberado y está comentado (auto-sanación de contratos antiguos), pero
   verifica que sea idempotente bajo dos peticiones concurrentes; hoy dos GET
   simultáneos pueden sembrar dos juegos de etapas.
4. **`recalcularEtapa` no registra el retroceso.** Registra el cambio vía
   `RegistroService`, pero con el mismo texto para avanzar y para retroceder.
   Quien audite el contrato no distingue una corrección de un avance.
5. `EstadoContrato` tiene 5 valores (`BORRADOR`, `ACTIVO`, `SUSPENDIDO`,
   `FINALIZADO`, `CANCELADO`) y `PATCH /api/contratos/{id}/estado` acepta
   cualquiera desde cualquiera: `FINALIZADO → BORRADOR` pasa hoy.

## Cómo trabajar

1. Escribe primero, en una tabla, el mapa de transiciones **actual** (lo que el
   código permite) y el **propuesto**, marcando cuáles son evidentes y cuáles
   quedan `PENDIENTE_DE_DEFINIR`. Ponlo en el cuerpo del commit o en el resumen.
2. Implementa el rechazo como `BusinessException` con mensaje en español — el
   `GlobalExceptionHandler` ya lo traduce a 400 con la forma correcta.
3. Cada transición prohibida necesita una prueba que demuestre que se rechaza, y
   cada transición permitida una que demuestre que sigue funcionando. Lo segundo
   importa igual: esta rama puede romper el flujo del supervisor.
4. Revisa el frontend **solo para leer**: si el panel del supervisor ofrece hoy un
   botón que tu regla va a rechazar, dilo en el resumen. No cambies la UI.

## Criterios de aceptación

- [ ] Ninguna transición de subetapa/etapa/contrato ocurre sin pasar por una
      comprobación explícita.
- [ ] Lo no confirmado institucionalmente queda marcado, no adivinado.
- [ ] Existe traza distinguible entre avance y retroceso.
- [ ] El flujo completo del supervisor (6 etapas, 27 subetapas) sigue pasando de
      principio a fin — probado, no supuesto.
- [ ] `cd backend; .\mvnw.cmd -B -ntp verify` en verde.
