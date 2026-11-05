# ADR-016 — Que dos equipos se vean casi al instante

**Estado:** Aceptada, ejecutada el 3 de noviembre de 2026 · **Fecha:** 3 de noviembre de 2026

## Contexto

SICOT se usa desde más de un sitio a la vez. El supervisor tiene el expediente
abierto en el computador y el teléfono en la mano; Gestión carga una ficha
mientras el supervisor mira ese mismo contrato; y desde ADR-015 el mismo
supervisor puede estar en dos equipos porque uno de ellos volvió de trabajar
sin red.

Medido sobre el código el 3-11-2026, no supuesto: **no había ningún mecanismo
para que un equipo se enterara de lo que hizo otro.**

- No hay sondeo periódico de datos. Los tres `setInterval` del cliente son el
  reenvío de la cola (`colaDeCambios.ts`, cada 30 s, que **sube**), la vigilancia
  de la vuelta del servidor (`vueltaDelServidor.ts`, cada 15 s y **solo** en
  sesión sin red) y el precalentado del Copiloto (cada 10 min).
- No hay canal de empuje. Ni WebSocket, ni SSE, ni `EventSource`. El único
  `StreamingResponseBody` del backend es la respuesta en vivo del Copiloto, que
  nace con la pregunta y muere con ella.
- No hay refresco al volver la ventana al primer plano. El único
  `visibilitychange` del panel del supervisor precalienta el Copiloto.
- `useRecursoDelContrato` dice de sí mismo, en su propia documentación, que no
  revalida.

El resultado: **con la pantalla abierta, lo que hacía el otro equipo no
aparecía nunca.** Había que cambiar de contrato, volver a entrar o recargar la
página. Nada estaba roto, y por eso no se había visto: con un solo equipo el
comportamiento es indistinguible del correcto.

## Decisión

Se adopta **sondeo de un registro de novedades**, y **no** un canal de empuje.

El servidor expone `GET /api/contratos/{id}/novedades?desdeId=N`, que contesta
qué entradas de la auditoría del contrato son posteriores a la `N` y cuál es la
marca con la que preguntar la próxima vez. El cliente pregunta cada 5 segundos
mientras tiene la ventana delante, cada 30 con la ventana detrás, y **en el
acto** al volver a ella. Cuando algo vuelve, no lo aplica: **vuelve a pedir las
pantallas enteras**.

### Por qué sobre la auditoría y no sobre las entidades

`registros` ya es el punto por el que pasa todo cambio relevante de SICOT — es
la misma razón por la que ADR-008 enganchó ahí el motor de automatizaciones. Y
ninguna de las entidades que escribe el supervisor tiene columna de última
modificación: filtrar por ellas exigiría añadirla a nueve tablas y acordarse de
mantenerla en cada sitio que escribe. Con la auditoría, si quedó constancia se
avisa; y una acción nueva que alguien añada mañana se avisa sola, sin tocar
este código.

### Por qué la marca es un identificador y no una hora

Porque los dos equipos no tienen por qué estar en hora, y no hace falta que lo
estén. Con una marca de tiempo del cliente, el equipo adelantado preguntaría
«desde una hora que aún no ha llegado» y se saltaría cambios **para siempre**.
Devolver la hora del servidor lo arregla a medias: sigue habiendo que elegir un
margen de solape, y ese margen o repite o pierde. Se probó, y la primera
versión repetía la auditoría entera del montaje en cada arranque.

El identificador de la auditoría es creciente y lo asigna la base. «Mayor que
N» no admite interpretación: ni repite ni pierde, y el reloj de nadie
interviene. Es la misma razón por la que ADR-015 guarda aparte la hora que
declara un equipo sin red.

### Por qué sondeo y no empuje

| Forma | Por qué no |
| --- | --- |
| **WebSocket / STOMP** | Una dependencia nueva, un protocolo nuevo, una conexión abierta por cliente y toda la reconexión a mano. Para un Centro con un puñado de supervisores, el coste no guarda ninguna proporción con el problema |
| **SSE** | Más barato que WebSocket, pero arrastra la trampa que ADR-014 ya documentó: desde Spring Framework 7 el flujo que Spring entrega al cuerpo ignora `flush()`, y Tomcat retiene los eventos hasta el final — medido el 28-10-2026: 116 eventos en un solo instante, a los 57 s. Hay que escribir en el `OutputStream` del servlet, como hace `CopilotoController`. Además `spring.mvc.async.request-timeout` cortaría la conexión a los 10 minutos, así que hay reconexión igual |
| **Sondeo del registro** (esta) | Una consulta sobre el índice que ya existe, `(contrato_id, fecha)`: recorre solo las entradas de **ese** contrato —unos cientos en uno con el proceso completo—, sin migración, sin dependencias y sin estado en el servidor. El caso normal —no ha pasado nada— es de lo más barato que el sistema puede hacer. Si algún día un contrato acumula decenas de miles, un índice `(contrato_id, id)` la vuelve una sola búsqueda |

