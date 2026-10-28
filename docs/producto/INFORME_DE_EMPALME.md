# SICOT — Informe de empalme

**Fecha:** 27 de octubre de 2026 · **Versión del software:** 0.6.0 · **Para:** empalme con la supervisión

Este documento sirve para explicarle SICOT a alguien que no lo ha visto: qué
hace, cómo se usa, en qué estado está de verdad y qué falta. Todo lo que
aparece aquí está comprobado contra el código y contra el sistema corriendo; lo
que no se ha comprobado se dice que no se ha comprobado.

---

## 1. Qué es SICOT y qué problema resuelve

SICOT acompaña el **ciclo de vida de un contrato** en el Centro Tecnológico del
Mobiliario, desde que Contratación lo suscribe hasta que el expediente se
archiva. No reemplaza al SECOP II ni al SIGEP: los complementa.

El problema que ataca es concreto. El procedimiento oficial **GCCON-P-010** es
largo, los formatos son varios (GCCON-F-018, F-031, F-032, F-030, GIL-F-010,
ESUCON) y un supervisor que lleva varios contratos a la vez no tiene dónde ver,
de un vistazo, en qué punto va cada uno y qué le toca ahora. Lo que hoy suple
eso son hojas de cálculo personales y la memoria de cada quien.

SICOT pone ese recorrido en pantalla, avisa solo cuando algo vence y arma los
documentos formales con los datos que ya están registrados, para que el
supervisor los revise y los firme en vez de redactarlos desde cero.

**Un principio que atraviesa todo el proyecto:** el sistema no inventa datos.
Si no sabe algo, lo dice. Si un envío falló, lo dice. Si la IA propone un dato
extraído de un PDF, es una **propuesta** que una persona confirma, nunca un
hecho consumado.

---

## 2. Quién usa qué

Hay tres roles, y no son intercambiables.

| Rol | Quién es | Qué hace en SICOT | Desde dónde |
|---|---|---|---|
| **GESTION** | Unidad de Gestión Contractual | Carga la ficha del contrato, asigna el supervisor, activa el contrato | Computador, desde el navegador |
| **SUPERVISOR** | El supervisor designado | Recorre los 6 pasos del contrato, carga evidencias, firma documentos | Teléfono (APK) o computador |
| **ADMINISTRADOR** | Quien administra el sistema | Cuentas, formatos institucionales, seguimiento de todos los supervisores | Computador |

El **supervisor** es quien más usa el sistema, y es el único para quien existe
una aplicación de teléfono. Eso es deliberado: su trabajo ocurre en bodega, en
obra, frente a la entrega — no en un escritorio.

---

## 3. Manual de uso — el recorrido del supervisor

Un contrato en SICOT tiene **6 pasos y 21 sub-pasos**, que son los del
procedimiento real, no una invención del programa.

### Paso 1 — INICIO · Designación de la supervisión (GCCON-F-032)

| Sub-paso | Qué es | Responsable |
|---|---|---|
| 1.1 | Suscripción y publicación del contrato en SECOP II | Unidad de Contratación |
| 1.2 | Designación del supervisor y del apoyo a la supervisión (GCCON-F-032) | Ordenador del gasto |
| 1.3 | Registro del contrato en SICOT y aviso al supervisor | Apoyo a la supervisión |

El supervisor **no hace nada aquí**: cuando Gestión le asigna el contrato, este
paso ya está cerrado y le llega el aviso.

### Paso 2 — INICIO · Acta de Inicio y arranque (GCCON-F-018)

| Sub-paso | Qué es |
|---|---|
| 2.1 | Firma y devolución del Acta de Inicio (GCCON-F-018) |
| 2.2 | Aviso a los profesionales SST y ambiental |
| 2.3 | Verificación de especificaciones en SECOP II |
| 2.4 | Contacto con el contratista y acuerdo de entregas |

Aquí empieza el trabajo real. SICOT le arma el acta con los datos del contrato;
él la revisa y la firma. En el 2.3 el sistema consulta los datos abiertos del
SECOP y le muestra las diferencias contra lo que tiene registrado. En el 2.4
guarda las fechas de entrega acordadas y los contactos de SST y ambiental, que
antes no tenían dónde quedar.

### Paso 3 — INSPECCIÓN · Monitoreo y ejecución

| Sub-paso | Qué es |
|---|---|
| 3.1 | Verificación física de la entrega en bodega |
| 3.2 | Carga de evidencia fotográfica georreferenciada |
| 3.3 | Comparación cantidad/calidad vs. ficha técnica |
| 3.4 | Firma del Informe de Supervisión (GCCON-F-031) |

