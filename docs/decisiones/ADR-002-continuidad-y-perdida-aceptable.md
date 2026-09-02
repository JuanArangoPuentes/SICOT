# ADR-002 — Pérdida de datos aceptable y tiempo de recuperación

**Estado:** Aceptada · **Fecha:** 1 de septiembre de 2026

## Contexto

SICOT es el registro de contratos de un centro de formación público. Corre sobre
**un solo host**, con **una sola instancia** de PostgreSQL, **sin réplica** y
**sin recuperación a un punto en el tiempo**.

Hasta ahora eso no era una decisión: era un vacío. No existía ninguna cifra
escrita sobre cuánta información es aceptable perder ni en cuánto tiempo hay que
volver a estar en pie. Sin esas dos cifras es imposible saber si la
infraestructura actual es suficiente o insuficiente — solo se puede opinar.

Los dos números que hay que fijar:

- **RPO** (*Recovery Point Objective*) — cuánto trabajo reciente se acepta
  perder, medido en tiempo.
- **RTO** (*Recovery Time Objective*) — cuánto puede estar caído el sistema
  antes de que el impacto deje de ser tolerable.

## Decisión

Se declaran los siguientes objetivos, **alcanzables con la infraestructura
actual**:

| Objetivo | Valor comprometido | Con qué se cumple |
| --- | --- | --- |
| **RPO** | **24 horas** | Respaldo diario verificado (`scripts/respaldo-sicot.sh`, cron 02:00) |
| **RTO** | **4 horas** | Restauración manual documentada en `docs/operacion/BACKUP_Y_RESTAURACION.md` |
| **Retención** | **14 días** | Rotación automática en el mismo script |

Se acepta explícitamente que **una caída del disco a las 23:00 pierde el trabajo
de todo ese día**. Ese es el costo declarado de correr sobre un solo host, y se
asume a conciencia.

Un respaldo solo cuenta si se ha leído entero: el script ejecuta
`pg_restore --list` sobre cada volcado. Un `pg_dump` puede terminar con código 0
y dejar un archivo truncado si el disco se llenó a mitad, y eso **solo se
descubre el día que hace falta restaurar**.

## Consecuencias

**Lo que se gana.** Una expectativa explícita. Si alguien considera que perder un
día de trabajo es inaceptable, ahora hay una cifra concreta que discutir y un
camino de mejora ya escrito, en vez de una discusión sobre sensaciones.

**Lo que se pierde.** Ninguna capacidad: esto documenta lo que ya había, no lo
degrada.

**Camino de mejora, en orden de costo creciente.** Si el RPO de 24 h resulta
insuficiente, estas son las opciones **en el orden en que conviene tomarlas**:

| Si el RPO debe bajar a… | Qué hacer | Costo operativo |
| --- | --- | --- |
| **1 hora** | Cambiar el cron a cada hora (`0 * * * *`) | Ninguno — la base son megabytes |
| **Minutos** | Archivado continuo de WAL (pgBackRest) | Medio: un componente más que aprender y vigilar |
| **Casi cero** | Réplica en caliente en un segundo host | Alto: un segundo servidor y sus procedimientos |

Se elige el escalón más bajo a propósito. Un equipo de tres personas sostiene
mejor un respaldo diario que **sí se verifica** que un archivado continuo que
nadie sabe restaurar bajo presión.

## Cuándo revisar

- Cuando el sistema tenga contratos reales en producción y no solo datos de
  prueba: ahí conviene reevaluar si 24 h sigue siendo tolerable.
- Si el SENA fija por norma un RPO menor.
- Si alguna vez ocurre una pérdida real: el incidente manda sobre esta cifra.
