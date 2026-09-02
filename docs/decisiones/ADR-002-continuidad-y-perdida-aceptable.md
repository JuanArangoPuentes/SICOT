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

## Verificación del ciclo completo (2 de septiembre de 2026)

Este ADR prometía un RPO de 24 h apoyado en `scripts/respaldo-sicot.sh`. Ese
script estaba escrito y documentado, pero **nunca se había ejecutado**: la cifra
era una intención, no una capacidad. Se ejercitó de punta a punta y se corrigieron
dos defectos que solo aparecen al correrlo:

| Defecto | Síntoma | Corrección |
| --- | --- | --- |
| El volcado se escribía a un temporal dentro del contenedor | Bajo Git Bash en Windows, `/tmp/...` se traducía a una ruta de Windows inexistente para el contenedor, y `pg_dump` fallaba | Volcado directo a la salida estándar; se eliminó el temporal y el `docker cp` |
| La verificación montaba un volumen (`docker run -v`) | Con rutas tipo `/c/Users/...` el demonio no las resuelve: daba «volcado no legible» sobre un archivo **válido** | Se usa el `pg_restore` de la máquina si existe; si no, el contenedor por entrada estándar |

El segundo era el más peligroso: un **falso positivo** que declara corrupto un
respaldo sano entrena al equipo a ignorar la alarma.

**Resultado del ciclo, con la base real:**

```
Original    → usuarios=3 contratos=1 etapas=6 subetapas=27 documentos=0 · 6 migraciones, última 13
Restaurada  → usuarios=3 contratos=1 etapas=6 subetapas=27 documentos=0 · 6 migraciones, última 13
```

El script también se volvió **bimodal** (Docker o PostgreSQL nativo). No es una
comodidad: un procedimiento que solo se puede ejercitar bajo una condición
concreta tiende a no ejercitarse nunca, que es precisamente lo que había pasado.

**Pendiente:** repetir este ejercicio cuando la base tenga documentos reales en
`BYTEA`. El volcado verificado aquí pesa 44 KB; el comportamiento con cientos de
megabytes de binario no está comprobado.
