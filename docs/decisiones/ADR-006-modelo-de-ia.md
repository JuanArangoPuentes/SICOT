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