El sondeo también encaja con lo que SICOT es: un sistema que corre entero en la
red del Centro, sin servicios de pago y pensado para andar en cualquier PC. Un
canal abierto por cliente es estado que hay que vigilar; una pregunta cada
cinco segundos, no.

### Esto es un aviso, no los datos

Es la propiedad que sostiene todo lo demás. El cliente **nunca** aplica una
novedad: vuelve a pedir etapas, alertas, documentos y cronograma. El servidor
sigue siendo la única autoridad sobre el expediente, igual que en ADR-015, y no
hay una segunda implantación de las reglas en el cliente.

Tiene una consecuencia que conviene decir en voz alta: **el sondeo no promete
que ninguna entrada se quede sin avisar.** Dos escrituras simultáneas pueden
confirmarse en orden distinto al de sus identificadores, y una respuesta
cortada por el tope de 50 salta igual a la más reciente. No importa, y es por
esto: lo que se perdería es un **aviso**, nunca un cambio del expediente. El
efecto de lo que no se avisó llega con el aviso siguiente, o al volver a la
ventana, porque en los dos casos se relee todo.

## Lo segundo que hacía falta: que el otro equipo pudiera llegar

Construida la propagación, la prueba con dos computadores seguía sin
funcionar, y no por el sondeo. La aplicación web llamaba a la API por un
origen **horneado en el paquete** (`VITE_API_URL`, que Vite fija al construir):

- Construida con `localhost:8080`, el navegador del **otro** equipo entendía
  «la API está en mi propia máquina», donde no hay nada.
- Construida con la IP del portátil, servía hasta que la IP cambiara. Cambió
  el 03-11-2026 a media prueba, al pasar el portátil de una red a otra, y se
  cayó todo sin que nada dijera por qué.
- Y había que mantener cuadrados **tres** sitios con la misma dirección: el
  `VITE_API_URL` del build, el `connect-src` de la CSP que emite nginx y los
  orígenes de CORS del backend. Olvidar el segundo deja la pantalla vacía sin
  que falle nada en el servidor; olvidar el tercero, bloqueada en el navegador
  antes de enviar nada.

**Se adopta el mismo origen también en desarrollo.** El nginx de la imagen del
frontend reenvía `/api`, `/actuator` y la documentación al backend, igual que
hace Caddy en producción (ver `Caddyfile`), y el servidor de desarrollo de Vite
hace lo mismo por `server.proxy`. `VITE_API_URL` y `CSP_CONNECT_SRC_EXTRA`
pasan a estar **vacíos por defecto** en `docker-compose.yml`.

No es una forma nueva: es la que el propio código ya daba por supuesta. El
comentario de `apiBase` dice desde hace tiempo que «en la web el frontend y la
API comparten origen (ADR-009) y no hay nada que configurar». Desarrollo era la
excepción, y esa excepción es la que no sobrevivía a un segundo equipo.

Lo que se gana, además de que funcione: el otro equipo necesita **un** puerto
abierto en el cortafuegos (8443) en vez de dos, y no hay ninguna dirección que
acertar ni que volver a tocar cuando cambie la red. El 8080 se sigue
publicando y sirve igual — Swagger vive ahí, y el instalador de escritorio y el
APK pueden apuntar a cualquiera de los dos.

Detalles que el proxy sí tiene que respetar, y por qué:

- **Sin buffering en `/api`.** La respuesta del Copiloto llega token a token;
  guardada en un búfer llegaría entera al final y parecería que el streaming no
  funciona — el mismo síntoma que costó encontrar en ADR-014.
- **Espera larga** (960 s). Una pregunta abierta sobre CPU tarda minutos
  (`OLLAMA_TIMEOUT_SECONDS`); con el minuto por defecto de nginx, el proxy
  cortaría antes que el modelo.
- **25 MB de cuerpo.** Los documentos llegan hasta 20 MB
  (`spring.servlet.multipart.max-file-size`); con el 1 MB por defecto de nginx,
  la carga moriría en un 413 antes de que el backend la viera.
