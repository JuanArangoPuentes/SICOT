# Auditoría de la interfaz en pantalla de teléfono — 16 de septiembre de 2026

Este documento **mide**, no arregla. Es la entrada del trabajo de adaptación a
móvil: sin él, los arreglos se ordenan por lo primero que alguien vio y no por
gravedad.

## Cómo se midió

- Viewport real de **360 × 800** con emulación de dispositivo móvil (agente de
  usuario de Android, eventos táctiles, densidad 2×), no una ventana de
  escritorio estrechada.
- Contra el **entorno completo** —backend, PostgreSQL y frontend en Docker— y
  con la base poblada: 7 contratos, 42 etapas, 189 subetapas, 15 alertas. Una
  tabla sin filas cabe en cualquier ancho y no habría revelado nada.
- Las once pantallas de los tres roles, con sesión real de cada rol.
- Para cada pantalla se midió: si la página se puede desplazar en horizontal,
  qué elementos quedan fuera del ancho visible y por cuántos píxeles, qué
  objetivos táctiles miden menos de 44 px y qué campos de formulario tienen
  tipografía por debajo de 16 px.

Guion de la auditoría y capturas: no se versionan, se regeneran con el mismo
procedimiento. Lo que se conserva es esta tabla de hallazgos.

## Resumen medido

| Pantalla | Elementos recortados | El peor se sale por | Toques < 44 px | Campos con zoom |
| --- | ---: | ---: | ---: | ---: |
| Login | — | ver hallazgo 1 | 1 | 0 |
| Supervisor · bandeja | 1 | 166 px | 22 | 0 |
| Supervisor · contrato | 5 | 270 px | 25 | 1 |
| Supervisor · alertas | 1 | 166 px | 14 | 0 |
| Supervisor · documentos | 19 | 365 px | 10 | 0 |
| Supervisor · registros | 1 | 166 px | 15 | 4 |
| Gestión · contratos | 41 | 713 px | 7 | 0 |
| Administración · panel | 1 | 132 px | 8 | 0 |
| Administración · documentos | 1 | 132 px | 9 | 0 |
| Administración · usuarios | 1 | 132 px | 18 | 0 |
| Administración · firmas | 1 | 132 px | 9 | 0 |

---

## Hallazgo 0 — Lo recortado no se puede alcanzar de ninguna manera

**Gravedad: es lo que convierte todo lo demás en un problema funcional y no
estético.**

En las once pantallas el ancho desplazable del documento es exactamente 360 px:
el mismo que el visible. Es decir, **no hay desplazamiento horizontal**. Todo lo
que la tabla de arriba cuenta como «recortado» no está simplemente incómodo:
está fuera del alcance del usuario, sin gesto posible que lo traiga a la vista.

La causa es el armazón: `.app-shell` es `height: 100vh` con `overflow: hidden`
(`frontend/src/index.css:468`). Recorta por diseño, que es lo correcto en un
escritorio donde nada se sale, y es exactamente lo peor que puede hacerse cuando
todo se sale.

**Por qué importa decirlo primero:** cada hallazgo siguiente se leería como «se
ve apretado» si no se supiera esto. No se ve apretado — no se ve.

---

## Hallazgo 1 — Un supervisor no puede iniciar sesión desde un teléfono

**Gravedad: máxima. Bloquea todo lo demás.**

A 360 px la pantalla de acceso se maqueta en **606 px**: el panel de identidad
ocupa 434 px y el formulario se queda con 171 px. El botón **Ingresar** queda
situado entre los píxeles 475 y 566 del eje horizontal — es decir, **empieza 115
px más allá del borde derecho de la pantalla**.

Medido de dos formas independientes, porque la primera parecía demasiado grave
para fiarse de ella:

1. Geometría: el centro del botón cae en x = 521 sobre un ancho visible de 360.
2. Automatización: el primer intento de la auditoría de pulsar el botón agotó su
   tiempo de espera reintentando, con el mensaje `<div class="login-identity">
   intercepts pointer events` — el panel de identidad se interpone.

La causa está localizada. La hoja de estilos ya tiene un bloque para pantallas
estrechas (`index.css:1020`, `max-width: 767px`) que le da al panel de identidad
una altura de 120 px y deja que el formulario ocupe el resto. Lo que ese bloque
**no** hace es cambiar la dirección del contenedor flexible, que sigue siendo
`row`. El resultado es que el panel de identidad no se coloca encima del
formulario, como se pretendía: se queda al lado, ahora con 120 px de alto y 434
de ancho, y el formulario se comprime hasta lo que sobra.

O sea: la intención de apilar en vertical está escrita, pero le falta la línea
que la hace efectiva.

---

## Hallazgo 2 — El menú de usuario se sale de la pantalla en las once pantallas

**Gravedad: alta, y es el único hallazgo que aparece en todas partes.**

El panel del menú de usuario —nombre, correo, cerrar sesión— mide 181 px en los
paneles de Supervisor y Gestión y 198 px en el de Administración, y queda
desplazado entre 132 y 270 px fuera del borde derecho. Al estar anclado a la
derecha de una cabecera calculada para un escritorio, en un teléfono nace ya
fuera de la pantalla.

