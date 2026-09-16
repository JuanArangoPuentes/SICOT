# ADR-012 — Qué es "la aplicación móvil" de SICOT

**Estado:** Propuesta · **Fecha:** 16 de septiembre de 2026

## Contexto

SICOT tiene hoy dos formas: la aplicación web para navegador de escritorio y el
instalador de escritorio del Supervisor ([ADR-001](./ADR-001-bifurcamiento-de-despliegue.md)).
Las dos asumen una pantalla ancha. La tercera forma, el teléfono, se pidió como
una frase — y esa frase admite al menos tres arquitecturas distintas, igual que
pasó con "instalación local":

| Interpretación | Qué implica de verdad |
| --- | --- |
| **A. El navegador del teléfono** | Solo trabajo de interfaz. Nada que instalar ni que firmar |
| **B. Aplicación Android empaquetada sobre el mismo frontend** | Una sola base de código; icono propio, instalable; exige la cadena de herramientas de Android |
| **C. Aplicación nativa aparte (React Native, Flutter)** | Segunda base de código, con su propio ciclo de vida |

El punto de partida, medido antes de decidir nada
([auditoría del 16 de septiembre](../AUDITORIA_MOVIL_2026-09-16.md)): la interfaz
no estaba adaptada a una pantalla estrecha en absoluto. En las once pantallas el
ancho desplazable era igual al visible, de modo que lo que se salía del borde no
se podía alcanzar con ningún gesto. Un supervisor no podía siquiera iniciar
sesión desde un teléfono.

Eso importa para la decisión: **el trabajo que las tres interpretaciones
comparten —que la interfaz quepa— era el que faltaba entero**. Lo que las separa
es solo el envoltorio.

## Decisión

Se adopta la **interpretación B**: la aplicación móvil de SICOT es el mismo
frontend empaquetado para Android con Tauri, sin una segunda base de código.

Concretamente:

1. Se reutiliza el andamiaje de Tauri que ya existe para el escritorio. El
   proyecto de Android vive en `frontend/src-tauri/gen/android` y **se versiona**
   — el motivo está escrito en `frontend/src-tauri/.gitignore`.
2. La interpretación **A queda cubierta por el mismo trabajo**: toda la
   adaptación de interfaz sirve igual abriendo SICOT en el navegador del
   teléfono. No hay que elegir entre las dos.
3. La interpretación **C queda descartada** mientras el equipo sea del tamaño
   que es. Duplicar la interfaz de un sistema de contratación pública significa
   duplicar cada corrección de reglas de negocio, y quien mantenga esto en 2029
   heredaría dos sistemas que tienen que decir lo mismo y que se van a separar.

## Lo que este ADR **no** decide, porque nadie lo ha confirmado

Tres puntos quedan marcados como pendientes de la reunión institucional, y se
dicen aquí para que no se conviertan en supuestos silenciosos:

1. **Para qué rol es.** La hipótesis de trabajo es el **SUPERVISOR**, porque es
   el único rol del que ya se dijo que trabaja fuera de un escritorio. Es una
   hipótesis, no un hecho: nadie del SENA la ha confirmado. La adaptación de
   interfaz se hizo igualmente para los tres roles, que era lo barato y lo
   honesto — si mañana resulta que Gestión también usa teléfono, no hay nada que
   rehacer.
2. **Exige conexión**, igual que el escritorio. Trabajar sin red sigue fuera de
   alcance por ADR-001, y por el mismo motivo: dos versiones divergentes de un
   acta firmada no son un problema de sincronización, son dos documentos
   oficiales que se contradicen.
3. **Sin iOS.** Compilar para iPhone exige macOS con Xcode, que el equipo no
   tiene, y distribuir exige un programa de desarrollador de pago, que choca con
   la restricción de que SICOT sea gratuito de punta a punta. No es un pendiente
   que se resuelva con tiempo: es un costo que hoy el proyecto no puede asumir.

## Consecuencias

**Lo que se gana.** Una sola base de código para escritorio, web y teléfono. El
backend sigue siendo la única autoridad sobre el dato oficial. Y la dirección
del servidor ya se elige por instalación y no viaja dentro del binario, así que
el mismo artefacto sirve para cualquier Centro.

**Lo que se pierde.** Todo lo que un envoltorio web no da: notificaciones push,
cámara, biometría. Ninguna de esas capacidades la ha pedido nadie, y añadirlas
sin que nadie las pida sería inventar el proceso institucional.

**Lo que queda pendiente y hay que decir en voz alta.** Android bloquea el
tráfico sin cifrar en las compilaciones de publicación. Hoy el andamiaje lo
permite solo en la compilación de depuración, que es lo correcto. Pero significa
que **una instalación de publicación contra un servidor de Centro sin TLS
fallaría en silencio**: las peticiones no salen y no hay error visible. Eso no se
resuelve aquí —depende de [ADR-009](./ADR-009-terminacion-tls.md)— y no se puede
escribir por adelantado una excepción acotada porque nadie ha dado todavía el
nombre del servidor del Centro. Inventarlo sería peor que dejarlo escrito.

**Lo que queda prohibido.** Abrir una segunda base de código para móvil sin
reemplazar antes este ADR. Y publicar en una tienda de aplicaciones sin una
decisión previa sobre la cuenta institucional y su costo.

## Cuándo revisar

- Si la reunión institucional confirma que el supervisor trabaja **sin
  cobertura** en planta o en obra. Entonces esto ya no es un problema de
  empaquetado sino el de ADR-001, y hay que reabrir aquella decisión primero.
- Si aparece un requisito que un envoltorio web no puede cumplir —firma con
  certificado del dispositivo, evidencia fotográfica con metadatos, trabajo sin
  red—. Uno solo de ellos no justifica la interpretación C; tres sí.
- Si el SENA entrega equipos iOS, que obligaría a replantear el costo del
  programa de desarrollador frente a la restricción de gratuidad.