- **El `Host` original, puerto incluido** (`$http_host`). Añadido el
  05-11-2026, cuando la revisión antes de la prueba en otro PC encontró que
  faltaba: sin él nginx manda `Host: backend:8080`, Spring compara el `Origin`
  del navegador con ese Host para decidir si la petición es de otro origen, y
  el `POST` del inicio de sesión desde otro equipo (`Origin:
  http://<IP>:8443`) se rechazaba con **403 «Invalid CORS request»**. La
  página cargaba y no dejaba entrar. En el portátil no se veía porque
  `localhost:8443` está en `CORS_ALLOWED_ORIGINS`. Comprobado con `curl`
  antes y después: 403 con el Origin de la IP, y 401 tras el arreglo. Es lo
  que Caddy hace por omisión en producción, que por eso nunca lo tuvo. Así que
  la lista de orígenes del backend deja de importar para el navegador **por
  este reenvío**, no por el proxy en sí.
- **El nombre `backend` resuelto en cada petición**, con el DNS de Docker y
  una variable en `proxy_pass`. Con el nombre literal nginx lo resolvía una
  sola vez al arrancar: si el backend aún no existía —al reiniciar el PC,
  Docker no respeta el orden de `depends_on`— el frontend no arrancaba, y si
  el backend se recreaba con otra IP, todo `/api` daba 502.

Y una limitación que se acepta en desarrollo: detrás del nginx, el backend ve a
todos los navegadores con la dirección del propio nginx, así que el tope de
intentos fallidos de inicio de sesión (`LoginAttemptService`, 20 por
dirección) cuenta entre todos los equipos de una prueba. Confiar en
`X-Forwarded-For` en desarrollo no lo arregla bien: el 8080 también está
publicado, y cualquiera de la red podría mandar esa cabecera directamente. En
el Centro no pasa, porque el perfil `prod` confía en Caddy
(`server.forward-headers-strategy=native`).

## Consecuencias

**Lo que se gana.** Dos equipos sobre el mismo contrato se ven con un retraso
de unos segundos, sin que nadie recargue nada. Y se cierra de paso el hueco de
volver a la ventana: alternar entre aplicaciones ya no deja la pantalla
congelada en el instante en que se abrió.

**Lo que cuesta.** Una petición cada 5 segundos por cada pantalla de contrato
abierta. Con un supervisor por contrato y un puñado de contratos a la vez, es
ruido de fondo frente a cualquier otra cosa que hace el backend; con el Centro
entero mirando el mismo día habría que medirlo antes de bajar el intervalo.

**Lo que empeora.** Nada que se haya medido. El sondeo se calla entero cuando
no hay token de sesión, así que la sesión sin red de ADR-015 no lo nota, y se
espacia tras un fallo para no llenar la consola cuando el camino al servidor
está cortado.

**Lo que no cambia.** El servidor sigue siendo la única autoridad. Ninguna
regla del expediente se duplica en el cliente. Firmar, generar y cargar
documentos siguen necesitando red.

## Qué haría falta para bajar de unos segundos

Si algún día cinco segundos no bastan, el paso siguiente es SSE **sobre este
mismo endpoint**, no un rediseño: el cliente ya sabe pedir «desde N» y ya sabe
qué hacer cuando le avisan. Lo que habría que resolver está escrito arriba —
escribir en el `OutputStream` del servlet por el `flush()` no-op de Spring 7, y
reconectar por el corte de `spring.mvc.async.request-timeout`. Mientras nadie
lo pida con un caso real, no se construye.

## Dónde está

- Servidor: `RegistroController.novedades`, `RegistroService.novedadesDesde`,
  `RegistroRepository.findByContratoIdAndIdGreaterThanOrderByIdDesc` y
  `ultimoIdDelContrato`, `dto/registro/NovedadesResponse`.
- Cliente: `services/novedadesService.ts`, `hooks/useNovedadesDelContrato.ts`,
  enganchado en `screens/SupervisorPanel.tsx`. Las listas se ponen al día al
  volver a la ventana con `hooks/useAlVolverALaVentana.ts`, usado en `App.tsx`
  y en `screens/GestionPanel.tsx`.
- Mismo origen: `frontend/nginx.conf.template` (bloques `location /api/` y
  siguientes), `frontend/vite.config.ts` (`server.proxy` y `preview.proxy`) y
  los valores por defecto de `docker-compose.yml`.
- Pruebas: `NovedadesEntreEquiposIntegrationTest` (servidor, 8 casos),
  `useNovedadesDelContrato.test.tsx` (cliente, 9 casos) y
  `useAlVolverALaVentana.test.tsx` (cliente, 7 casos).
- Al llegar una novedad el panel relee también la auditoría y la ficha del
  contrato (`getContrato`), no solo etapas, alertas, documentos y cronograma:
  si Gestión cambiaba las fechas desde otro equipo, la cabecera seguía con las
  de antes. Añadido el 05-11-2026.
- Cómo probarlo con dos equipos de verdad:
  [`docs/operacion/PROBAR_EN_OTRO_DISPOSITIVO.md`](../operacion/PROBAR_EN_OTRO_DISPOSITIVO.md).
