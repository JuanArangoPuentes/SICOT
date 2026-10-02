# ADR-006 — Qué modelo de IA local usa el Copiloto

**Estado:** Aceptada · **Fecha:** 1 de septiembre de 2026

## Contexto

El Copiloto de SICOT corre sobre **Ollama en local**. Esa decisión de fondo —no
usar una API de pago— se mantiene y es de las más acertadas del proyecto: costo
recurrente cero y ningún dato de contratación pública saliendo hacia un tercero.

Lo que sí estaba mal era **cuál** modelo. El valor por defecto era
`qwen2.5-coder:7b`, una variante **afinada para generar código**. SICOT no
genera ni una línea de código con la IA. Sus tres usos reales son:

1. Redactar documentos formales en español administrativo (actas, informes).
2. Extraer datos estructurados de un PDF de contrato.
3. Conversar con el supervisor sobre el estado de su contrato.

Los tres son tareas de lenguaje natural en español. Un modelo afinado para
código está optimizado para lo contrario: sintaxis, no prosa.

## Decisión

El modelo por defecto pasa a ser **`qwen2.5:7b`**, la variante *instruct* de
propósito general de la misma familia y el mismo tamaño.

Se mantiene la misma familia y el mismo número de parámetros a propósito: el
requisito de memoria y la velocidad no cambian, así que **no hay que revalidar
el hardware**. Lo único que cambia es el afinado.

El modelo sigue siendo configurable con `OLLAMA_MODEL` sin tocar código.

## Consecuencias

**Lo que se gana.** Redacción en español administrativo notablemente más
natural, que es literalmente el producto que el supervisor ve.

**Lo que se pierde.** Capacidad de generar código, que este sistema no usa.

**Lo que hay que hacer al desplegar.** Descargar el modelo nuevo en el host:

```bash
ollama pull qwen2.5:7b
```

Si el modelo no está descargado, el backend falla de forma honesta con un 503 y
un mensaje explícito (ver `OllamaClient`), no con una respuesta inventada.

**Qué no se decidió aquí.** Cuál es el mejor modelo en abstracto. Esto corrige un
desajuste evidente entre la tarea y la herramienta; medir calidad de salida entre
varios candidatos es un trabajo aparte que exige un conjunto de evaluación con
documentos reales del SENA.

## Cuándo revisar

- Cuando existan documentos reales generados y revisados por un supervisor: eso
  permite comparar candidatos con evidencia en vez de por reputación.
- Si el hardware del despliegue cambia y admite un modelo mayor.

## Lo que pasó al aplicar esta decisión (2 de septiembre de 2026)

El cambio de `qwen2.5-coder:7b` a `qwen2.5:7b` se hizo en la configuración
**sin comprobar que el modelo nuevo estuviera descargado**, con Ollama apagado
en ese momento. El resultado: durante horas la configuración apuntó a un modelo
inexistente en la máquina, y cualquier uso real del copiloto habría respondido
503. Una mejora convertida en regresión, sin que nada lo señalara.

Este ADR ya documentaba `ollama pull qwen2.5:7b` como paso de despliegue. **No
fue suficiente**, y esa es la lección: un paso manual escrito en un documento no
es un control. El nombre del modelo es una cadena de configuración que nada
valida, y descargar varios gigabytes se olvida.

**Control añadido:** `VerificacionDelModeloIa` consulta al arrancar el catálogo
de Ollama y, si el modelo configurado no está, escribe en el log un aviso
explícito con el `ollama pull` exacto que falta ejecutar. No impide arrancar —
la IA es opcional y el resto del sistema no depende de ella— y no descarga nada
por su cuenta, porque una descarga de gigabytes disparada en silencio durante el
arranque de un servicio es justo la clase de sorpresa que no debe ocurrir en
producción.

### Verificado en funcionamiento (2 de septiembre de 2026)

Descargado el modelo, se comprobó que responde de verdad y no solo que existe en
el catálogo. Pregunta de dominio contractual, en español:

> *¿Qué es un acta de inicio en un contrato de suministro y quién la suscribe?*
>
> «Un acta de inicio en un contrato de suministro documenta formalmente el
> comienzo de las actividades acordadas entre las partes. Este documento se
> suscribe generalmente por el representante legal del contratista y por el
> representante o oficial designado por la institución SENA…»

Respuesta correcta, en español y con vocabulario del dominio — que es
precisamente lo que un modelo afinado para generar código no garantiza. La
decisión de este ADR queda verificada de punta a punta: modelo descargado,
registrado en Ollama, y produciendo salida útil para el copiloto.

---

## Revisión — 14 de septiembre de 2026: el modelo se queda, pero con menos trabajo

Esta revisión **no cambia la decisión de este ADR**: `qwen2.5:7b` sigue siendo el
modelo del copiloto. Lo que cambia es cuánto se le pide.

### Lo que se midió

El copiloto llevaba tiempo sin que nadie comprobara si respondía **a tiempo**. Se
midió contra el stack real, y el resultado fue que no:

| Llamada | Antes |
| --- | --- |
| `POST /api/ia/extraer-contrato` | **HTTP 503 a los 181 s** |
| `POST /api/contratos/{id}/copiloto/chat` | **HTTP 503 a los 180 s** |

Las dos se pasaban del límite de `sicot.ia.timeout-seconds`. La función que
justifica el proyecto entero —la asistencia por IA— no funcionaba.

La reacción natural era bajar de modelo, y se probó. Sobre un contrato que estaba
en la **etapa 1**, a la pregunta «¿qué tengo que hacer en el paso en el que
está?»:

| Modelo | Tiempo | Etapa que indicó |
| --- | --- | --- |
| `qwen2.5:1.5b` | 45,4 s | etapa 4 — **falso** |
| `qwen2.5:3b` | 90,7 s | etapa 4 — **falso** |
| `qwen2.5:7b` | 158,4 s | etapa 1 — correcto |

Los dos modelos pequeños copiaron el «paso 4» de un ejemplo de estilo incluido en
el propio prompt, que les advertía expresamente de no copiar sus datos. Al
recortar ese ejemplo, el de 3B dejó de decir «4» y pasó a decir «2»: seguía
siendo falso. **No era un problema de redacción del prompt, era de capacidad.**

### La decisión

No bajar de modelo. Bajar el trabajo.

Se comprobó dónde se iba el tiempo y resultó que no estaba en escribir la
respuesta sino en **leer el prompt**: 119,6 s de los 158 s, con 1545 tokens. Y se
comprobó que lo que se le estaba pidiendo al modelo era, en buena parte, repetir
datos que el sistema ya tenía. De ahí dos piezas nuevas:

- `ExtraccionDeterminista` saca los nueve campos con forma fija de un contrato
  estatal —número, NIT, valor, fechas, registro presupuestal— en **un
  milisegundo y sin modelo**. Eran justo los que fallaban: el de 3B no encontraba
  el valor del contrato y el de 7B pegaba la cédula al nombre del representante.
- `GuiaDelPasoActual` responde «¿en qué paso voy?» con el estado real de las
  etapas, **en microsegundos y correcto por construcción**. Al modelo le quedan
  las preguntas abiertas, que es donde sí aporta.

Es la misma conclusión a la que llegó ADR-008 con el resumen periódico, aplicada
ahora a las otras dos funciones de IA: **no pedirle al modelo que sostenga hechos
que el código ya tiene calculados.**

### El resultado, medido igual que el problema

| Llamada | Antes | Después |
| --- | --- | --- |
| «¿En qué paso voy?» | 503 a los 180 s | **200 en 0,43 s** |
| Extracción de contrato | 503 a los 181 s | **200 en 146 s, 11 de 11 campos** |

Los once campos incluyen los dos que el modelo devolvía mal por su cuenta.

### Qué haría falta para bajar a 3B

Con el trabajo ya reducido, `qwen2.5:3b` **sí** resuelve lo que le queda de la
extracción: acertó el objeto y el tipo de contrato en 78 s. Eso, sumado a los
nueve campos deterministas, da 11 de 11 con un modelo que cabe en cualquier
portátil.

Aun así el modelo por defecto **no se baja todavía**, y conviene decir por qué:
sólo se midió la extracción. Las preguntas abiertas del copiloto —«¿de dónde saco
la póliza?»— con 3B no se han evaluado, y son la parte donde el modelo es
insustituible. Bajar el valor por defecto afectaría a las dos.

El paso pendiente, si se quiere cerrar esto, es un conjunto de preguntas abiertas
reales de supervisión con su respuesta esperada, y medir 3B contra él. Mientras
eso no exista, bajar el modelo sería cambiar un problema medido por una
suposición.

---

## Revisión — 1 de octubre de 2026: las sugerencias rápidas tampoco pasan por el modelo

Misma doctrina que la revisión anterior, aplicada a lo que el supervisor más
pulsa: las cinco sugerencias rápidas del panel del Copiloto.

**Lo que se encontró.** Ninguna entraba por un atajo. Cuatro preguntaban datos
fijos del catálogo —qué es el GCCON-F-031, quién lo firma, en qué sub-paso se
genera— que el prompt le pasaba al modelo para que los repitiera; la quinta
pedía el paso actual, que es justo lo que los modelos pequeños fallan (tabla de
arriba). Cada pulsación era una inferencia completa en CPU: hasta ~158 s la
primera del contrato, decenas de segundos las siguientes. Además, `GuiaDelPasoActual`
comparaba sus frases solo en minúsculas: «¿qué documento falta?» o «qué
necesito» no coincidían con ninguna y se iban al modelo.

**Lo que se hizo.**

- `FichaDeDocumentoFormal` contesta sin modelo qué es cada documento formal,
  en qué sub-paso lo arma SICOT, quién firma y su estado en ese contrato. El
  código y el nombre salen de `PlantillaDocumentoIA.CATALOGO`, el mismo con el
  que se arma el PDF.
- `PreguntaNormalizada` quita tildes, signos y mayúsculas a la pregunta y a
  las frases señal por igual, y exige que la frase empiece en una palabra
  («qué falta» ya no se encuentra dentro de «porque faltan»).
- La guía del paso actual dice además qué botón pulsar en el sub-paso
  siguiente, con la misma regla que la guía del tutorial (`guiaSubPaso.ts`).
- Las preguntas condicionales («¿qué hago si…?») se dejan al modelo aunque
  traigan la frase: piden consejo sobre un caso, no el estado del contrato.

Las pruebas afirman el texto exacto de cada respuesta y que ninguna de las
cinco sugerencias llama a Ollama.

**Lo que se corrigió en el prompt de paso.** Decía que SICOT no deja cargar
archivos «ni en 3.1-3.3»; desde el 22-09-2026 en 3.1 y 3.2 se cargan las
fotos de la entrega, y el modelo contradecía a la guía del tutorial. También
decía que el Copiloto redacta los documentos, cuando los arma
`RedactorDeDocumentos` con código y el modelo solo redacta las observaciones.
