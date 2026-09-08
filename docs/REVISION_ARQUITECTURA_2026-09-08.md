# Revisión de arquitectura — SICOT

**8 de septiembre de 2026 · revisión crítica**

La auditoría ([`AUDITORIA_2026-09-08.md`](./AUDITORIA_2026-09-08.md)) mide el
estado. Este documento juzga las **decisiones**: qué está mal decidido, qué falta
por decidir, y qué conviene dejar exactamente como está.

Va en ese orden a propósito. La recomendación más valiosa que puede dar una
revisión de arquitectura sobre un proyecto de tres personas suele ser *no
reestructures esto*, y conviene decirlo antes que las críticas para que no se
pierda entre ellas.

---

## Veredicto

**SICOT está mejor construido que la mayoría de los sistemas de su tamaño.** El
backend es una autoridad real, no un pasamanos de la base de datos. Las
decisiones difíciles están escritas en ADR con su porqué y con lo que se pierde.
El sistema falla honesto donde otros fingirían. Hay 294 pruebas y una CI que
corre contra PostgreSQL de verdad.

**Y tiene tres problemas estructurales**, uno de los cuales se acaba de
introducir con el motor de automatizaciones.

Lo que sigue es duro a propósito. Un sistema de contratación pública que va a
vivir tres o cuatro años merece una revisión que busque de verdad dónde se va a
romper.

---

## Lo que NO hay que cambiar

**El monolito.** Once mil líneas repartidas en paquetes por capa técnica
(`controller`, `service`, `repository`, `dto`). Un revisor con prisa recomendaría
paquetes por dominio, puertos y adaptadores, o directamente separar servicios.
Sería un error.

El equipo son tres personas y una parte son aprendices que rotan. Una
arquitectura hexagonal multiplica por tres los archivos que hay que tocar para
añadir un campo, y su beneficio —poder sustituir la infraestructura sin tocar el
dominio— resuelve un problema que este proyecto no tiene y no va a tener: la base
va a seguir siendo PostgreSQL y la API va a seguir siendo REST. La estructura
actual se lee de arriba abajo y un aprendiz encuentra las cosas. Eso vale más.

**Guardar los documentos como `BYTEA`.** ADR-003 ya fijó el umbral y
`VigilanciaDeAlmacenamiento` lo mide sola. La decisión está tomada, medida y
vigilada. No se toca hasta que la métrica lo pida.

**El JWT sin estado, sin token de refresco.** Con ocho horas de validez y
`JwtAuthenticationFilter` comprobando `activo` en cada petición, la revocación ya
es inmediata. Un token de refresco añadiría almacenamiento con estado, rotación y
una superficie nueva, a cambio de una comodidad que nadie ha pedido.

---

## Problema 1 · Hay dos cronogramas y no dicen lo mismo

**Este es el hallazgo más grave de la revisión, y lo introdujo el cambio que
acabo de hacer.**

El frontend calcula el semáforo así (`SupervisorPanel.tsx:468`): reparte el plazo
del contrato en **seis segmentos iguales**, uno por etapa, y compara la fecha de
hoy con el final del segmento de la etapa activa.

La regla de backend que acabo de añadir (`AvisoDeCronogramaAtrasado`) calcula
otra cosa: la **brecha entre la fracción de plazo consumida y la fracción de
subetapas cerradas**, y alerta a partir de 30 puntos.

Son dos algoritmos distintos respondiendo a la misma pregunta. Un supervisor
puede abrir su panel y leer *«Paso 3 va a tiempo»* mientras la bandeja de alertas
del mismo contrato dice *«brecha de 39 puntos, revise el avance»*. Las dos frases
las produce SICOT. Las dos son defendibles por separado. Juntas, el sistema se
contradice a sí mismo sobre un dato del que depende una decisión de supervisión.

**ADR-008 justifica que convivan una vista y un hecho registrado. Eso sigue
siendo correcto. Lo que no es defendible es que el cálculo esté duplicado**, en
dos lenguajes, con dos definiciones distintas de «atraso», y que nada obligue a
que coincidan. Es la violación exacta de FR-002 —el backend es la única autoridad
de las reglas de negocio— cometida por el mismo cambio que dice defenderla.

**Qué hacer.** El backend calcula y expone el estado del cronograma; el frontend
lo pinta. Un único cálculo, en un único sitio, con una única prueba.

## Problema 2 · El respaldo es una promesa sin mecanismo

ADR-002 compromete un RPO de 24 horas. El script existe, está probado y se
verificó de punta a punta. **Nada instala el cron que lo ejecuta.**