Consecuencia concreta: **desde un teléfono no se puede cerrar sesión**. En un
sistema institucional que se abre en dispositivos compartidos, eso no es un
detalle de comodidad.

---

## Hallazgo 3 — El registro de contratos de Gestión pierde cuatro de sus seis columnas

**Gravedad: alta. Es la pantalla principal del rol.**

41 elementos recortados, el peor **713 px** fuera del borde. La causa es la
rejilla de columnas fijas de `GestionPanel.tsx:418`:

```
gridTemplateColumns: '180px 1fr 180px 160px 120px 180px'
```

Suman **940 px de mínimo absoluto** antes de contar la columna flexible. En 360
px se ve el número de contrato y una porción del objeto partida en palabras
sueltas; **las fechas, el estado, el supervisor asignado y las acciones no
existen para quien mira**. El botón «Cargar nueva ficha» de la cabecera también
queda cortado por la mitad.

---

## Hallazgo 4 — Los documentos del Supervisor pierden estado y acciones

**Gravedad: alta.**

19 elementos recortados, el peor 365 px fuera. Misma causa, otra rejilla
(`VistaDocumentos.tsx:211`):

```
gridTemplateColumns: '1fr 150px 1fr 100px 160px'
```

Lo que queda fuera son justamente las etiquetas de estado («Sin generar aún») y
la columna de acciones: el supervisor ve la lista de documentos formales pero no
puede saber en qué estado está ninguno ni actuar sobre ellos.

---

## Hallazgo 5 — En la vista de contrato se salen las tarjetas y el Copiloto

**Gravedad: media.**

Cinco elementos recortados: las tarjetas de gráficas («Tiempo frente a avance»,
«Documentos formales del proceso») se salen 56 px cada una porque piden un
mínimo de 340 px y conviven con el margen y la barra lateral; el panel lateral
del Copiloto se sale 34 px; y el botón «Tutorial» de la cabecera, 76 px.

Es el caso que confirma que no basta con arreglar las tablas: **una gráfica no
se convierte en tarjeta como se convierte una fila**, y este frente necesita
tratamiento propio.

---

## Hallazgo 6 — Casi ningún objetivo táctil llega al tamaño mínimo

**Gravedad: media, pero es la que más se nota al usar.**

Medidas reales, repetidas en todos los paneles:

| Elemento | Tamaño medido | Mínimo recomendado |
| --- | --- | --- |
| Entradas de navegación (Bandeja, Contrato, Alertas…) | 51 × 35 px | 44 × 44 px |
| Configuración | 35 × 34 px | 44 × 44 px |
| Contraer menú | 35 × 33 px | 44 × 44 px |
| Avatar del usuario | 32 × 32 px | 44 × 44 px |
| Botón «Tutorial» | 85 × 31 px | 44 px de alto |

Entre 7 y 25 objetivos por pantalla quedan por debajo del mínimo. Son tamaños
cómodos con un ratón y una trampa con un dedo.

Nota sobre la barra lateral: en un teléfono de 360 px se muestra en su versión
colapsada, de 68 px, que se lleva el **19 % del ancho disponible** para mostrar
cinco iconos.

---

## Hallazgo 7 — Cinco campos provocan zoom automático al enfocarlos

**Gravedad: baja, pero tiene arreglo trivial.**

En la vista de registros hay un desplegable de 13 px y tres campos de 13–14 px;
en la de contrato, uno de 14 px. Por debajo de 16 px el navegador móvil hace
zoom solo al enfocar el campo, y al salir no vuelve: la pantalla queda
descolocada y el usuario no sabe qué hizo.

---

## Qué no se midió, y por qué

- **La vista de documentos con volumen real.** La base tiene un solo documento
  cargado, así que la rejilla se midió con una fila. El desbordamiento es de la
  rejilla y no de los datos, así que la medida vale; lo que no se pudo observar
  es el comportamiento con una lista larga.
- **Modales y formularios de creación** (nuevo usuario, nueva firma, formato):
  exigen abrir cada uno con su flujo, y la auditoría se ciñó a las pantallas.
  Quedan anotados como pendientes, no como aprobados.
- **Orientación horizontal del teléfono y tablets.** Se midió el caso más
  exigente. Si el caso más exigente se arregla bien, el resto es continuo.
- **iOS.** No hay forma de comprobarlo con el equipo disponible. Cualquier
  afirmación sobre Safari móvil sería inventada.

---

## Orden de ataque que sale de esta auditoría

1. El login (hallazgo 1), porque bloquea todo lo demás.
2. El armazón: navegación alcanzable y fin del recorte silencioso (hallazgos 0,
   2 y 6).
3. Las dos rejillas de columnas fijas (hallazgos 3 y 4).
4. Las tarjetas de la vista de contrato y el Copiloto (hallazgo 5).
5. La tipografía de los campos (hallazgo 7).

---

## Estado al cierre del mismo día

