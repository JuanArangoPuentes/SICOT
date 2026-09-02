# ADR-001 — Qué significa "instalación local" para el Supervisor

**Estado:** Aceptada · **Fecha:** 1 de septiembre de 2026

## Contexto

SICOT se planteó desde el principio con dos formas de despliegue: una aplicación
remota para GESTIÓN y ADMINISTRADOR, y una **instalación local** para el
SUPERVISOR. Esa segunda parte nunca se definió más allá de la frase.

El problema no es que falte construirla: es que **la frase admite tres
arquitecturas radicalmente distintas**, y el resto del sistema se estaba
construyendo asumiendo en silencio una de ellas.

| Interpretación | Qué implica de verdad |
| --- | --- |
| **A. Sin conexión real** | Base de datos local, cola de escrituras, resolución de conflictos, sincronización bidireccional |
| **B. Escritorio con conexión** | La misma aplicación web dentro de una ventana propia; exige red igual que hoy |
| **C. Lectura sin conexión** | Copia local solo de consulta; escribir exige red |

Hoy nada en el sistema contempla trabajar sin red: JWT sin estado sobre HTTP,
sin caché local, sin capa de sincronización. Es decir, **el sistema ya asumió la
interpretación B sin que nadie lo decidiera**.

El motivo por el que se pidió una instalación local nunca se documentó. La
hipótesis de trabajo es que un supervisor puede estar en planta o en obra con
conectividad intermitente — pero eso **no está confirmado con el SENA**.

## Decisión

Se adopta la **interpretación B**: la instalación local del Supervisor es un
contenedor de escritorio sobre la misma aplicación web, que **requiere
conexión** para operar.

Concretamente:

1. Se empaqueta con **Tauri** (gratuito, de código abierto, binarios pequeños)
   sobre el mismo frontend, sin una segunda base de código.
2. La interpretación **C queda pre-aprobada** como siguiente paso si aparece la
   necesidad: es incremental sobre B y solo añade una caché de solo lectura.
3. La interpretación **A queda explícitamente fuera de alcance** hasta que se
   cumplan dos condiciones, ambas por escrito: una necesidad documentada con
   casos reales, y una política de resolución de conflictos aprobada por el
   área jurídica o de contratación del SENA.

## Consecuencias

**Lo que se gana.** Una sola base de código y una sola fuente de verdad. El
backend sigue siendo la autoridad sobre todo dato oficial, que es justo lo que
un sistema de contratación pública necesita. Un equipo de tres personas puede
sostenerlo.

**Lo que se pierde.** Un supervisor sin red no puede trabajar. Es una limitación
real y hay que decirla de frente en la reunión institucional, no descubrirla en
producción.

**Lo que queda prohibido.** Introducir escrituras sin conexión "provisionales"
sin cerrar antes la política de conflictos. En contratación pública, dos
versiones divergentes de un acta firmada no son un problema de sincronización:
son dos documentos oficiales contradictorios. El costo de equivocarse aquí no
es técnico.

**Por qué no A, siendo lo más completo.** Sincronizar escrituras exige decidir
qué pasa cuando el mismo contrato se modifica en dos lugares a la vez. Esa
decisión es jurídica antes que técnica, y no puede tomarla el equipo de
desarrollo por su cuenta. Construir la maquinaria antes de tener la política
sería construir sobre una respuesta que todavía no existe.

## Cuándo revisar

- Si el SENA confirma que hay supervisores trabajando **sin conectividad de
  forma habitual** (no ocasional).
- Si aparece un caso real de pérdida de trabajo por falta de red.
- Cuando exista una política escrita de resolución de conflictos: eso desbloquea
  reconsiderar la interpretación A.
