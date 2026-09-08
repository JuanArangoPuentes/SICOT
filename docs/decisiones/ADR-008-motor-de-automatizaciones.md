# ADR-008 — Dónde viven las automatizaciones de SICOT

**Estado:** Aceptada · **Fecha:** 8 de septiembre de 2026

## Contexto

SICOT tiene un hueco funcional concreto y verificable: **nada en el sistema crea
nunca una alerta**.

La tabla `alertas` existe desde `V1__create_sicot_schema.sql` y admite diez
tipos (`VENCIMIENTO`, `DOCUMENTO`, `FACTURA`, `FIRMA`, `IA`, `SECOP`,
`RECORDATORIO`, `SOLICITUD`, `RECHAZADO`, `CRONOGRAMA`). El backend sabe
listarlas y marcarlas como leídas. No hay una sola línea que inserte una. La
pantalla de alertas de un contrato real está vacía y lo seguirá estando por
construcción.

Lo único que hoy avisa de algo es el semáforo de cronograma de FR-010, y se
calcula **en el navegador** a partir de las fechas del contrato. Eso significa
que un contrato en rojo solo está en rojo mientras alguien lo esté mirando: no
hay registro, no hay correo, no hay historial de cuándo se puso en rojo ni de si
alguien lo vio.

Al plantear cómo llenar ese hueco aparecieron dos caminos:

1. **n8n**, un motor de automatizaciones externo.
2. Un **proceso separado** (aplicación de escritorio Java/Spring Boot instalada
   por máquina) que recibiera webhooks de SICOT y ejecutara automatizaciones y
   trabajos de IA contra la misma base de datos.

Ambos se descartan. Este ADR deja escrito el porqué, porque los dos van a volver
a proponerse.

### Por qué no n8n

- **Licencia.** n8n se distribuye bajo la *Sustainable Use License*, que no es
  software libre y restringe usos comerciales y de reventa. El proyecto tiene la
  regla explícita de mantenerse en herramientas gratuitas y sin límite de uso; una
  licencia con condiciones de uso es exactamente el tipo de dependencia que esa
  regla existe para evitar.
- **Segundo runtime y segundo almacén.** n8n trae su propio proceso Node, su
  propia base de datos de flujos y su propio depósito de credenciales. Para un
  equipo de tres personas eso es un segundo sistema que operar, respaldar,
  actualizar y parchear.
- **La lógica de negocio se sale del repositorio.** Un flujo de n8n es un grafo
  JSON editado en un navegador. No pasa por revisión de código, no tiene pruebas,
  no aparece en un `git diff` y no lo cubre Flyway. En un sistema de contratación
  pública, una regla que decide cuándo se alerta sobre un contrato es lógica de
  negocio y tiene que vivir donde vive el resto.
- **Credenciales duplicadas.** n8n necesitaría credenciales de la API o de la base
  para hacer algo útil, creando una segunda superficie de acceso con su propia
  rotación y su propia auditoría.

### Por qué tampoco un proceso separado con webhooks

- **El webhook a una máquina de escritorio no puede funcionar.** El equipo de un
  supervisor está detrás de NAT, con IP por DHCP, con el firewall de Windows y
  suspendiéndose al cerrar la tapa. El servidor no puede alcanzarlo. Empujar
  desde el servidor exige que el destino sea direccionable, y no lo es.
- **En el mismo host, el webhook es ceremonia pura.** Si el motor corre junto al
  backend, serializar un JSON, autenticarlo, reintentarlo y mantener dos procesos
  vivos hace exactamente lo que hace una llamada a método, con dos procesos más
  que desplegar y versionar en sincronía.
- **Escribir a PostgreSQL desde fuera rompe FR-002 y ADR-005.** El SPEC declara
  al backend única autoridad de reglas de negocio. Un proceso que escriba directo
  a la base se salta `TransicionesDeEstado`, la auditoría de `RegistroService` y
  el bloqueo optimista de `V12__bloqueo_optimista.sql`. Además `docker-compose.prod.yml`
  cerró a propósito el puerto de PostgreSQL para que solo el backend le hable;
  reabrirlo sería revertir una decisión de seguridad ya tomada.
- **Un segundo despliegue contradice ADR-001.** Ese ADR rechazó una segunda base
  de código para la aplicación del Supervisor con este razonamiento textual: «un
  equipo de tres personas puede sostenerlo». Un segundo artefacto Spring Boot
  instalado por máquina va en dirección contraria.

## Decisión

Las automatizaciones viven **dentro del backend**, en el paquete
`co.sena.sicot.automatizacion`, como un módulo explícito con cuatro piezas:

1. **Disparador por evento.** Todo evento de negocio de SICOT ya pasa por un
   único método, `RegistroService.registrar(contrato, accion, descripcion)` —
   trece puntos de llamada que cubren contratos, etapas, subetapas, documentos y
   firmas. Ese método publica ahora un `EventoDeNegocio`, y las reglas lo
   escuchan con `@TransactionalEventListener(AFTER_COMMIT)`. No hace falta tocar
   ningún servicio de negocio, y una regla nunca reacciona a una transacción que
   terminó en `rollback`.

