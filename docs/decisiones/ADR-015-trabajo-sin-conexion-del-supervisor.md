# ADR-015 — Trabajo sin conexión del supervisor

**Estado:** Propuesta · **Fecha:** 26 de octubre de 2026 · **Sustituye la decisión de** [ADR-001](./ADR-001-bifurcamiento-de-despliegue.md)

## Contexto

ADR-001 enumeró las tres lecturas de «instalación local» y adoptó la **B**
(escritorio con conexión), dejando la **A** (sin conexión real, con
sincronización) fuera de alcance hasta que se cumplieran dos condiciones por
escrito: una necesidad documentada con casos reales, y una política de
resolución de conflictos aprobada por el área jurídica o de contratación del
SENA.

El 26-10-2026 Juan decidió la **A**: el supervisor tiene que poder trabajar sin
ninguna red y sincronizar después. La primera condición queda cubierta por esa
decisión. **La segunda no**, y este ADR no la inventa: está entre las preguntas
abiertas del final, porque «qué vale cuando el expediente local y el del
servidor discrepan» es una pregunta jurídica, no técnica.

Conviene registrar una tensión con el cliente. Alex Zapata escribió en su lista
de chequeo (paso 1, columna REQUERIMIENTOS, sin cambios desde el 20-10-2026):
«ambos sitemas deben estar descargados de forma LOCAL, **pero se transfieren
información por la web**». Y sus pasos 1 y 4 dicen «SE DEBE ACCEDER AL SECOP».
Esta decisión va más allá de lo que él pidió; antes de construirla conviene que
él sepa que los pasos que dependen del SECOP no funcionarán sin conexión.

### Lo que SICOT ya tiene a favor

Medido sobre el código el 26-10-2026, no supuesto:

- **La superficie de escritura del supervisor son siete operaciones.** Cerrar o
  avanzar un sub-paso, cargar un documento o una foto, generar un documento,
  firmarlo, registrar fechas de entrega, registrar los contactos de SST y
  ambiental, y marcar una alerta como leída. Nada más. Un sistema con setenta
  mutaciones no admitiría este enfoque; con siete, sí.
- **Un solo supervisor por contrato.** Los dos escritores que pueden chocar son
  el supervisor y Gestión, y Gestión no toca los sub-pasos.
- **Bloqueo optimista ya implantado** (`@Version`) en Contrato, Documento,
  Etapa, Subetapa, EntregaAcordada, FirmaElectronica, FormatoDocumental,
  ItemSolicitudPedido y Usuario.
- **El Copiloto ya es local**: habla con Ollama en `127.0.0.1`, no con un
  servicio de pago en la nube.
- **Un registro de auditoría (`registros`) por contrato**, que es donde tendrá
  que constar qué se hizo sin conexión y cuándo se sincronizó.

### Lo que hoy lo impide

- **Todas las entidades usan `GenerationType.IDENTITY`**: el identificador lo
  asigna PostgreSQL. Una fila creada sin conexión no puede conocer el suyo.
- **El token de sesión dura 8 horas** (`JWT_EXPIRATION_MS=28800000`). Un
  supervisor desconectado más de una jornada no puede ni autenticarse.
- **Firmar tiene invariantes entre filas.** Además de comprobar la huella del
  borrador revisado, el servidor consulta que no exista ya otro documento
  firmado con el mismo nombre en ese sub-paso. Esa consulta no se puede
  responder con los datos de un solo portátil.
- **El contenido de los documentos vive en la base** (`bytea`, hasta 20 MB cada
  uno). Lo que se cargue sin conexión hay que guardarlo en el portátil y
  empujarlo después.
- **Las alertas y los recordatorios los calcula un temporizador del servidor.**
- **Los datos abiertos del SECOP no funcionan sin internet**, con o sin esta
  decisión.

## Decisión

Se adopta la interpretación **A** con la forma de **cola de intenciones**
(*outbox*) en el cliente, y **no** la de réplica bidireccional de bases de
datos.

El cliente guarda dos cosas: una **copia local de lectura** de los contratos del
supervisor, y una **cola ordenada de las siete operaciones** pendientes de
enviar. Sin conexión, la operación se encola y se aplica de inmediato sobre la
copia local, para que el supervisor vea su trabajo reflejado. Al recuperar la
red, la cola se reenvía **en orden** contra los mismos endpoints de hoy. El
servidor sigue siendo la única autoridad y valida exactamente igual que ahora.
Lo que rechace se le muestra al supervisor con el motivo y sin perder su
trabajo.

### Por qué esta forma y no las otras

| Forma | Por qué no |
| --- | --- |
| **Pila completa en cada portátil** (PostgreSQL + Spring Boot + Ollama, replicación bidireccional) | Reutiliza las reglas de negocio, pero exige Docker y 8 GB libres en cada portátil del Centro, y la réplica multi-maestro es la clase de sincronización más difícil que existe. El coste no guarda ninguna proporción con siete operaciones |
| **Reimplantar las reglas en el cliente** (SQLite o IndexedDB con la lógica en TypeScript) | Dos implantaciones de las reglas del expediente, que es justo lo que un sistema de contratación pública no puede permitirse: el día que diverjan, nadie sabrá cuál vale |
| **Cola de intenciones** (esta) | El servidor sigue siendo el único que decide. El cliente no inventa reglas: recuerda intenciones. Lo que se pierde es la respuesta inmediata del servidor, que es exactamente lo que se pierde al estar sin red |