Lo que convierte esto en un problema de arquitectura y no en una tarea pendiente
es que **el proyecto ya aprendió esta lección y la escribió**. ADR-006, tras
desplegar un modelo de IA que no estaba descargado:

> «un paso manual escrito en un documento no es un control»

Y la solución fue correcta: `VerificacionDelModeloIa` comprueba al arrancar y
avisa en el log con el comando exacto que falta.

El respaldo tiene la misma forma —una promesa escrita en un ADR que depende de
que alguien se acuerde— y no recibió el mismo tratamiento. Un equipo que
identifica un patrón de fallo, escribe el control para una instancia y no lo
aplica a la otra instancia idéntica que tiene delante, va a repetirlo.

**Qué hacer.** Que el sistema mida cuándo fue el último respaldo verificado y lo
grite si excede el RPO comprometido. La misma forma que ya funcionó dos veces.

## Problema 3 · El frontend no tiene arquitectura de estado

`SupervisorPanel.tsx` son **948 líneas** que sostienen a mano el estado de todo
el panel: contrato, etapas, subetapas, documentos, alertas, copiloto, semáforo.
Cada uno con su `useState`, su `useEffect`, su bandera de carga y su bandera de
error, y con las recargas encadenadas a mano después de cada escritura.

No es un problema de estilo. Es de dónde salen estas consecuencias:

- **8 236 líneas de frontend con 28 pruebas.** No es desidia: un componente que
  mezcla obtención de datos, estado y presentación en el mismo archivo es
  genuinamente difícil de probar, así que no se prueba.
- **Cada escritura obliga a recordar qué recargar.** Es exactamente la clase de
  olvido que produce el bug de «lo guardé y no se ve», y ya ocurrió antes en este
  proyecto con la persistencia silenciosa de subetapas.
- **El aviso de `act(...)` que la suite emite y nadie atiende** es un síntoma de
  lo mismo: efectos que se disparan fuera del control del componente.

**Qué hacer, y qué no.** Meter una librería de estado de servidor hoy es un
cambio grande y arriesgado. Lo que sí procede ahora es **extraer los efectos de
datos a hooks propios por dominio**, dejando el componente como presentación. Es
incremental, se puede hacer un dominio por PR, y cada hook extraído es
inmediatamente probable. Si más adelante el equipo quiere una librería, la
migración parte de hooks ya aislados en vez de un archivo de mil líneas.

---

## Lo que falta decidir (no está mal, está sin decidir)

**HTTPS.** No es una tarea pendiente: es una decisión sin tomar. Terminar TLS en
el nginx del frontend, poner un proxy inverso delante, o dejarlo al
`Application Load Balancer` de la red del SENA son tres respuestas distintas con
consecuencias distintas. Mientras no se decida, el JWT viaja en claro y
`Strict-Transport-Security` es una cabecera que ningún navegador aplica. Merece
su propio ADR.

**Versionado de la API.** Todas las rutas cuelgan de `/api/...` sin versión. Hoy
el único cliente es el frontend, que se despliega junto al backend. Pero ya hay
un servidor MCP que consume la misma API con su propio ciclo de vida, y ADR-001
compromete una aplicación de escritorio Tauri que se instalará en máquinas y no
se actualizará al mismo tiempo que el servidor. Un cambio incompatible romperá
esos clientes sin aviso. La decisión no es urgente; tomarla antes de que existan
tres clientes sí lo es.

**Quién mira las métricas.** El backend publica métricas de Prometheus muy bien
elegidas —incluida la de almacenamiento de ADR-003 y las cinco que acaba de
añadir el motor—. **Nada las raspa.** No hay Prometheus ni Grafana en ningún
compose, ni ningún destino de alertas. Son métricas que existen para que alguien
entre a `/actuator/prometheus` y las lea a mano, que es lo mismo que decir que
nadie las lee. Instrumentar sin recolectar es trabajo hecho a medias.

**Retención de datos.** Ninguna tabla de crecimiento libre tiene política de
purga: ni `registros`, ni `alertas`, ni la `tareas_automatizadas` que acabo de
añadir. Para un expediente de contratación pública la respuesta correcta
probablemente sea *no se purga nunca*, pero eso es una decisión jurídica que hay
que tomar y escribir, no un vacío que se hereda.

---

## Lo que hay que mejorar, en orden

