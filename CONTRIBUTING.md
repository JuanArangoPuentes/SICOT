# Cómo contribuir a SICOT

Este documento existe porque el flujo de trabajo del proyecto funcionaba, pero
solo vivía en la memoria de quienes ya estaban. SICOT tiene un horizonte de
tres a cuatro años y un equipo que rota: lo que no está escrito, se pierde en
el siguiente relevo.

## Antes de tocar código

1. **Levante el entorno.** Todo está en [`README.md`](README.md). Un
   `docker compose up -d --build` deja los cuatro contenedores corriendo.
2. **Lea las decisiones.** [`docs/decisiones/`](docs/decisiones/) explica *por
   qué* el sistema es como es: por qué los archivos van en la base, cuánta
   pérdida de datos se acepta, por qué no hay Tailwind. Si algo le parece una
   mala decisión, es probable que ya esté discutida ahí — y si no lo está, ese
   es exactamente el momento de escribir un ADR nuevo.

## Modelo de ramas

```
master     ← estable, lo que se despliega. Solo entra desde develop.
  ↑
develop    ← integración. Aquí llega todo el trabajo terminado.
  ↑
tipo/asunto ← su rama personal
```

**Nunca trabaje directo sobre `master` ni sobre `develop`.** Cree siempre una
rama propia desde `develop`:

```bash
git checkout develop && git pull
git checkout -b fix/nombre-corto-del-problema
```

Prefijos en uso: `fix/` para corregir algo roto, `feat/` para funcionalidad
nueva, `chore/` para mantenimiento, `docs/` para documentación.

## Commits

El mensaje debe explicar **por qué**, no qué. El *qué* ya está en el diff.

```
Corrige el agotamiento de memoria al listar documentos

El listado cargaba el contenido binario completo de todos los archivos solo
para descartarlo al construir el DTO. Con 20 MB de tope por archivo, una
veintena bastaba para un OutOfMemoryError.
```

Un mensaje así le ahorra media hora a quien dentro de un año se pregunte por
qué esa consulta es una proyección y no un `findAll`.

## Pull requests

Se abre siempre contra `develop`, nunca contra `master`. La plantilla pide tres
cosas: qué cambia, por qué, y **cómo se verificó**. Esa tercera es la que
importa — «pasa el CI» no es verificación de que la funcionalidad haga lo que
debe.

Un PR no se mergea si el CI está en rojo. Los cuatro checks bloqueantes son
backend, frontend, mcp y construcción de imágenes. El de vulnerabilidades es
informativo y puede tardar bastante.

## Qué espera el proyecto de su código

Estas reglas no son estilo, son lo que mantiene el sistema entendible:

- **No inventar datos.** Si el backend no lo confirmó, la interfaz no lo
  afirma. Un estado vacío y un error son pantallas distintas: decirle a un
  supervisor «no tiene contratos» cuando en realidad falló la consulta le hace
  creer que no tiene trabajo pendiente.
- **Fallar diciendo la verdad.** Si Ollama no responde, se devuelve 503 con un
  mensaje honesto; no se fabrica una respuesta.
- **Explicar las decisiones no obvias junto al código.** Si algo se hizo de una
  forma rara por un motivo, el motivo va en un comentario. Quien lo mantenga no
  va a poder preguntarle.
- **Una migración aplicada nunca se edita.** Todo cambio de esquema es una
  migración nueva. Ver
  [`docs/operacion/MODELO_DE_DATOS.md`](docs/operacion/MODELO_DE_DATOS.md).

## Pruebas

- Backend: `./mvnw verify` en `backend/`. Debe quedar en verde antes de abrir
  el PR.
- Frontend: `npm run test:run` y `npm run typecheck` en `frontend/`.
- Si corrige un fallo, **añada la prueba que lo habría detectado**. Es la única
  forma de que no vuelva.

La cobertura de rama tiene un mínimo declarado en el CI. No hace falta subirlo,
pero un PR que lo baje no pasa.

## Si algo no cuadra

Abra una incidencia describiendo lo que esperaba y lo que ocurrió. Una
observación bien escrita sobre algo que parece mal diseñado vale tanto como un
parche.