Este es el paso del teléfono: el supervisor está frente a la entrega, toma las
fotos desde la aplicación y las carga ahí mismo.

### Paso 4 — RECEPCIÓN · Acta de Recibo a Satisfacción

| Sub-paso | Qué es |
|---|---|
| 4.1 | Verificación de aportes a seguridad social (PILA) |
| 4.2 | Verificación de factura electrónica DIAN (FEV) |
| 4.3 | Firma del Acta de Recibo a Satisfacción (GIL-F-010) |

### Paso 5 — CERTIFICACIÓN · Cumplimiento y trámite de pago

| Sub-paso | Qué es |
|---|---|
| 5.1 | Verificación de vigencia de garantías |
| 5.2 | Revisión de orden de pago y CRP |
| 5.3 | Firma de la Certificación de cumplimiento |

### Paso 6 — CIERRE · Informe final y archivo (GCCON-F-030)

| Sub-paso | Qué es |
|---|---|
| 6.1 | Verificación de cumplimiento total del objeto contractual |
| 6.2 | Evaluación de modificaciones o adiciones (si aplica) |
| 6.3 | Firma del Informe Final de Supervisión (GCCON-F-030) |
| 6.4 | Cierre y archivo del expediente digital en SIGEP |

### Lo que ve el supervisor al abrir la aplicación

Una **bandeja de entrada** con tres cosas: en qué paso va su contrato, cuál es
el siguiente sub-paso, y las alertas pendientes. Abajo, una barra con Contrato,
Alertas, Documentos, Registros y Configuración.

Si tiene varios contratos, los cambia desde un selector arriba.

### Cómo firma un documento

SICOT arma el documento con los datos registrados y se lo muestra. Antes de
firmar hay un portillo —«Revisar antes de firmar»— donde puede cancelar, y
entonces el sub-paso queda abierto. Si firma, el documento queda con un sello de
integridad (SHA-256): si alguien altera el archivo después, SICOT lo detecta y
lo dice.

---

## 4. Manual de uso — Gestión y Administración

### Gestión Contractual

Carga la ficha del contrato. Puede hacerlo a mano o **subiendo el acta en PDF**:
la IA lee el documento y propone los campos —número de contrato, objeto,
contratista, NIT, representante legal, valor, fechas, lugar, registro
presupuestal— y Gestión los confirma o corrige. Nunca se guardan sin que alguien
los mire.

Después asigna el supervisor. Desde el 26 de octubre de 2026 puede hacerlo de
dos formas: eligiendo a uno que ya trabaja en SICOT, o **invitando a uno nuevo
por su correo**. En el segundo caso SICOT le crea la cuenta, le manda por correo
el usuario y la clave con los que entrará después de instalar el programa, y le
asigna el contrato, todo de una vez.

Si el correo no sale —porque no hay servidor de correo configurado— la pantalla
lo dice y muestra la clave para que Gestión la entregue en mano, avisando de que
no se vuelve a mostrar. Un supervisor que ya tiene cuenta conserva la suya.

Por último, **activa el contrato**. Eso importa: solo los contratos activos
entran en las reglas de calendario, es decir, solo esos se vigilan.

### Administración

Cuentas de usuario, carga de los formatos institucionales, y una pantalla de
**seguimiento de supervisores** donde ve en qué va cada uno.

---

## 5. Lo que el sistema hace solo

Hay un motor de automatizaciones con **siete reglas**. No usa el modelo de IA:
son reglas en Java con plantillas de texto, de modo que funcionan en un servidor
sin Ollama instalado.

| Regla | Cuándo dispara |
|---|---|
| `vencimiento-proximo` | 30, 15 y 7 días antes de terminar el plazo |
| `contrato-vencido` | Cuando el plazo pasó |
| `cronograma-atrasado` | Cuando el avance se queda detrás del tiempo consumido |
| `entrega-acordada` | Antes de cada fecha de entrega pactada con el contratista |
| `supervisor-asignado` | Al asignar supervisor a un contrato |
| `integridad-comprometida` | Si un documento firmado fue alterado |
| `resumen-semanal` | Resumen de los últimos 7 días, por contrato |

La cola es persistente y las tareas son idempotentes: si una regla ya emitió su
aviso, la siguiente ronda lo descarta en vez de duplicarlo.

**Comprobado el 27-10-2026:** una ronda encoló 9 tareas; 1 se ejecutó y 8 se
descartaron por repetidas, con 0 fallidas. El resumen que produjo es texto
calculado, no una plantilla con huecos: *«En el contrato CO1.PCCNTR.99015001
(plazo del 14/10/2026 al 15/12/2026), durante el periodo se cargó un documento.
El avance acumulado es de 4 de 21 subetapas (19%)».*

