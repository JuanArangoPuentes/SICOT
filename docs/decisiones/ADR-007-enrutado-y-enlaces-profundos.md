# ADR-007 — Enrutado por URL y enlaces compartibles

**Estado:** Aceptada · **Fecha:** 1 de septiembre de 2026

## Contexto

La navegación de SICOT se controlaba por estado de React: una variable `screen`
en `App.tsx` y una variable `tab` dentro de cada panel. La URL nunca cambiaba —
siempre `/`, en cualquier pantalla.

Las consecuencias eran concretas, no teóricas:

- **El botón atrás del navegador saca de la aplicación.** No vuelve a la vista
  anterior: abandona SICOT. Es el reflejo más automático que tiene un usuario y
  hacía justo lo contrario de lo esperado.
- **Recargar la página devuelve al inicio.** Un supervisor revisando sus alertas
  que pulsa F5 aparece en la bandeja de entrada.
- **No se puede compartir una vista.** "Mírate los documentos del contrato"
  obliga a explicar la ruta de clics, porque no hay enlace que mandar.
- **No hay historial.** No se puede volver al paso anterior de un recorrido.

Con tres paneles esto era tolerable. Con un horizonte de tres a cuatro años y un
producto que va a crecer, deja de serlo: cada pantalla nueva multiplica el costo
de la decisión de no tener rutas.

## Decisión

Se adopta **React Router** y la vista activa pasa a vivir en la URL.

Esquema de rutas:

| Ruta | Pantalla |
| --- | --- |
| `/login` | Inicio de sesión |
| `/supervisor/:vista` | Panel del Supervisor (`bandeja`, `contrato`, `alertas`, `documentos`, `registros`) |
| `/gestion/:vista` | Panel de Gestión |
| `/admin/:vista` | Panel de Administración |

Reglas:

1. La ruta raíz redirige al panel que corresponde al rol de la sesión, o a
   `/login` si no hay sesión.
2. Entrar a una ruta de un rol ajeno **redirige**, no muestra un error: la
   autoridad sobre permisos sigue siendo el backend, y la URL no es un control de
   acceso.
3. Una vista desconocida cae en la vista por defecto del panel en vez de romper.

**Por qué React Router y no un enrutador propio.** Escribir cien líneas sobre la
History API es tentador y funcionaría. Se descarta a propósito: en tres años,
quien mantenga esto va a buscar en internet cómo se hace algo con la navegación,
y encontrará respuestas sobre React Router, no sobre un enrutador casero sin
documentación ni comunidad. Para un equipo pequeño y rotativo, usar el estándar
**es** la decisión de mantenibilidad.

## Consecuencias

**Lo que se gana.** El botón atrás funciona, recargar conserva la vista, y una
vista se puede compartir por enlace o dejar en marcadores.

**Lo que se pierde.** Una dependencia más (gratuita, de código abierto, estándar
de facto en React) y unos kilobytes en el paquete.

**Lo que hay que tener en cuenta.** nginx ya está configurado con
`try_files $uri $uri/ /index.html`, así que entrar directo a una ruta profunda
funciona sin cambios en el servidor. Esa configuración estaba puesta "por si
acaso" desde antes; ahora es un requisito real.

## Cuándo revisar

- Si se añade la instalación de escritorio de [ADR-001](./ADR-001-bifurcamiento-de-despliegue.md):
  Tauri sirve la aplicación desde el sistema de archivos y puede convenir pasar a
  enrutado por hash.
