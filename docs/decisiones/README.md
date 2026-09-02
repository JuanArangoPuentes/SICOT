# Decisiones de arquitectura (ADR)

Este directorio guarda las decisiones que **no son evidentes leyendo el código** y
que alguien tendría que volver a tomar —probablemente mal, y sin saber que ya se
discutió— si no estuvieran escritas.

## Por qué existen

SICOT tiene un horizonte de tres a cuatro años y lo sostiene un equipo pequeño
que va a rotar. La persona que mantenga este sistema en 2029 casi con seguridad
no es ninguna de las que lo escribió. Un comentario en el código explica *qué*
hace una línea; un ADR explica *por qué el sistema es así y no de las otras dos
formas que también funcionaban*.

Una revisión de arquitectura de septiembre de 2026 señaló que varias decisiones
grandes del proyecto estaban **implícitas**: nadie las había tomado a propósito,
pero el sistema se estaba construyendo alrededor de ellas igual. Estos documentos
las hacen explícitas.

## Formato

Cada ADR responde cuatro preguntas y nada más:

| Sección | Qué contiene |
| --- | --- |
| **Contexto** | Qué situación obliga a decidir. Hechos, no opiniones. |
| **Decisión** | Qué se decidió, en una frase que se pueda contradecir. |
| **Consecuencias** | Qué se gana, qué se pierde y qué queda prohibido. |
| **Cuándo revisar** | El disparador concreto que obliga a reabrir esto. |

La sección **Cuándo revisar** es la que evita que un ADR se convierta en dogma.
Una decisión correcta hoy puede ser incorrecta con diez veces más datos o con
un requisito nuevo; lo que no puede pasar es que se revierta por accidente,
sin que nadie note que había un motivo.

## Estados

- **Aceptada** — vigente, el sistema se comporta así.
- **Propuesta** — escrita, pendiente de confirmación (normalmente institucional).
- **Reemplazada por ADR-NNN** — ya no aplica; se conserva porque explica por qué
  el código tuvo una forma anterior.

Un ADR **no se edita para cambiar la decisión**: se escribe uno nuevo que lo
reemplaza. Igual que una migración de base de datos aplicada, el historial es
parte del valor.

## Índice

| ADR | Título | Estado |
| --- | --- | --- |
| [001](./ADR-001-bifurcamiento-de-despliegue.md) | Qué significa "instalación local" para el Supervisor | Aceptada |
| [002](./ADR-002-continuidad-y-perdida-aceptable.md) | Pérdida de datos aceptable y tiempo de recuperación | Aceptada |
| [003](./ADR-003-umbral-de-migracion-de-documentos.md) | Cuándo dejar de guardar los archivos dentro de PostgreSQL | Aceptada |
| [004](./ADR-004-un-solo-sistema-de-estilos.md) | Un solo sistema de estilos en el frontend | Aceptada |
| [005](./ADR-005-gestion-de-secretos.md) | Dónde viven las credenciales y cómo se rotan | Aceptada |
| [006](./ADR-006-modelo-de-ia.md) | Qué modelo de IA local usa el Copiloto | Aceptada |
| [007](./ADR-007-enrutado-y-enlaces-profundos.md) | Enrutado por URL y enlaces compartibles | Aceptada |