---

## 6. La inteligencia artificial: qué hace y qué no

SICOT usa un modelo que corre **en el servidor del Centro**, no en un servicio
de pago por Internet. Hoy es `qwen2.5:7b` sobre Ollama. Esto no es negociable en
el proyecto: nada de lo que SICOT necesita puede depender de una plataforma con
cuota o factura.

Hace dos cosas:

**Leer documentos.** Gestión sube el acta en PDF y la IA propone los campos.
Medido el 27-10-2026 sobre un acta institucional real: **11,4 segundos y 13
campos**, todos verificados uno por uno contra el texto del PDF. Ninguno
inventado.

**Acompañar al supervisor.** El Copiloto responde preguntas sobre el contrato.
Tiene dos caminos, y esa es la clave del diseño:

- Las preguntas del proceso —«¿en qué paso voy?», «¿qué me falta aquí?»— las
  contesta un camino determinista en **menos de 1 segundo, sin pasar por el
  modelo**. Son la mayoría.
- Las preguntas abiertas sí van al modelo: **unos 20 segundos hasta la primera
  palabra y 45–52 segundos en total** en el portátil de desarrollo. La respuesta
  se escribe en pantalla mientras se genera, para que no parezca colgado.

El motor de automatizaciones **tampoco** usa el modelo. Todo esto existe para
que baste una IA pequeña que corra en cualquier computador del Centro.

**Lo que la IA no hace:** no firma nada, no cierra sub-pasos por su cuenta y no
decide. Propone, y una persona confirma.

---

## 7. Estado del proyecto hoy

| | |
|---|---|
| Versión publicada | 0.6.0 (23 de octubre de 2026) |
| Primer commit | 17 de agosto de 2026 |
| Commits | 520 |
| Backend | 231 archivos Java, ~29.400 líneas |
| Frontend | 117 archivos TypeScript, ~25.900 líneas |
| Pruebas automáticas | **1.000 en el backend, 368 en el frontend** — todas en verde |
| Migraciones de base de datos | 16 (esquema en V23) |
| Endpoints de la API | 59, inventariados y vigilados por una prueba |
| Decisiones registradas (ADR) | 15 |

**Qué hay publicado y descargable** en las *Releases* del repositorio
(`JuanArangoPuentes/SICOT`): el instalador de Windows, el APK oficial firmado,
un APK de pruebas que acepta `http://` para la red local, y un paquete de
documentos de prueba.

---

## 8. Qué está hecho y comprobado

- El recorrido completo de los 6 pasos y 21 sub-pasos.
- Generación y firma de los documentos formales, con sello de integridad.
- El motor de automatizaciones, con sus siete reglas.
- La IA: extracción desde PDF y Copiloto conversacional.
- Aplicación de Android y instalador de Windows, ambos publicados.
- Invitación de supervisores por correo desde Gestión.
- **Copia local de lectura**: sin red, el supervisor sigue viendo el expediente,
  con una franja en pantalla que dice de cuándo es lo que está viendo.
- Consulta de los datos abiertos del SECOP II para contrastar la ficha.

---

## 9. Qué falta — por orden de importancia

### 9.1 Trabajo sin red de verdad (ADR-015)

Hoy el supervisor puede **leer** sin red, pero no **trabajar** sin red. La
sesión dura 8 horas, así que la copia local alcanza para terminar la jornada, no
para pasar tres días en campo.

Faltan cinco piezas, documentadas en ADR-015:

1. Identidad de lo creado sin conexión (identificadores locales y su tabla de
   equivalencias).
2. La cola de envíos y su reenvío, con clave de idempotencia.
3. Autenticación sin servidor — y esto **debilita la seguridad de forma real**:
   un portátil robado seguiría siendo válido durante una ventana que hay que
   elegir.
4. Una pantalla de lo que no se pudo aplicar al sincronizar.
5. Decidir si las alertas se congelan sin red (recomendado) o se recalculan.

### 9.2 Salir de Spring Boot 3.5 (ADR-014)

**Hecho el 28-10-2026.** La línea 3.5 ya no recibía parches gratuitos y había
dos vulnerabilidades críticas cuyo único arreglo era pasar a Boot 4. El backend
está ahora en Spring Boot 4.1.2 (soporte gratuito hasta el 31-07-2027), con
Jackson 3, y la compuerta de seguridad pasa sin excepciones. Lo que se encontró y
cómo se comprobó está al final de la ADR-014. El siguiente salto conviene
planearlo antes del 30-04-2027.

### 9.3 La prueba manual del APK