2. **Disparador por calendario.** Un `@Scheduled` diario evalúa las reglas que
   dependen del paso del tiempo (vencimientos, atrasos de cronograma) contra los
   contratos vigentes.

3. **Cola persistente en la misma base.** Las reglas no ejecutan efectos: **producen
   tareas** en la tabla `tareas_automatizadas`. Un ejecutor las consume con
   reintentos y espera exponencial. La cola es persistente y no en memoria porque,
   con un solo host y un RPO de 24 h (ADR-002), una cola en memoria pierde trabajo
   en cada reinicio sin que nadie se entere.

4. **Carril de IA con presupuesto propio.** Las tareas que llaman al modelo se
   ejecutan de una en una y con su propio cupo, separado del que consumen los
   usuarios del copiloto.

### Las cuatro reglas invariantes del módulo

Estas son las que evitan que el módulo crezca hasta volverse otro sistema:

1. **Ninguna regla escribe a la base directamente.** Toda escritura pasa por los
   servicios existentes (`AlertaService`, `EmailService`, `OllamaClient`), de modo
   que la máquina de estados, la auditoría y el bloqueo optimista se mantienen.
2. **La regla decide, la IA solo redacta.** Vencimientos, atrasos y umbrales son
   aritmética de fechas y estados: deterministas, reproducibles y auditables, sin
   modelo. El modelo solo entra cuando el resultado es prosa.
3. **Una regla es una clase con su prueba.** Es precisamente lo que n8n no puede
   dar: las reglas entran por *pull request* y se revisan.
4. **Toda tarea es idempotente por clave.** Sin esto, una regla de calendario
   crearía la misma alerta cada día hasta convertir la bandeja en ruido, que es
   como mueren en la práctica los sistemas de alertas.

### El actor `SISTEMA` en la auditoría

`registros.usuario_id` es nulable, pero hoy los trece puntos de llamada corren
siempre bajo una petición autenticada, así que en la práctica nunca es nulo. Si
las automatizaciones empezaran a escribir con usuario nulo, en la pantalla de
auditoría aparecería un actor en blanco, indistinguible de un fallo.

Se añade por eso la columna `registros.origen` (`USUARIO` | `SISTEMA`). En un
sistema de contratación pública, poder distinguir «esto lo hizo una persona» de
«esto lo hizo el sistema» no es cosmético: es parte de lo que hace revisable un
expediente.

**No se crea un usuario ficticio «Sistema» en la tabla `usuarios`.** Una fila con
credenciales que nadie usa es una cuenta que alguien puede acabar usando, y
ensucia todos los listados de administración.

## Consecuencias

**Lo que se gana.** Las alertas empiezan a existir. El backend sigue siendo la
única autoridad. Las reglas son código revisable y probado. No hay segundo
proceso, ni segunda base, ni segundo juego de credenciales, ni licencia que
revisar. Todo el trabajo pendiente sobrevive a un reinicio.

**Lo que se pierde.** Una regla nueva exige recompilar y desplegar, en vez de
editarse en un navegador. Se acepta a conciencia: en este dominio, que cambiar
una regla de alertas requiera un *pull request* es una característica, no un
estorbo.

**Lo que queda prohibido.**
- Que una regla escriba a la base sin pasar por un servicio de dominio.
- Que una automatización llame al modelo de IA por fuera de `OllamaClient`.
- Que una tarea de calendario se cree sin clave de idempotencia.

**Relación con FR-010.** El semáforo del navegador **no se retira**. Sigue siendo
la vista en vivo, y es correcta. Lo que se añade es su contraparte persistente:
cuando el atraso cruza el umbral rojo, queda constancia en `alertas` con fecha, y
esa constancia sí sobrevive a cerrar el navegador. Son dos cosas distintas —
una vista y un hecho registrado— y conviven a propósito.

**Sobre la IA local por máquina.** La idea de «una IA pequeña en cada equipo» ya
es la realidad desplegada, pero en el **host de despliegue**, no en el portátil de
cada usuario: `docker-compose.yml` apunta a `host.docker.internal:11434`. Bajo la
interpretación B de ADR-001, la máquina del supervisor solo corre la cáscara de
escritorio y no ejecuta ni backend ni modelo. Instalar Ollama por usuario solo
tendría sentido bajo las interpretaciones A o C, que están fuera de alcance.

## Cuándo revisar

- Si aparece la necesidad de que **personal no técnico** edite reglas sin
  desplegar. Ese es el único argumento real a favor de un motor visual, y hoy no
  existe: las reglas las escribe el mismo equipo que escribe el backend.
- Si SICOT pasa a correr en **más de una instancia**. El reclamo de tareas ya está
  escrito para ser seguro con varias instancias (actualización condicional sobre
  el estado, no `SELECT` y luego `UPDATE`), pero convendría verificarlo con dos
  nodos reales antes de confiar en ello.
- Si el volumen de tareas supera lo que una tabla y un sondeo cada minuto
  atienden con holgura. El siguiente escalón es `LISTEN/NOTIFY` de PostgreSQL,
  no un intermediario de mensajes.