Se atacó en ese orden y se volvió a medir con el mismo procedimiento, sobre el
mismo entorno y los mismos datos. La comparación no es una impresión:

| Medida | Antes | Después |
| --- | ---: | ---: |
| Pantallas con elementos recortados | 10 de 10 | **0 de 10** |
| Elementos recortados en el registro de contratos | 41 | **0** |
| Elementos recortados en documentos del supervisor | 19 | **0** |
| Campos que provocan zoom al enfocarlos | 5 | **0** |
| Objetivos táctiles bajo 44 px, por pantalla | entre 7 y 25 | **entre 0 y 9** |
| ¿Se puede iniciar sesión desde un teléfono? | No | **Sí** |
| ¿Se puede cerrar sesión desde un teléfono? | No | **Sí** |

### Lo que solo apareció al abrirlo en un teléfono de verdad

Merece un apartado propio porque es la lección más aprovechable del día: el
navegador con emulación de móvil **no sustituye** a ejecutar la aplicación. Dos
defectos que ninguna de las medidas anteriores podía detectar salieron a la
primera al instalar el APK en un emulador:

1. **El titular de la pantalla de acceso se cortaba a media letra.** Ocupa cuatro
   líneas de 36 px y la banda de identidad mide 120 px. En el navegador no se
   veía porque la ventana era más alta y la banda tenía sitio.
2. **La marca quedaba dibujada debajo del reloj y la batería.** La causa no
   estaba en ninguna regla de estilo sino en la etiqueta `viewport` del
   documento: sin `viewport-fit=cover`, `env(safe-area-inset-*)` devuelve cero y
   todos los rellenos escritos para esquivar la barra de estado y la barra de
   gestos no hacen absolutamente nada. Un navegador de escritorio no tiene
   ninguna de las dos barras, así que la regla parecía correcta.

**Lo que queda pendiente, con su causa:**

- **La barra de etapas** (hallazgo 5, parte táctil). Sus segmentos miden 38 px de
  ancho porque son seis repartiéndose la pantalla. No se fuerzan a 44: es una
  barra de progreso, no una lista de acciones, y estirarla rompería la lectura
  de izquierda a derecha que representa. Necesita un rediseño propio para
  teléfono, no un mínimo impuesto desde fuera.
- **Modales y formularios de creación** — nuevo usuario, nueva firma, formato.
  Siguen sin medirse: la auditoría se ciñó a las pantallas, y abrir cada modal
  exige recorrer su flujo. No están aprobados, están sin mirar.
- **La vista de documentos con volumen real.** Sigue habiendo un solo documento
  cargado en la base.
- **iOS.** Sin forma de comprobarlo con el equipo disponible.

Lo que impide que esto se deshaga solo: las pruebas de extremo a extremo tienen
desde hoy un segundo proyecto con viewport de teléfono, que afirma que se puede
entrar, que ninguna vista del Supervisor se desborda y que se puede salir.

---

## Apéndice — 17 de septiembre de 2026: qué se cerró de esta lista

Se retoman aquí los pendientes de arriba, porque una lista de pendientes que
nadie vuelve a tocar deja de ser un pendiente y pasa a ser una carencia.

| Pendiente del 16 | Estado |
| --- | --- |
| La barra de etapas | **Resuelto.** Cada segmento pasa de 38 px de ancho a 132 × 69, con su rótulo legible, y la barra se desplaza en horizontal con anclaje. Se llega a la etapa en curso sola al abrir la vista |
| Modales y formularios de creación | **Medidos los cinco.** No se desbordaban ni provocaban zoom; el defecto real era el botón de cerrar, de 13 × 44 px en todos |
| La vista de documentos con volumen real | **Medida con 9 documentos.** Aparecieron 6 elementos fuera del ancho: el nombre del documento se quedaba con 86 px de 360. Corregido, 250 px |
| iOS | Sigue sin forma de comprobarse con el equipo disponible |

### Dos correcciones a lo que este documento afirmaba

Conviene dejarlas escritas porque las dos son del tipo que hace desconfiar de
una medida, no de un arreglo:

1. **La compuerta que este documento anunciaba no protegía lo que decía.**
   Medía `document.documentElement.scrollWidth` contra `innerWidth`, y el
   hallazgo 0 de este mismo documento dice que el armazón recorta con
   `overflow: hidden` y que el ancho desplazable era exactamente el visible en
   las once pantallas. O sea: esa comprobación habría pasado en verde sobre la
   aplicación rota que venía a proteger. Se descubrió deshaciendo a propósito la
   conversión de tabla a tarjetas y viendo que no se enteraba. Ahora mide la caja
   de cada elemento contra el ancho de la ventana, que es lo que se midió aquí.

2. **Y no se ejecutaba en ningún sitio.** La suite de Playwright estaba fuera del
   CI. Desde el 17 corre en cada PR, con los tres roles y contra una base
   sembrada — porque con la base vacía habría estado midiendo pantallas sin
   filas, que es justo lo que la sección «Cómo se midió» de arriba descarta por
   inútil.
