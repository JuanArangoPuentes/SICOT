# ADR-004 — Un solo sistema de estilos en el frontend

**Estado:** Aceptada · **Fecha:** 1 de septiembre de 2026

## Contexto

El frontend tenía **dos sistemas de estilos a medias y ninguno completo**. Una
revisión de arquitectura lo midió:

| Mecanismo | Uso real |
| --- | --- |
| Objetos `style={{…}}` en línea | **574** usos en 16 archivos |
| Clases propias (`.card`, `.btn-green`, …) | ~45 clases definidas, 130 usos |
| Utilidades de Tailwind | **7 usos en total**, casi todos de un archivo escrito el mismo día |

Es decir: el proyecto pagaba la instalación, el paso de compilación y la
dependencia de Tailwind **sin usarlo**. Al mismo tiempo, sí tenía un sistema de
diseño real y bien pensado —**48 variables CSS con nombre**— pero se aplicaba
repitiendo objetos de estilo a mano en cada componente.

El costo no es estético. Un color de acento que cambie obliga a buscar en 574
sitios. Y un archivo con cientos de objetos en línea es mucho más difícil de leer
para alguien que llega nuevo, que es la situación normal en este equipo.

## Decisión

**Se retira Tailwind.** El sistema de estilos de SICOT son las variables CSS como
tokens, más clases con nombre en `index.css`.

Tres partes:

1. **Fuera la dependencia**: `tailwindcss`, `@tailwindcss/vite` y el
   `@import 'tailwindcss'`.
2. **Reset propio y explícito**: Tailwind aportaba en silencio un *preflight*
   (`svg { display: block }`, herencia de fuente en `button`/`input`, márgenes a
   cero). Al retirarlo hay que escribir ese reset, no heredarlo. Queda en
   `index.css`, comentado y bajo control del proyecto.
3. **Clases para lo repetido**: los patrones en línea que se repiten —botones de
   acción en tabla, filas de rejilla, cabeceras de sección— pasan a clases con
   nombre.

**No** se migran los 574 objetos en línea de una vez. Se convierten los patrones
repetidos, y lo puntual se deja como está.

## Consecuencias

**Lo que se gana.** Una sola forma de estilar, una dependencia menos, un paso de
compilación menos, y un reset que el proyecto controla en vez de heredar de un
framework que no usa.

**Lo que se pierde.** Las utilidades de Tailwind, que no se estaban usando.

**Por qué retirar y no adoptar.** La otra salida coherente era usar Tailwind de
verdad y migrar los 574 objetos a utilidades. Se descarta por proporción: es una
reescritura completa de la capa visual, sin pruebas de regresión visual que la
respalden, para llegar al mismo sitio al que ya se llega con los tokens que el
proyecto **ya tiene y ya funcionan**. El riesgo no compensa.

**La regla a partir de ahora.** Color, espaciado, tipografía y bordes salen de
las variables CSS. Si un valor se repite en tres sitios, se convierte en clase.
Un `style={{}}` en línea es aceptable para algo genuinamente único de un
componente.

## Pendiente conocido: las tipografías son externas

`index.css` importa Space Grotesk e IBM Plex desde Google Fonts. Eso tiene tres
costos que conviene saldar, y que no se saldan en esta decisión:

1. **No funciona sin conexión** — justo lo que necesita la instalación de
   escritorio de [ADR-001](./ADR-001-bifurcamiento-de-despliegue.md).
2. **La IP de cada funcionario llega a Google** en cada carga.
3. **Bloquea el pintado** hasta que responde un tercero.

Al endurecer las cabeceras de seguridad, la primera versión de la CSP no
contemplaba esos orígenes y **bloqueó las tipografías**: la interfaz cayó a las
fuentes del sistema, con un error solo visible en la consola del navegador y
ningún síntoma en el servidor. Se corrigió admitiendo `fonts.googleapis.com` y
`fonts.gstatic.com`, pero el episodio deja claro el punto: una dependencia
externa es una cosa más que puede romperse en silencio.

**Lo correcto es alojar las tipografías en el propio servidor.** Queda
pendiente, con prioridad ligada a ADR-001: en el momento en que la instalación
de escritorio sea real, deja de ser opcional.

## Cuándo revisar

- Si el equipo crece y varias personas tocan la capa visual a la vez: ahí un
  sistema de utilidades empieza a pagar su costo de aprendizaje.
- Si aparece la necesidad de temas visuales por entidad (más de un centro de
  formación con identidad propia).
