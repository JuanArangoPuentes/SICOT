# ADR-010 — Versionado de la API

**Estado:** Aceptada · **Fecha:** 8 de septiembre de 2026

## Contexto

Todas las rutas del backend cuelgan de `/api/...` sin ninguna marca de versión.
Mientras el único cliente fue el frontend, eso no costó nada: se despliegan
juntos en el mismo `docker compose up`, así que un cambio incompatible se arregla
en el mismo commit que lo introduce.

Ese ya no es el caso. Hoy hay tres clientes con ciclos de vida distintos:

| Cliente | Cómo se actualiza |
| --- | --- |
| `frontend/` | Con el backend, siempre |
| `mcp/` | Por su cuenta; corre en la máquina de quien usa el asistente de IA |
| Aplicación de escritorio (ADR-001) | Instalada por máquina; se actualiza cuando alguien la actualiza |

El tercero es el que obliga a decidir. ADR-001 compromete un empaquetado Tauri
para el Supervisor que vivirá en portátiles del centro. Un cambio incompatible en
la API dejará de funcionar en cada máquina que no se haya actualizado, sin aviso
y sin forma de saber cuántas son.

## Decisión

**Las rutas se quedan como están, sin `/v1`. Lo que se adopta es una política de
compatibilidad explícita.**

La política, en tres reglas:

1. **Solo se añade.** Un campo nuevo en una respuesta es siempre compatible: un
   cliente viejo lo ignora. Un campo nuevo en una petición debe ser opcional.
2. **No se quita ni se renombra nada publicado** —campo, ruta o valor de enum—
   sin un ciclo de retirada: primero se marca obsoleto en la documentación de
   OpenAPI, y se retira cuando todos los clientes conocidos se hayan actualizado.
3. **Un cambio que rompa de verdad crea `/api/v2/…`** para la ruta afectada, y
   solo para esa. La versión no es global.

**Por qué no `/api/v1` desde ya.** Porque el prefijo, por sí solo, no protege de
nada: si todo el mundo sigue cambiando `/api/v1` sin ceremonia, el número miente.
Lo que protege es la política, y esa se puede aplicar hoy sobre las rutas
actuales sin tocar tres clientes, la documentación de la API, el servidor MCP y
todas las pruebas de integración.

Renombrar 42 rutas para conseguir la misma protección que da escribir esta
página es cambio a cambio de nada.

**La excepción es explícita.** `/api/automatizaciones/**` es una superficie de
operación interna, no un contrato con clientes. Puede cambiar sin ciclo de
retirada; queda dicho aquí para que nadie lo dé por estable.

## Consecuencias

**Lo que se gana.** El compromiso queda escrito y es verificable en una revisión
de código: «esto quita un campo, ¿qué clientes lo usan?» pasa a ser una pregunta
que alguien hace en el *pull request*.

**Lo que se pierde.** No hay ningún mecanismo que impida romper la
compatibilidad: la sostiene la revisión humana. Es coherente con el tamaño del
equipo — un sistema que lo impidiera por construcción cuesta más de mantener que
el problema que resuelve con tres clientes.

**Lo que hay que hacer.** Al añadir o cambiar un endpoint, actualizar
[`docs/api/INVENTARIO_ENDPOINTS.md`](../api/INVENTARIO_ENDPOINTS.md). Ese archivo
es la lista de lo publicado, y sin él la regla 2 no se puede aplicar: nadie sabe
qué está publicado.

## Cuándo revisar

- Cuando exista un cliente que **no** controle este equipo. Ahí la política deja
  de bastar y el prefijo de versión empieza a ganarse su costo.
- Cuando haya que romper la compatibilidad por primera vez: ese es el momento de
  comprobar si la regla 3 funciona en la práctica.
- Si la aplicación de escritorio llega a estar instalada en más máquinas de las
  que el equipo puede actualizar en una tarde.