## Lo que implica construir

Por partes, de la más barata a la más cara.

**1. Copia local de lectura.** Guardar en el portátil los contratos del
supervisor, sus etapas, sub-pasos, documentos y alertas, y servir las pantallas
desde ahí cuando no hay red. Es la interpretación C de ADR-001, que ya estaba
pre-aprobada, y es requisito de todo lo demás.

**2. Identidad de lo creado sin conexión.** El cliente **no** inventa
identificadores del servidor: usa identificadores locales propios y mantiene una
tabla de equivalencias que se rellena cuando el servidor responde con el suyo.
Toda referencia local (el documento que cierra un sub-paso, por ejemplo) se
reescribe al sincronizar. La alternativa —pasar las trece tablas a UUID— toca
el esquema entero y no hace falta.

**3. La cola y su reenvío.** Persistente, ordenada, idempotente. Cada operación
guarda qué se intentó, cuándo y sobre qué. El reenvío tiene que tolerar que la
red se corte a mitad sin duplicar lo ya aplicado: hace falta una clave de
idempotencia por operación, que hoy no existe salvo en el chat del Copiloto
(`idSolicitud`).

**4. Autenticación sin servidor.** El token de 8 horas no sirve. El cliente
tiene que poder verificar la contraseña contra un hash guardado en el último
acceso con red y abrir una sesión local, con una **ventana máxima sin
reconectar** pasada la cual exija volver a la red. Esto debilita la seguridad de
forma real: un portátil robado sigue siendo válido durante esa ventana. Elegir
su duración es una decisión, no un detalle.

**5. La pantalla de lo que no se pudo aplicar.** La parte que todo el mundo
subestima. Un supervisor trabaja tres días sin red y al sincronizar el servidor
rechaza el cierre del 2.1 porque Gestión reasignó el contrato. Hace falta una
pantalla que diga qué no entró, por qué, y qué puede hacer; y que su trabajo
siga ahí mientras lo resuelve.

**6. El cronograma y las alertas.** O el cliente recalcula el cronograma en
local —la lógica de `Cronograma` es determinista y se podría portar, a costa de
tenerla en dos sitios— o se acepta que sin red las alertas están congeladas y se
le dice así al supervisor. Recomiendo lo segundo: una alerta vieja y rotulada
como vieja es honesta; una recalculada en dos sitios distintos acaba
contradiciéndose.

## Consecuencias

**Lo que se gana.** El supervisor trabaja en bodega o en obra sin señal y su
trabajo llega entero cuando vuelve. Es lo que la decisión pide.

**Lo que cuesta.** Es la pieza más grande que se ha abordado en el proyecto:
más que el motor de automatizaciones y más que el Copiloto. Y no es un añadido
aislado — toca la autenticación, el modelo de identificadores, el cliente
entero (~18 000 líneas hoy) y la forma de todas las pantallas que escriben.

**Lo que empeora.** Tres cosas, y conviene decirlas antes y no después:

- La **seguridad de la sesión**, por la ventana sin reconectar del punto 4.
- La **confianza en las marcas de tiempo**: lo que se hace sin conexión lleva la
  hora del portátil, que su dueño puede cambiar. En un expediente público eso
  importa. Lo que se registre tendrá que distinguir «hora declarada por el
  equipo» de «hora en que el servidor lo recibió».
- La **capacidad de razonar sobre el sistema**: hoy, si algo está en el
  expediente, está. Con cola, hay un tercer estado —«hecho pero todavía no
  aceptado»— que hay que mostrar en todas partes.

**Lo que no cambia.** El servidor sigue siendo la única autoridad. No hay
réplica, no hay fusión automática de filas en conflicto, y ninguna regla del
expediente se duplica en el cliente.

## Preguntas que hay que responder antes de construir

Las dos primeras bloquean; las demás se pueden resolver sobre la marcha.

1. **¿Se firma sin conexión?** Es el nudo real, y es jurídico. La firma
   registra quién firmó, cuándo y el SHA-256 de lo firmado; sin red, la hora
   sale del reloj del portátil y la comprobación de «no hay ya otro firmado
   igual en este sub-paso» no se puede hacer. Lo más defendible es que **firmar
   exija red**: el supervisor hace todo lo demás sin conexión y firma al
   volver. Si Alex dice que se firma en campo, hay que llevarle a contratación
   la pregunta de qué vale una firma con hora declarada por el equipo.
2. **La política de conflictos que ADR-001 exige por escrito.** Qué prevalece
   cuando lo local y lo del servidor discrepan, y quién decide. Sin esto,
   cualquier regla que implantemos será una invención nuestra con consecuencias
   legales.
3. **¿Cuántos días seguidos sin red?** Dimensiona la ventana de sesión, el
   tamaño de la copia local y cuánto puede acumular la cola.
4. **¿El Copiloto tiene que funcionar sin conexión?** Si sí, cada portátil
   necesita Ollama, el modelo de 5 GB y unos 8 GB de memoria libres, y sobre CPU
   la primera pregunta de un contrato tarda más de dos minutos. Si no, es una
   función que se apaga sin red, como las consultas al SECOP.
5. **Qué pasa con los pasos 1 y 4 de la lista de Alex**, que dependen del SECOP
   y nunca funcionarán sin internet.
