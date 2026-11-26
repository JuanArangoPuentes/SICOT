# ADR-018 — Lo que se pulsa es un `<button>`

**Estado:** Aceptada, ejecutada el 26 de noviembre de 2026 · **Fecha:** 26 de noviembre de 2026

## Contexto

El 19 de noviembre de 2026 entró ESLint con `eslint-plugin-jsx-a11y` en el
frontend (MDL-151), y encontró un defecto real: dos zonas de la interfaz eran
un `<div onClick>` sin rol, sin parada de tabulación y sin manejo de teclado.

- La **fila de cada paso** del expediente del supervisor, que es la navegación
  principal de su panel.
- La **zona para elegir documentos** del alta de contratos.

Con ratón funcionaban. Para el teclado y para un lector de pantalla
simplemente no existían: **un supervisor que no usa ratón no podía abrir
ningún paso de su contrato**, y nadie podía cargar un documento sin ratón.
SICOT es un sistema del SENA, sujeto a la Resolución 1519 de 2020, así que esto
no es una mejora opcional del producto.

Lo que obliga a escribir este ADR no es el defecto —ese se arregla una vez—,
sino **cómo se arregló: tres veces, de dos formas distintas, en una semana.**

| Cuándo | Qué se hizo |
| --- | --- |
| 19-11-2026 | `<div role="button" tabIndex={0}>` con un ayudante compartido, `propsDePulsable`, que añadía el rol, la parada de tabulación y Enter y Espacio |
| 20-11-2026 | La revisión técnica lo convirtió en un `<button>` de verdad, con CSS que le devuelve el aspecto, y escribió pruebas de extremo a extremo que buscan `button.step-row` |
| 26-11-2026 | Al unir las dos ramas se conservó el `div`, las pruebas dejaron de encontrar la fila, y se arregló cambiando el localizador de las pruebas a `.step-row[role="button"]` |

Las tres son defendibles y ninguna estaba mal razonada. Eso es precisamente el
problema: sin una regla escrita, cada persona —o cada sesión— que toque ese
panel vuelve a elegir, y el código oscila. Lo que se pierde no es el arreglo,
es el tiempo de volver a discutirlo y el riesgo de que la próxima oscilación
deje las pruebas y el código apuntando a cosas distintas, como ya pasó.

## Decisión

**Un elemento que se pulsa es un `<button type="button">`.** No un `<div>` con
`role="button"`, ni un `<div>` con manejadores de teclado añadidos a mano.

Tres consecuencias prácticas de la regla:

1. **El aspecto se arregla con CSS, no cambiando la etiqueta.** Un botón trae
   fondo gris, borde, texto centrado y la fuente del sistema; cinco líneas
   (`width`, `background`, `border`, `color`, `font`, `text-align`) lo dejan
   como estaba. El anillo de foco **no** se toca: es lo que hace que el cambio
   sirva.
2. **Un bloque dentro de un botón se acepta a propósito.** El modelo de
   contenido del HTML dice que un `<button>` solo admite contenido en línea, y
   la fila del paso lleva `<div>` dentro (`StepCircle`, la barra de progreso).
   Se acepta: los navegadores y React lo renderizan sin queja, ningún lector de
   pantalla deja de anunciar el botón, y lo único que protesta es un validador
   de HTML. Perder el foco, Enter, Espacio, el rol y el control por voz para
   complacer a un validador es un mal cambio. Cuando el contenido **ya es** de
   línea, o cuesta nada que lo sea —un `<p>` que pasa a `<span>` con
   `display: block`—, se hace.
3. **El rol `button` sobre un `<div>` queda prohibido** como forma de resolver
   esto. Si algún día aparece un caso que de verdad no puede ser un botón —una
   región entera pulsable, un control anidado dentro de otro—, se escribe el
   motivo junto al código y se añade aquí; no se reintroduce un ayudante
   compartido, que es una invitación a usarlo donde cabe un botón.

Por eso `propsDePulsable` desaparece: existía para el punto 3, y con la regla
escrita no tiene usuarios.

## Consecuencias

**Se gana** una respuesta única a una pregunta que ya se había contestado tres
veces, y un comportamiento que no hay que imitar: el foco, Enter, Espacio, el
rol correcto y el control por voz vienen de serie.

**Se pierde** la validez estricta del HTML en las filas de pasos, por el punto
2 de arriba. Queda escrito para que nadie lo descubra como si fuera un error.

**Queda prohibido** arreglar un elemento pulsable dándole `role="button"` a un
`<div>`, y queda prohibido cambiar el localizador de una prueba para que
encaje con el `div` en lugar de arreglar el elemento. Lo segundo es lo que
pasó el 26-11-2026: la prueba dejó de proteger lo que existía para proteger.

**Lo vigilan dos cosas, no la memoria de quien revise:**

- `npm run lint`, que corre en el CI con `--max-warnings=0`. Las reglas
  `jsx-a11y/click-events-have-key-events` y
  `jsx-a11y/no-static-element-interactions` se ponen rojas con cualquier
  `<div onClick>` nuevo.
- La prueba de extremo a extremo «los seis pasos se pueden abrir y cerrar sin
  ratón», que busca `button.step-row` **por la etiqueta**, a propósito: así se
  pone roja si la fila vuelve a ser un `div`.

## Cuándo revisar

Dos disparadores concretos:

1. **Si aparece un elemento pulsable que de verdad no puede ser un `<button>`**
   —un control dentro de otro control, o una región entera pulsable con un
   botón dentro—. Entonces este ADR se amplía con ese caso y con la forma
   exacta de resolverlo, en vez de que cada sitio lo improvise.
2. **Si un validador de HTML entra como compuerta del CI.** Hoy no hay
   ninguno, y el punto 2 de la decisión se apoya en eso. El día que lo haya,
   hay que elegir entre convertir el contenido de las filas a elementos de
   línea o excluir esa regla con su motivo — y esa elección merece quedar
   escrita aquí.