| # | Qué | Por qué ahora |
| --- | --- | --- |
| 1 | Un solo cálculo de cronograma, en el backend | El sistema se contradice consigo mismo |
| 2 | Vigilancia del respaldo | El RPO comprometido no tiene mecanismo |
| 3 | `origen` en la auditoría del frontend | Atribuye al sistema acciones de personas |
| 4 | Cobertura de `automatizacion.acciones` | Deuda del cambio en curso, se salda aquí |
| 5 | Purga de la cola de automatizaciones | Deuda del cambio en curso, se salda aquí |
| 6 | Paginación en listados por contrato | El motor empieza a llenar `alertas` |
| 7 | Extraer hooks de datos del `SupervisorPanel` | Habilita probar el frontend |
| 8 | ADR de HTTPS · ADR de versionado de API | Decisiones, no tareas |

Los puntos 1 a 6 son acotados y se aplican en este mismo cambio. El 7 es
incremental y se empieza aquí. El 8 son decisiones que necesitan a más gente que
a quien escribe el código.

---

## Qué se aplicó (mismo día)

Todo lo de la tabla se ejecutó en este cambio. Lo que sigue es qué quedó, con la
evidencia.

| # | Qué se hizo | Dónde |
| --- | --- | --- |
| 1 | Un solo cálculo de cronograma, del lado del servidor | `service/Cronograma.java` · `GET /api/contratos/{id}/cronograma` |
| 2 | Vigilancia diaria del respaldo con métrica propia | `config/VigilanciaDelRespaldo.java` |
| 3 | La auditoría usa `origen` en vez de la ausencia de nombre | `services/mappers.ts` |
| 4 | Cobertura de las acciones del motor | `AccionesDeAutomatizacionTest` |
| 5 | Purga diaria de la cola, con retención configurable | `AlmacenDeTareas.purgarResueltas` |
| 6 | Tope en los listados por contrato | `AlertaService` · `RegistroService` |
| 7 | Primer hook de datos extraído del panel | `hooks/useRecursoDelContrato.ts` |
| 8 | Las dos decisiones, escritas | ADR-009 (TLS) · ADR-010 (versionado) |

**ADR-009 se implementó, no solo se escribió.** El perfil de producción incorpora
un proxy inverso (Caddy) que pasa a ser el único servicio con puertos publicados;
el backend y el frontend dejan de exponerlos. El frontend se compila con
`VITE_API_URL` vacío —mismo origen—, lo que además elimina el problema que
arrastraba el proyecto: la URL de la API quedaba horneada en el build y un
frontend compilado con `localhost` no funcionaba desde ninguna otra máquina.

### Verificación

| Comprobación | Antes | Después |
| --- | --- | --- |
| Suite backend | 294 | **325** pruebas, 0 fallos |
| Cobertura de líneas | 85,6 % | **87,0 %** |
| `automatizacion.acciones` | 35,3 % | cubierto por 9 pruebas nuevas |
| Suite frontend | 28 | **36** pruebas, 0 fallos |
| Esquema contra PostgreSQL 18.6 | 5 | 5 pruebas, 0 fallos |
| `npm run build` | verde | verde |

La prueba que impide que el problema 1 vuelva es
`CronogramaIntegrationTest.laPantallaYLaAlertaDicenLoMismoSobreElMismoContrato`:
compara el texto que responde la API con el que la regla dejó escrito en
`alertas`. Si alguien vuelve a duplicar el cálculo, esa comparación se rompe.

Y la vigilancia del respaldo empezó a hacer su trabajo en el primer arranque:

```
WARN  c.s.sicot.config.VigilanciaDelRespaldo :
 No hay vigilancia del respaldo: falta RESPALDO_DIRECTORIO.
 ADR-002 compromete un RPO de 24 h apoyado en un respaldo diario…
```

Ese aviso es el hallazgo A, ahora dicho por el propio sistema en vez de por una
auditoría que alguien tiene que acordarse de hacer.

## Lo que queda pendiente, y por qué

No todo lo que encontró la auditoría se arregló aquí. Decirlo importa:

**La contraseña temporal sigue sin caducar (hallazgo E).** Obligar a cambiarla en
el primer ingreso necesita una columna nueva, un endpoint y una pantalla de
cambio forzado en el frontend. Es un cambio de alcance propio y mezclarlo con
este habría hecho irrevisables los dos. Debería ser el siguiente.

**La accesibilidad sigue sin verificarse (hallazgo I).** SICOT es un sistema de
una entidad pública colombiana, sujeta a la Resolución 1519 de 2020. Hace falta
una auditoría con herramientas específicas (`axe`, revisión con lector de
pantalla) y arreglos por pantalla; no es algo que se resuelva de paso.

**Nadie raspa las métricas.** El backend publica once métricas bien elegidas —dos
de ellas nuevas de este cambio— y no hay ningún Prometheus que las recoja. Añadir
el recolector es fácil; decidir quién mira las alarmas y por qué canal es la
parte que hace falta, y esa decisión necesita a más gente que a quien escribe el
código.