Son 17 puntos y es la compuerta antes de llevar nada a `master`. Dos quedan
«pasa con matiz» y conviene repetirlos en un teléfono real, no en el emulador:
el 11 y el 17, los dos sobre qué pasa cuando el Copiloto está respondiendo y uno
sale de la aplicación.

### 9.4 Envío por mensaje de texto

Gestión puede invitar a un supervisor por correo. **Por teléfono no**, y no está
previsto: mandar un SMS exige una pasarela de pago.

### 9.5 Lectura de documentos escaneados (OCR)

La extracción lee PDF con texto. Un acta escaneada como imagen no se lee.

### 9.6 Dependencias al día

Hay 10 propuestas de actualización automática abiertas, todas de versiones
menores.

---

## 10. Riesgos conocidos

**La IA es lenta en equipos modestos.** El portátil de desarrollo genera a unos
4 tokens por segundo. Con la máquina ocupada, una pregunta abierta llegó a
tardar 247 segundos y el tiempo de espera configurado son 240: **bajo carga, el
Copiloto se queda sin tiempo**. No es un defecto de programación sino de
capacidad de cómputo. Mitigado en parte porque las preguntas frecuentes no pasan
por el modelo.

**El encaminador determinista a veces se adelanta.** Una pregunta sobre ampliar
el plazo contractual fue atrapada por la palabra «plazo» y respondida con el
cronograma, que no era lo que se preguntaba. No es falso; es que no contesta.

**Un solo servidor.** Hoy la IA vive únicamente en el portátil de desarrollo.
Para que otro dispositivo pruebe, tiene que estar en la misma red.

**Un solo constructor.** El proyecto lo lleva una persona. Las decisiones están
escritas junto al código —15 ADR— precisamente para que eso no se vuelva un
problema.

---

## 11. Cómo se instala y cómo se prueba

**El servidor** corre con Docker: `docker compose up -d` levanta la base de
datos, el backend y el frontend. La IA corre aparte, con Ollama, en el mismo
equipo.

**En otro computador:** se instala `SICOT_<versión>_x64-setup.exe` y se le
indica la dirección del servidor en la pantalla de acceso.

**En un teléfono:** se instala el APK. El oficial solo habla `https://`; para
probar contra un portátil en la red local existe el APK de pruebas, que acepta
`http://`. En los dos casos hay que escribir la dirección del servidor antes de
entrar.

El detalle está en `docs/operacion/PROBAR_EN_OTRO_DISPOSITIVO.md` y en
`INSTALACION.md`.

---

## 12. Preguntas que probablemente le harán

**¿Esto reemplaza al SECOP?**
No. SICOT consulta los datos abiertos del SECOP II para contrastar la ficha,
pero el expediente oficial sigue siendo el del SECOP.

**¿Dónde quedan los datos?**
En el servidor del Centro. No hay ningún servicio externo de por medio, y la IA
corre en ese mismo servidor.

**¿Cuánto cuesta?**
Nada en licencias. Es una decisión del proyecto: ninguna pieza depende de una
plataforma de pago.

**¿Funciona sin internet?**
Sin internet sí, mientras haya red local contra el servidor. Sin **ninguna** red
todavía no del todo: se puede leer el expediente, no trabajar varios días. Eso
está planteado en ADR-015 y aún no construido.

**¿La IA decide algo?**
No. Propone datos extraídos de documentos y responde preguntas. Firmar, cerrar
un sub-paso y aprobar es siempre de una persona.

**¿Qué pasa si el modelo se equivoca?**
Lo que propone se muestra para confirmar, nunca se guarda solo. Y en el
acompañamiento, las respuestas del proceso —qué falta, en qué paso va— no las
da el modelo sino el propio sistema, con los datos registrados.

**¿Está listo para producción?**
Para probarlo con usuarios reales, sí. Antes de un despliegue institucional
quedan la migración de Spring Boot (ADR-014) y, si se quiere trabajo en campo
sin señal, ADR-015.

---

## Dónde está todo lo demás

| Qué busca | Dónde |
|---|---|
| Por qué se decidió algo | `docs/decisiones/` — 15 ADR |
| El proceso real, con los formatos | `docs/producto/PROCESO_REAL_EN_SICOT.md` |
| Instalar en el Centro | `INSTALACION.md` |
| Probar en otro equipo o teléfono | `docs/operacion/PROBAR_EN_OTRO_DISPOSITIVO.md` |
| La lista de prueba manual del APK | `docs/operacion/PRUEBA_MANUAL_APK.md` |
| Los endpoints de la API | `docs/api/INVENTARIO_ENDPOINTS.md` |
| El modelo de datos | `docs/operacion/MODELO_DE_DATOS.md` |
| Copias de seguridad | `docs/operacion/BACKUP_Y_RESTAURACION.md` |
