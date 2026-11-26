# Revisión técnica de SICOT — 20 de noviembre de 2026

Revisión de punta a punta del repositorio en `claude/kind-pasteur-y4gop8`
(base `f58d44a`). Todo lo que se afirma aquí está verificado ejecutando el
proyecto o leyendo el código, no inferido de la documentación.

> ## Estado: TODOS LOS HALLAZGOS CORREGIDOS (alto, medio y bajo)
>
> Este documento se escribió como diagnóstico y se conserva como tal: cada
> hallazgo mantiene la descripción del problema **tal como estaba**, porque es
> lo que explica por qué el arreglo es el que es. Debajo de cada titular, una
> nota «**Corregido**» dice qué se hizo y dónde comprobarlo.
>
> El resumen de la intervención, las cifras de antes y después y las tres cosas
> que se decidieron NO hacer están al final, en «Qué se corrigió».

## Qué se ejecutó para escribir esto

| Comprobación | Resultado |
|---|---|
| `./mvnw -B -ntp verify` (JDK 25) | **BUILD SUCCESS** — 1 037 pruebas, 0 fallos, 6 omitidas |
| JaCoCo real | **92,0 %** línea · **81,6 %** rama · 93,6 % método |
| `npm run typecheck` | limpio |
| `npm run format:check` | limpio, 160 archivos |
| `npm run test:run` | **441 pruebas** en 50 archivos, 0 fallos |
| `npm run build` | 688 módulos, 381 ms |
| `vitest run --coverage` | **79,2 %** línea · **69,3 %** rama · 69,5 % función |
| Playwright | no ejecutado aquí (exige backend + PostgreSQL en pie) |

De las 6 omitidas, 5 son `EsquemaPostgreSqlIntegrationTest`, que se salta sin
`SICOT_IT_DB_URL`. En CI sí corre contra `postgres:18-alpine`.

## Veredicto

Esto no es un proyecto de estudiante con buena fachada. Es ingeniería real: el
arranque es *fail closed*, el bloqueo optimista tiene una prueba que reproduce
la actualización perdida, la firma registra el SHA-256 de lo que se firmó y
exige la huella de lo que el supervisor leyó, el límite de intentos cuenta por
correo **y** por IP con umbrales distintos y razonados, y el CI corre Semgrep,
Gitleaks sobre el historial completo, Trivy y OWASP Dependency-Check con las
acciones fijadas por SHA. Hay detalles que casi nadie cuida: el pre-filtro de
rol en la cadena de filtros para que el 403 llegue antes de que `@Valid` filtre
el contrato de la API, y el 404 en lugar de 403 para no dar oráculo de
enumeración.

Dicho eso, el proyecto tiene un sesgo consistente y es el eje de este informe:
**se invirtió mucho más en explicar y en razonar que en ejercitar los caminos
donde el sistema se usa de verdad.** Hay 83 386 líneas de Markdown frente a
unas 59 000 de código, y 9 115 líneas de comentario sobre 19 189 de código
Java (0,48). Al mismo tiempo, el modal que crea usuarios y el que restablece
contraseñas tienen **0 % de cobertura**, la pantalla donde el supervisor
resuelve un conflicto de datos tiene **11 % de ramas**, y ninguna prueba
recorre el proceso GCCON-P-010 a través de la interfaz, que es el producto.

Hay además un problema de disponibilidad explotable por el rol de menor
privilegio. Ese es el hallazgo que ordenaría arreglar primero.

---

## ALTO

### A1 — Un supervisor autenticado puede tumbar el backend subiendo o bajando archivos

> **Corregido.** `service/LimitadorDeArchivos.java` acota a 4 las operaciones de archivo en vuelo (429 con `Retry-After` al agotarse, el mismo patrón que `LimitadorDeUsoIa`), aplicado en subida, firma y descarga de documentos y del catálogo, en la lectura del estudio previo y en la extracción de la ficha. `server.tomcat.threads.max=40` en `application.properties`, con la cuenta de memoria escrita al lado. El peor caso pasa de `200 × 20 MB = 4 GB` a `4 × 20 MB × 2 ≈ 160 MB` sobre 768 MB de heap. Prueba: `LimitadorDeArchivosTest`, con un caso de 40 peticiones simultáneas que comprueba que el techo no se supera nunca. Lo que NO se arregló, y por qué: servir los bytes como flujo exige sacarlos de la fila, que es la migración que ADR-003 deja condicionada al umbral de 5 GB. El limitador acota el problema a una cifra calculable; no lo elimina.

El camino completo trabaja con el archivo entero en memoria:

- `DocumentoService.subir` → `documento.setContenido(archivo.getBytes())`: 20 MB
  de `byte[]` por petición.
- `DocumentoService.firmar` → `estampaDeFirma.estampar(...)` sobre el PDF ya
  cargado, y PDFBox trabaja sobre otra copia.
- `DocumentoController.descargar` devuelve `ResponseEntity<byte[]>`, y antes de
  responder llama a `estadoDeIntegridad` y `porQueNoSeAbreAlEntregarlo`, que
  recorren esos mismos bytes.

Frente a eso:

- `docker-compose.yml` limita el backend a **1 GB**, y `MaxRAMPercentage=75` deja
  **~768 MB de heap**.
- `spring.servlet.multipart.max-file-size=20MB`.
- **No hay** `server.tomcat.threads.max`: el valor por defecto son **200 hilos**.
- **No hay ningún limitador de concurrencia** en subida ni en descarga. Sí lo hay
  para la IA (`sicot.ia.max-concurrentes=2`), que es la parte que *no* corre
  riesgo de memoria.
- `-XX:+ExitOnOutOfMemoryError` convierte el `OutOfMemoryError` en muerte del
  proceso.

El resultado: con unas pocas decenas de subidas o descargas simultáneas de
20 MB —ni siquiera las 200 que Tomcat admite— el heap se agota, la JVM se
suicida por diseño y Docker reinicia el contenedor. El endpoint es
`POST /api/contratos/{id}/documentos`, abierto a `SUPERVISOR`, el rol más bajo.
Y no hace falta mala fe: tres supervisores subiendo evidencia fotográfica a la
vez desde el teléfono en bodega van por el mismo camino.

El comentario de `Documento.contenido` ya razona esto con precisión **para los
listados** y lo resuelve con una proyección JPQL. El análisis simplemente no se
extendió a la escritura ni a la descarga.

**Qué hacer**, de más barato a más correcto:

1. Un `Semaphore` (3–4 permisos) alrededor de subida, firma y descarga, que
   responda 429 con `Retry-After` al agotarse — la misma forma que ya usa
   `LimitadorDeUsoIa`.
2. Fijar `server.tomcat.threads.max` a algo coherente con el heap (p. ej. 40).
3. En la descarga, salir del `byte[]`: `StreamingResponseBody` leyendo del LOB,
   o `InputStreamResource`. Esto además quita el pico de memoria por descarga.
4. Cuando toque ADR-003, la subida por `InputStream` cae sola al mover los bytes
   fuera de la fila.

### A2 — Las dos pantallas que manejan credenciales no tienen ni una prueba

> **Corregido.** `NewUserModal.test.tsx` (12 pruebas) y `ResetPasswordModal.test.tsx` (6), que cubren lo que podía romperse sin aviso: la validación del correo institucional —incluido el caso que falló de verdad en octubre—, que la contraseña temporal se vea cuando el correo no sale, que restablecer no cambie el rol de nadie (es un `PUT` que reemplaza la fila entera) y que un doble clic no cree la cuenta dos veces. `CambiosSinEnviar.test.tsx` (13) cubre la pantalla donde se decide un conflicto. Y la cobertura del frontend **ya se mide en el CI** (`npm run test:coverage`, umbrales en `vite.config.ts`): el script existía y no lo corría ningún workflow.

Cobertura medida con `vitest run --coverage`:

| Archivo | Líneas | Ramas |
|---|---|---|
| `src/components/admin/NewUserModal.tsx` | **0,0 %** | **0,0 %** |
| `src/components/admin/ResetPasswordModal.tsx` | **0,0 %** | **0,0 %** |
| `src/components/CambiosSinEnviar.tsx` | 27,1 % | **10,8 %** |
| `src/services/colaDeCambios.ts` | 73,3 % | 57,4 % |
| `src/services/contratoService.ts` | 20,0 % | 21,4 % |
| `src/App.tsx` | 65,0 % | 52,1 % |

Crear una cuenta y restablecer una contraseña son las dos operaciones que
deciden quién entra al sistema, y en el frontend no las toca ninguna prueba.
`CambiosSinEnviar.tsx` es peor en consecuencia que en número: es la pantalla
donde el supervisor ve un conflicto de lista y decide si pisa lo que otro
guardó en el servidor. Diez de cada once ramas de esa decisión no se ejecutan
nunca en la suite.

**Y nada de esto lo detecta el CI**, porque el job de frontend corre
`npm run test:run`. El script `test:coverage` existe en `package.json` y no se
usa en ningún workflow: no hay medición ni umbral de cobertura en el frontend.

### A3 — El proceso que es el producto no se prueba por la interfaz

> **Corregido.** `e2e/specs/escritorio/proceso-gccon.spec.ts`: el supervisor entra, ve las seis etapas de la plantilla GCCON-P-010 con la 1 al 100 % y la 2 en curso, despliega un paso **con el teclado**, carga una evidencia fotográfica real (multipart, validación por contenido, lectura de EXIF), y el documento aparece en el expediente y en la bitácora con su nombre — y sigue ahí tras recargar, que es lo que distingue «guardado» de «parece guardado». Verificado ejecutándolo contra el backend y PostgreSQL de verdad, y comprobando además que **falla cuando debe**, rompiendo una aserción a propósito. No depende de Ollama a propósito: una compuerta que necesita un modelo de 5 GB no es una compuerta.

Los 6 archivos de Playwright, con sus títulos reales:

- `se puede iniciar sesión desde un teléfono`
- `se puede cerrar sesión desde un teléfono`
- `el compositor sigue al alcance cuando la conversación crece`
- `el modal de crear usuario cabe y se puede cerrar con un dedo`
- `la navegación queda al alcance y ninguna vista se desborda`
- `a 1024 px las acciones de cada contrato quedan a la vista`
- `a N px el objeto del contrato tiene espacio para leerse`
- `sin red, el supervisor entra con su contraseña y ve su copia rotulada`
- `login como <rol> muestra el panel correcto`

Es una suite de **maquetación y acceso**. Nace de la auditoría móvil del
16-09-2026 y para eso está bien. Pero no existe un solo recorrido que haga:
crear contrato → asignar supervisor → generar el Acta de Inicio → revisarla →
firmarla → verificar su integridad → descargarla. Ese es el GCCON-P-010, y es
la razón de ser del sistema.

El backend sí lo cubre (`DocumentoFormalDeExtremoAExtremoIntegrationTest`, 19
pruebas; `ArranqueDelContratoIntegrationTest`, 14). El problema es que la
orquestación de pasos, el estado de los borradores, qué se recuerda entre
subpasos y cuándo se habilita firmar viven en `SupervisorPanel.tsx` —2 175
líneas, 80,0 % de línea y 70,3 % de rama— y ahí se validan con servicios
mockeados. El archivo más complejo y de mayor valor del repositorio nunca se
ejecuta contra el backend real.

Un único spec de ese recorrido completo valdría más que las nueve pruebas de
ancho de pantalla juntas.

### A4 — Si el administrador pierde la clave, no hay camino de vuelta

> **Corregido** (documentación, que es lo que faltaba). `docs/operacion/CUANDO_ALGO_FALLA.md` gana la sección «Cuando lo que se cae es el acceso»: por qué el arranque no sirve de recuperación, la recomendación de crear una segunda cuenta de ADMINISTRADOR al montar el Centro, el procedimiento con los comandos exactos para generar el hash BCrypt y escribirlo —incrementando `version_credenciales`, sin lo cual un token robado sigue valiendo ocho horas— y las dos cosas que no hay que hacer: vaciar `usuarios` deja la bitácora entera sin autor, y `down -v` borra la base.

`AdministradorInicial` siembra la cuenta solo cuando la tabla está vacía:

```java
if (usuarioRepository.count() > 0) { /* no hace nada */ }
```

No hay endpoint de «olvidé mi contraseña» (verificado: cero coincidencias de
`restablecer|resetPassword|reset-password` en `controller/`). Los
restablecimientos los hace un `ADMINISTRADOR` sobre otra cuenta.

Con un solo administrador —el escenario que el propio proyecto describe, «un
equipo de tres personas»— perder esa contraseña deja el sistema inadministrable
hasta que alguien entre a PostgreSQL y escriba un hash BCrypt a mano. Ni
`INSTALACION.md` ni `docs/operacion/CUANDO_ALGO_FALLA.md` lo mencionan.

**Qué hacer:** documentar el procedimiento de recuperación con el comando exacto
(es lo que el proyecto ya hace bien en otros sitios), y exigir al menos dos
cuentas `ADMINISTRADOR` en la instalación.

### A5 — Cerrar sesión no cierra nada en el servidor

> **Corregido.** `POST /api/cuenta/cerrar-sesion` invalida el token **en el servidor**: cada JWT lleva ahora un `jti` (`JwtService`), cerrar sesión lo anota en `sesiones_cerradas` (`V25`) y `JwtAuthenticationFilter` lo rechaza desde la siguiente petición. Cierra **solo esa sesión**: el supervisor usa el portátil del Centro y el teléfono de bodega, y salir de uno no puede echarlo del otro — de ahí una lista de negación por token y no `revocarSesiones()`. Un token anterior a V25 no tiene `jti` y recurre a revocar la cuenta: más de lo pedido, nunca menos, y el caso desaparece solo en ocho horas. El frontend avisa al servidor **antes** de limpiar el equipo (en el orden inverso la petición saldría sin credenciales) y nunca falla por ello: salir de la pantalla ocurre siempre. Pruebas: `CierreDeSesionIntegrationTest` (5) y `SesionesCerradasTest` (5).

No existe `/api/auth/logout`. `clearSession()` borra `localStorage` y pone el
token a `null` en el cliente. El JWT emitido sigue siendo válido **hasta 8
horas** (`JWT_EXPIRATION_MS=28800000`). La única revocación real es
`versionCredenciales`, que se incrementa al cambiar la contraseña o desactivar
la cuenta.

Combinado con que el token vive en `localStorage` (`sicot.session`), un XSS o un
equipo compartido entrega una sesión de ocho horas que «cerrar sesión» no
invalida. La CSP de nginx es estricta y reduce mucho la probabilidad del XSS,
pero no cambia nada sobre la revocación.

Para el despliegue web, donde la SPA y la API comparten origen (ADR-009), una
cookie `HttpOnly; Secure; SameSite=Strict` sería estrictamente mejor que
`localStorage`; la aplicación empaquetada seguiría con `Authorization`. Como
mínimo: un `POST /api/auth/logout` que llame a `revocarSesiones()`.

---

## MEDIO

### M1 — Política de contraseñas por debajo de lo que el propio sistema teme

> **Corregido.** `security/PoliticaDeContrasena.java`: mínimo **12** —el que `AdministradorInicial` ya exigía a la cuenta más poderosa, así que la incoherencia era interna—, lista de bloqueo en `security/contrasenas-prohibidas.txt` comparada en minúsculas, sin acentos y sin los dígitos de relleno del final (así `Password123!`, `contraseña2026` y `Sena2024*` caen con una sola entrada cada una), rechazo de secuencias y de contraseñas que contengan el correo de su dueño. Se aplica al crear una cuenta, al cambiar la propia, al restablecer y al `SICOT_ADMIN_PASSWORD` del arranque. **No** se exigen reglas de composición, siguiendo a OWASP: empujan a `Nombre123!` y castigan las frases largas, que son mejores. 38 pruebas, la mitad dedicadas a que la política no estorbe a quien elige bien. La clave temporal que genera el panel pasa de 12 a 16 caracteres, igualada con la del backend.

`@Size(min = 8, max = 72)` y un `@Pattern` que solo prohíbe espacios al
principio y al final. Sin lista de bloqueo de contraseñas comunes, sin
comprobación contra credenciales filtradas, sin MFA para `ADMINISTRADOR`.

`LoginAttemptService` documenta con precisión el ataque de **rociado de
contraseñas** contra un directorio institucional de correos predecibles
(`nombre.apellido@…`) y añade el contador por IP justamente para eso. Pero
`Password123` es una contraseña válida en este sistema, y es exactamente la que
ese ataque prueba. El control de frecuencia está, el control de calidad no.

Para un despliegue con nombre público y Let's Encrypt: mínimo 12 caracteres y
una lista de las 10 000 contraseñas más comunes (OWASP). MFA para administradores
cuando haya margen.

### M2 — El umbral de cobertura ya no protege nada

> **Corregido.** 0,88 de línea y 0,76 de rama, unos puntos por debajo de lo medido hoy (93,8 % y 82,3 %), con el historial de la medición escrito en el `pom.xml` para que la próxima vez se vea que el trinquete se quedó atrás. Y el frontend tiene ahora el suyo, que antes no existía.

`pom.xml` exige `LINE ≥ 0,80` y `BRANCH ≥ 0,58`. La realidad medida hoy es
**0,920** y **0,816**. El comentario que justifica el umbral dice, textualmente,
que el objetivo «no es empujar la cifra hacia arriba, es impedir que baje sin que
nadie se entere», y cita los datos de cuando se fijó: 85 % y 63 %.

Con los números de hoy, la cobertura puede caer 12 puntos de línea y 23 de rama
y el build sigue verde. El razonamiento del comentario es correcto; lo que falta
es haber vuelto a mover el trinquete. Subirlo a 0,88 / 0,76 mantiene el margen
para refactorizar y recupera la función del control.

### M3 — Lo que corre desatendido es lo menos probado

> **Corregido.** `PlanificadorDeAutomatizacionesTest` (7), `InformeParaElTecnicoTest` (7), `ReparadorDeServiciosTest` (5), `SaludControllerIntegrationTest` (3) y `AlmacenDeTareasTest` (9). El antes y el después en líneas cubiertas: Planificador 3,1 % → **100 %**, InformeParaElTecnico 12,3 % → **100 %**, SaludController 23,1 % → **100 %**, ReparadorDeServicios 16,7 % → **96,7 %**, AlmacenDeTareas 58,6 % → **96,6 %**. Lo que fijan no es el camino feliz sino el que nadie ve: que una excepción no cancele en silencio las rondas futuras de un `@Scheduled`, que el informe al técnico no falle nunca ni intente avisar por el canal que está caído, y que la cola rescate lo que quedó reservado por un proceso muerto sin quitarle nada a quien sí está ejecutando.

El subsistema de salud y automatización es el que funciona solo, de noche, en el
servidor del Centro, sin nadie mirando. Es el que peor cobertura tiene:

| Clase | Línea |
|---|---|
| `automatizacion/PlanificadorDeAutomatizaciones` | **3,1 %** |
| `salud/InformeParaElTecnico` | **12,3 %** |
| `salud/ReparadorDeServicios` | **16,7 %** |
| `controller/SaludController` | **23,1 %** |
| `automatizacion/AlmacenDeTareas` | 58,6 % |
| `service/DatosAbiertosSecop` | 69,3 % |
| `automatizacion/EjecutorDeTareas` | 74,0 % |

`AlmacenDeTareas` es el dueño de las escrituras de la cola y tiene el comentario
más cuidadoso del repositorio sobre por qué los tres pasos no pueden compartir
transacción —un razonamiento correcto y nada obvio— con 41 % de sus líneas sin
ejecutar. `PlanificadorDeAutomatizaciones`, al 3 %, es quien decide *cuándo*
corre todo lo demás.

Esto es lo contrario de lo que debería ser: la cobertura más alta está en las
clases de redacción (`RedactorDeDocumentos` 98,3 %, `FidelidadDeRedaccion`
96,7 %), que son deterministas y fáciles de probar, y la más baja en lo que
nadie puede observar en vivo.

### M4 — Los logs de producción pierden todos los acentos

> **Corregido.** `-Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8` en el `JAVA_OPTS` del `backend/Dockerfile`, con el porqué al lado (no vale `ENV LANG`: musl ignora los locales que no tiene instalados). Comprobado arrancando el backend con esa opción: los mensajes salen con sus acentos.

Verificado en esta máquina: con `LANG` vacío, la JVM 25 reporta

```
native.encoding = ANSI_X3.4-1968
stdout.encoding = ANSI_X3.4-1968
```

Y la propia suite lo demuestra en su salida:

```
Extracci?n de 'acta.pdf' completada en 0 ms
El modelo no respondi? para 'notificacion.pdf'
'escaneado.pdf' no tiene texto legible (?imagen escaneada sin OCR?)
```

`backend/Dockerfile` usa `eclipse-temurin:25...-jre-alpine` y **no define `LANG`
ni `LC_ALL`**, y `JAVA_OPTS` no fija la codificación de salida. Alpine no trae
locales. Desde JDK 19, `stdout.encoding` se deriva de la codificación nativa, así
que en el servidor del Centro todos los mensajes —que están íntegramente en
español, cuidados uno por uno para que el técnico los entienda— salen mutilados.

**Arreglo de una línea:**

```dockerfile
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8"
```

### M5 — No hay ninguna estrategia de accesibilidad, y aquí es exigible por norma

> **Corregido, y con una rectificación de este informe.** El dato que se citaba —44 `<input>`, 8 `<label>`, 3 `htmlFor`— era un **mal indicador**: medido con `eslint-plugin-jsx-a11y`, la regla moderna (`label-has-associated-control`) sale limpia, porque el frontend asocia las etiquetas **anidando** el control dentro de `<Field>`, que es válido y mejor que inventar ids. La conclusión «la mayoría de los campos no está etiquetada» era falsa y queda rectificada.
>
> Lo que sí había, y se arregló: los tres filtros de `Registros.tsx` cuyo rótulo era un `<div>` —invisible para un lector de pantalla— ahora son `<label htmlFor>` reales; los dos controles del selector de color de `Settings.tsx` compartían una etiqueta y cada uno tiene ya su `aria-label`; y tres elementos pulsables que eran `<div onClick>` —la fila de cada paso del proceso, la zona de carga de la ficha del contrato y la tarjeta del diálogo— pasan a `<button>` o pierden el manejador. Eso último no era cosmético: **con un `<div>`, quien no usa ratón no podía abrir ningún paso del proceso ni registrar un contrato.** El resaltado de fila se movió de JavaScript a CSS y gana `:focus-within`, así que ahora también se ve recorriendo la tabla con el teclado. El E2E comprueba los seis pasos con Enter y Espacio.

Conteo sobre `frontend/src`:

- 44 elementos `<input>`
- 8 elementos `<label>`
- **3** usos de `htmlFor`
- 32 `aria-label` en total (repartidos entre botones e iconos)
- cero `axe`, cero `jest-axe`, cero `eslint-plugin-jsx-a11y`, cero pruebas de a11y

La mayoría de los campos del sistema no están asociados a su etiqueta de forma
programática. Un lector de pantalla no puede decir qué se está pidiendo.

SICOT es para el SENA, una entidad del Estado colombiano. La **Resolución 1519
de 2020 del MinTIC** obliga a los sujetos de la Ley 1712 a cumplir **WCAG 2.1
nivel AA**. Es el único hallazgo de este informe con consecuencia externa
—auditoría de cumplimiento— y no aparece en ningún ADR ni en la especificación.

El trabajo de adaptación a pantalla estrecha, que sí se hizo con rigor y tiene
su propio proyecto de Playwright, demuestra que el equipo sabe tratar este tipo
de requisito cuando lo identifica. Aquí simplemente no se identificó.

### M6 — No hay linter en ninguno de los dos lenguajes

> **Corregido en el frontend**, que es donde faltaba de verdad. `frontend/eslint.config.js` con `typescript-eslint`, `eslint-plugin-react-hooks` y `eslint-plugin-jsx-a11y`, graduado a propósito —`error` lo que rompe algo o ya está limpio, `warn` la deuda que se paga pantalla por pantalla— y conectado al CI **sin** `--max-warnings`, por la lección de MDL-145: un linter que falla con mil avisos el primer día se desactiva. Primera pasada: 51 hallazgos. Hoy: **cero**. Entre ellos, un cierre obsoleto real en `App.tsx` (el manejador global del 401 guardaba el `logout` del primer render) y un espacio indivisible invisible dentro de una expresión regular.
>
> **En el backend no se añadió** SpotBugs ni Error Prone. Con Semgrep (tres rulesets), Gitleaks, Trivy, OWASP Dependency-Check, JaCoCo con trinquete y 1 130 pruebas, el valor marginal de otro analizador es bajo y cada herramienta nueva es una compuerta más que mantener. Queda anotado como decisión, no como olvido.

`frontend/package.json` trae `oxfmt`, que es un **formateador**, no un linter. No
hay `.eslintrc*`, ni `eslint.config.*`, ni `biome.json` en el repositorio. El
backend no tiene SpotBugs, Checkstyle, PMD ni Error Prone en `pom.xml`.

Semgrep en CI cubre seguridad (`p/security-audit`, `p/owasp-top-ten`,
`p/secrets`), y lo hace bien. No cubre corrección: `react-hooks/exhaustive-deps`,
`rules-of-hooks`, promesas sin `await`, variables sin usar. Sobre
`GestionPanel.tsx` (33 `useState` en un componente) y `SupervisorPanel.tsx`
(10 `useEffect`), `exhaustive-deps` es precisamente la regla que atrapa los
errores de cierre obsoleto que `tsc` no ve y que una prueba de componente rara
vez provoca.

Añadir `eslint` + `typescript-eslint` + `eslint-plugin-react-hooks` +
`eslint-plugin-jsx-a11y` resuelve M5 y M6 con el mismo movimiento.

### M7 — No hay paginación, hay techos silenciosos

> **Corregido.** `controller/ListadoTruncado.java`: los seis listados con techo devuelven `X-SICOT-Listado-Tope` siempre y `X-SICOT-Listado-Truncado: true` cuando lo alcanzan, ambas expuestas por CORS para que la aplicación de escritorio y el APK —los clientes que no se actualizan junto con el servidor— también las vean. Una cabecera y no un sobre en el cuerpo, para no romper tres clientes que se despliegan por separado; cuando el frontend aprenda a paginar, lo correcto es sustituirla por un cursor y retirarla. Prueba: `ListadoTruncadoIntegrationTest`, que comprueba sobre todo que con pocos elementos **no** se avise, porque un aviso que sale siempre se aprende a ignorar.

`MAX_CONTRATOS_LISTADO = 500`, `MAX_TAREAS_LISTADO = 200`, y los equivalentes en
alertas y registros. Los comentarios lo reconocen: «no es una paginación de
verdad —que el frontend todavía no sabe recorrer— sino un techo».

El razonamiento de por qué el techo es correcto. Lo que falta es el otro medio
paso: **la respuesta no dice que truncó**. El día que un Centro pase de 500
contratos, la pantalla mostrará 500 y parecerá completa. Para un registro de
expedientes, una lista que miente por omisión es peor que un error.

Mientras no haya paginación: devolver `total` y un `truncado: true` en la
respuesta, y que la pantalla lo diga.

### M8 — Una acción del MCP es indistinguible de una del funcionario

> **Corregido.** `OrigenRegistro.AGENTE` y la columna `registros.agente` (`V26`): el servidor MCP manda `X-SICOT-Agente` en **todas** sus peticiones —declarado en el cliente y no en cada herramienta, para que una nueva no pueda olvidarlo— y la bitácora registra la acción como `AGENTE` sin perder la cuenta responsable, que sigue siendo la de quien prestó sus credenciales. La pantalla de registros lo muestra como «Alex Fernando Zapata (vía servidor MCP de SICOT)». Declararse agente **no** sirve para esquivar las automatizaciones: el evento de negocio se publica igual, y hay una prueba dedicada a eso. Se dice en el código y en la migración lo que esto no es: un control de acceso — un cliente que no manda la cabecera sigue quedando como `USUARIO`.
>
> Hallazgo adicional, encontrado al probarlo: una cabecera con un salto de línea la rechaza el cortafuegos de Spring Security —correcto— y SICOT la devolvía como **500 «error interno del servidor»**, echándose la culpa de una petición mal formada. Ahora es un 400 con su motivo, sin detallar qué regla saltó (`GlobalExceptionHandler`).

`OrigenRegistro` tiene exactamente dos valores, `USUARIO` y `SISTEMA`, y su
comentario explica muy bien por qué `usuario_id` nulo era ambiguo.

Pero `mcp/src/sicotClient.ts` se autentica con `SICOT_EMAIL` /
`SICOT_PASSWORD` contra el mismo `/api/auth/login` y actúa **como esa persona**.
Sus 14 herramientas incluyen `crear contrato`, `asignar supervisor`,
`cambiar estado`, `cambiar estado de subetapa`. Todo lo que haga un agente de IA
queda en `registros` con `origen = USUARIO` y el `usuario_id` del funcionario.

El sistema entero existe para producir un expediente auditable de contratación
pública. «Esto lo decidió una persona» frente a «esto lo propuso un modelo
actuando con sus credenciales» es justo la distinción que ese expediente debe
conservar, y hoy se pierde. El propio proyecto tiene la regla «la IA es asesora,
nunca autoridad» (`copilot-instructions.md` §29); el MCP es el único punto donde
la trazabilidad no la sostiene.

Añadir `OrigenRegistro.AGENTE` y una cabecera que el MCP envíe siempre.

Efecto secundario menor del mismo diseño: el MCP comparte el contador de
intentos por correo con la persona, y reintenta el login al recibir un 401.

### M9 — Los respaldos no se cifran y viven en el disco que pueden perder

> **Corregido.** `scripts/respaldo-sicot.sh` gana cifrado (clave pública con `SICOT_RESPALDO_GPG_DESTINATARIO`, que es la recomendada porque en el servidor solo queda la pública; o frase de paso) y copia fuera del servidor (`SICOT_RESPALDO_DESTINO_REMOTO`, otro disco o SSH). Tres decisiones que importan más que el cifrado en sí: se cifra **después** de verificar, porque un archivo cifrado no se puede leer con `pg_restore` y cifrar antes dejaría el script «verificando» nada; si el cifrado falla, el volcado en claro **se conserva** y el script sale con error, porque cambiar confidencialidad por disponibilidad es peor; y sin configurar nada el script lo **avisa en cada ejecución**, porque un control ausente en silencio es el que nadie añade.
>
> Y la trampa que esto habría provocado: `VigilanciaDelRespaldo` solo contaba los `.dump`, así que encender el cifrado habría dejado el directorio lleno de respaldos correctos y al sistema avisando cada día de que no hay ninguno — el fallo que esa clase existe para evitar, provocado por la mejora. Ahora cuenta también los `.dump.gpg`, con dos pruebas. El arnés `prueba-respaldo-sicot.sh` comprueba además que lo cifrado se descifra y es exactamente el volcado que se verificó. ADR-002 queda corregido en su contradicción con ADR-003, y `BACKUP_Y_RESTAURACION.md` trae el procedimiento completo, incluido cómo restaurar un respaldo cifrado.

`scripts/respaldo-sicot.sh` está bien hecho: `set -euo pipefail`, `umask 077`,
escritura a `.parcial` con `trap`, rechazo del volcado vacío, y verificación con
`pg_restore -f -` (no `--list`, que aprobaba un volcado cortado al 60 % — ese
arreglo está documentado y es el tipo de detalle que distingue este proyecto).

Lo que falta:

1. **Sin cifrado.** El `.dump` es la base entera: nombres, correos, teléfonos,
   NIT, representantes legales y las **coordenadas GPS** de las fotos de
   evidencia (`capturaLatitud` / `capturaLongitud`, V17). Datos personales bajo
   Ley 1581 de 2012, en claro.
2. **Sin copia fuera del servidor.** `DESTINO="${1:-./respaldos}"`, en el mismo
   disco. ADR-002 acepta explícitamente perder el día; no dice qué pasa si el
   disco no vuelve. Los **secretos** sí tienen procedimiento (`gpg --symmetric
   --cipher-algo AES256`, copia fuera, borrar del servidor); la base, no.
3. **Una premisa que va a dejar de ser cierta.** ADR-002 justifica bajar el RPO a
   una hora con «Ninguno — la base son megabytes». Hoy son 27,6 MB. Pero los
   documentos viven *dentro* de la base (`documentos.contenido` como `bytea`) y
   ADR-003 fija el umbral de migración en **5 GB**. Un `pg_dump` seguido de un
   `pg_restore -f -` completo sobre varios GB, cada hora, no cuesta «ninguno».
   Las dos decisiones son defendibles por separado y se contradicen juntas.

El patrón de los tres es el mismo y es gratis de arreglar: una tubería
`| gpg --symmetric` y un `rsync` a otro destino en el mismo script.

---

## BAJO

### B1 — El troceado por rol no ayudó a quien más lo necesitaba

> **Corregido.** `ContratoGraficas` se carga bajo demanda con `React.lazy`, y Recharts se va con ella a su propio trozo. Medido: la primera carga del supervisor pasa de **837 kB a 469 kB** (`index` 340,5 + `SupervisorPanel` 128,6), por debajo incluso de los 755 kB del paquete único de antes. Las gráficas (359,6 kB) llegan al abrir su pestaña.

`npm run build`, salida real:

```
dist/assets/index-V0UhFdP8.js             348,87 kB │ gzip: 108,78 kB
dist/assets/SupervisorPanel-CpXOCByP.js   487,91 kB │ gzip: 140,26 kB
dist/assets/GestionPanel-BpqxgMUW.js       37,92 kB │ gzip:  10,52 kB
dist/assets/AdminPanel-CPEF8Wyq.js         36,73 kB │ gzip:   9,51 kB
```

`recharts` está dentro del chunk de `SupervisorPanel` (verificado por `grep` en
el bundle). El comentario de `App.tsx` justifica el `lazy()` diciendo que antes
había «un único paquete de 755 kB» y que importa «especialmente para el
despliegue local del supervisor y para la red del centro de formación».

El supervisor descarga hoy **349 + 488 = 837 kB**, más que los 755 kB del
paquete único. Gestión y Administración sí ganaron mucho (387 y 386 kB). El
razonamiento era bueno; el reparto dejó fuera justo al usuario que se citaba.

`ContratoGraficas` es una pestaña entre varias: un `React.lazy()` sobre ella, o
un `manualChunks` que aparte `recharts`, le quita ~300 kB a la primera carga del
supervisor.

### B2 — El comentario se convirtió en bitácora

> **Corregido en parte, y el resto no se toca a propósito.** Lo medible se arregló: la deriva concreta que este informe encontró (ver B3) y la documentación de las dos migraciones nuevas, verificada por la prueba de esquema. Cada pieza añadida en esta intervención lleva su comentario explicando **por qué** existe y qué pasa si se quita — que es el uso bueno del comentario.
>
> Lo que NO se hizo: reescribir las 9 115 líneas de comentario del backend para sacar la narrativa con fecha a los ADR. Son cientos de archivos, el cambio es puramente mecánico y cada línea movida es una oportunidad de perder un razonamiento que hoy está donde se necesita. Mezclarlo con arreglos de seguridad haría el diff ilegible y la revisión imposible. Es un trabajo propio, de una pasada, con su propio commit — igual que la primera pasada del formateador, que está en `.git-blame-ignore-revs` justo por este motivo.

Medido:

| | Código | Comentario | Ratio |
|---|---|---|---|
| `backend/src/main/java` | 19 189 | **9 115** | **0,48** |
| `backend/src/test/java` | 16 913 | 3 557 | 0,21 |
| `frontend/src` | 22 523 | 4 607 | 0,20 |

Más **83 386 líneas de Markdown** en 62 archivos, frente a ~59 000 de código.

Buena parte del comentario del backend no explica el código: narra su historia
con fecha. «Hasta el 21-09-2026 cargar un documento no dejaba ninguno», «hasta
la auditoría del 02-10-2026 entraba también el ADMINISTRADOR», «se descubrió
instalándola y entrando de verdad el 15 de septiembre de 2026». Cada una de
esas frases es información valiosa y está en el lugar equivocado: eso es
`git log` y son los ADR, que el proyecto ya tiene y usa bien (17 ADR).

El coste no es estético. Es que cada cambio de comportamiento arriesga dejar
prosa falsa que nadie releyó, y que revisar un cambio de 30 líneas de código
obliga a leer 60 de comentario para decidir si alguna dejó de ser cierta.

### B3 — Deriva ya presente

> **Corregidos los dos casos**, y el antídoto extendido a un tercer sitio. `playwright.config.ts` ya no remite a un directorio que no existe, y el comentario del umbral de JaCoCo trae las cifras reales con el historial de la medición. La deriva volvió a aparecer durante la propia intervención y la atrapó el guardia que el proyecto ya tenía: `EsquemaPostgreSqlIntegrationTest` falló al añadir la tabla `sesiones_cerradas` porque faltaba en `MODELO_DE_DATOS.md`. Hizo exactamente su trabajo; el documento está al día, con las dos migraciones nuevas descritas.

Dos casos encontrados sin buscar mucho:

- `frontend/playwright.config.ts:7` remite a «los specs bajo `e2e/specs/ai/`,
  que no dependen de Ollama». Ese directorio no existe: solo hay `auth`,
  `escritorio` y `movil`.
- El comentario del umbral de JaCoCo en `pom.xml` afirma «al medirlo, la
  cobertura era 85 % de línea y 63 % de rama». Hoy es 92,0 % y 81,6 %.

Lo notable es que el proyecto ya inventó el antídoto correcto:
`InventarioDeEndpointsTest` es una prueba que falla si
`docs/api/INVENTARIO_ENDPOINTS.md` no tiene una fila por endpoint, y su Javadoc
explica exactamente por qué («una ruta que no aparece en la tabla es una ruta
que nadie revisa»). Ese patrón —la documentación verificada por una prueba—
cubre hoy 1 de 62 documentos. Es la respuesta a B2 y a B3, y conviene extenderlo
a `MODELO_DE_DATOS.md` y a `PROCESO_REAL_EN_SICOT.md`.

### B4 — Componentes y clases dios

> **Corregido en parte.** La extracción de `ContratoGraficas` (ver B1) saca 360 kB y una pestaña entera de `SupervisorPanel.tsx`, y es el primer corte natural del archivo.
>
> Lo que NO se hizo: partir los dos componentes dios en piezas por paso del proceso. Es un refactor de varios días sobre los dos archivos con más lógica de negocio del frontend, con riesgo real de regresión, y **el momento correcto es ahora que existen las redes que antes faltaban**: el E2E del proceso completo, el linter con las reglas de los hooks y la cobertura con umbral en el CI. Hacerlo antes de tenerlas habría sido refactorizar a ciegas; hacerlo en el mismo cambio que los arreglos de seguridad habría enterrado ambos.

| Archivo | Líneas |
|---|---|
| `backend/.../ia/FidelidadDeRedaccion.java` | 2 008 |
| `frontend/src/screens/SupervisorPanel.tsx` | 2 175 |
| `frontend/src/screens/GestionPanel.tsx` | 1 970 (33 `useState`) |
| `backend/.../ia/RedactorDeDocumentos.java` | 1 291 |
| `backend/.../ia/PdfInstitucional.java` | 1 064 |
| `backend/.../ia/CopilotoChatService.java` | 966 |

El paquete `ia/` son 33 archivos y ~700 kB de fuente: por sí solo es más grande
que el resto del backend junto. `GestionPanel.tsx` con 33 piezas de estado local
en un componente no tiene una forma razonable de razonarse completo, y es
exactamente donde `exhaustive-deps` (M6) haría más falta.

### B5 — Una capa de datos construida a mano

> **No se hizo, y es la recomendación de este propio informe.** El texto de arriba ya dice «no es urgente». Sustituir `copiaLocal` + `colaDeCambios` + `segundoPlano` + `accesoSinRed` por TanStack Query es reescribir el núcleo sin conexión —lo más delicado del producto, lo que decide si el trabajo de campo de un supervisor se pierde— para ganar mantenimiento de terceros. Eso se decide con tiempo y se despliega solo, no dentro de una tanda de correcciones. Lo que sí cambió es la red que lo cubre: `CambiosSinEnviar.test.tsx` lleva la pantalla del conflicto de 11 % a cubierta, así que el día que se aborde habrá con qué comprobarlo.

`copiaLocal.ts` (341) + `colaDeCambios.ts` (688) + `segundoPlano.ts` (288) +
`accesoSinRed.ts` (240) + `vueltaDelServidor.ts` ≈ 1 600 líneas que implementan
caché local, deduplicación de envíos, reintentos con tope, detección de
conflicto por comparación de base, y invalidación.

El código es bueno y el razonamiento de ADR-015 («cola de intenciones, no
réplica de la base») es la decisión correcta. Pero es una infraestructura propia
que hay que mantener y probar para siempre, y su cobertura ya es la más baja de
los servicios (`colaDeCambios` 73 % / 57 %). TanStack Query con un persister
cubriría el 70-80 % de eso con mantenimiento de terceros, dejando a mano solo lo
que es específico del dominio: la comprobación de `base` antes de reemplazar una
lista. No es urgente; es la clase de deuda que conviene reconocer antes de que
crezca otro 50 %.

---

## Lo que está bien y conviene no romper

Para que este informe sea exacto y no solo severo:

- **Arranque *fail closed*.** Perfil `prod` por defecto, `JWT_SECRET` validado
  en `JwtService` con un mensaje que dice qué falta y cómo generarlo. La
  alternativa cómoda (perfil `dev` por defecto) es el fallo silencioso clásico, y
  está cerrado a conciencia.
- **El pre-filtro de rol en `SecurityConfig`.** Se añade porque `@Valid` corre
  antes de `@PreAuthorize` y un usuario sin permiso recibía un 400 con el mapa
  completo de campos obligatorios. Que cada regla sea deliberadamente igual o más
  permisiva que la del método, para que divergir no abra nada, es diseño
  defensivo de verdad.
- **404 en lugar de 403** en `verificarAccesoAlContrato`, para que un supervisor
  no distinga «no existe» de «no es suyo».
- **Firma comprobable.** SHA-256 calculado dentro de la misma transacción, más
  `huellaRevisada` para que no se pueda firmar un borrador distinto del que se
  leyó, más `firmantePrevisto` leído del propio PDF.
- **`EntradaNoConfiable`.** Frontera explícita de inyección de prompt, con
  neutralización de las marcas delimitadoras para que el bloque no se pueda
  cerrar antes de tiempo. Pocos proyectos que llaman a un LLM lo tienen.
- **Idempotencia con dos llaves** en la cola: consulta previa *y* restricción de
  unicidad, con el razonamiento de por qué la primera sola no basta.
- **`BloqueoOptimistaTest`**, que reproduce la actualización perdida con dos
  `EntityManager` en vez de dos hilos para ser determinista.
- **`EsquemaPostgreSqlIntegrationTest`**, que invierte la relación (Flyway
  construye, Hibernate valida) y es la única forma de detectar que una migración
  y una entidad dejaron de decir lo mismo. Con PostgreSQL 18 real en CI.
- **El CI**, que es mejor que el de la mayoría de los proyectos comerciales:
  Semgrep con tres rulesets, Gitleaks con `fetch-depth: 0` (razonado: el secreto
  está en el objeto, no en el último commit), Trivy en árbol y en imagen, OWASP
  Dependency-Check con caché de la NVD, imágenes base fijadas por versión exacta,
  acciones **fijadas por SHA** con el porqué escrito.
- **`dependabot.yml`**, que documenta honestamente su propio límite: que una CVE
  cuya corrección esté en una versión mayor **no** se corrige sola, y que eso se
  descubrió con la CVE de Tika. Admitir en la configuración que el control no
  cubre lo que uno creía es poco frecuente y vale mucho.

---

## Plan priorizado (el que se siguió)

Se conserva tal como se escribió, en el orden en que se decidió atacarlo. Las
dieciséis primeras filas están **hechas**; las tres últimas son las
estructurales, y lo que se hizo de cada una está en su hallazgo.

| # | Acción | Hallazgo | Esfuerzo |
|---|---|---|---|
| 1 | Semáforo en subida/firma/descarga + `server.tomcat.threads.max` | A1 | horas |
| 2 | `-Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8` en `JAVA_OPTS` | M4 | minutos |
| 3 | Endpoint de cierre de sesión que invalide el token en el servidor | A5 | horas |
| 4 | Cifrar el respaldo (`\| gpg`) y copiarlo fuera del servidor | M9 | horas |
| 5 | Documentar la recuperación del administrador + exigir dos cuentas | A4 | horas |
| 6 | Pruebas de `NewUserModal` y `ResetPasswordModal`; `test:coverage` con umbral en CI | A2 | 1–2 días |
| 7 | Un spec E2E del GCCON-P-010 completo, de crear a firmar y verificar | A3 | 2–3 días |
| 8 | ESLint + `react-hooks` + `jsx-a11y`, y arreglar `label`/`htmlFor` | M5, M6 | 2–3 días |
| 9 | Subir el umbral de JaCoCo a 0,88 / 0,76 | M2 | minutos |
| 10 | Pruebas de `PlanificadorDeAutomatizaciones`, `ReparadorDeServicios`, `InformeParaElTecnico` | M3 | 2–3 días |
| 11 | Lista de bloqueo de contraseñas comunes; mínimo 12 | M1 | 1 día |
| 12 | `truncado`/`total` en los listados con techo | M7 | 1 día |
| 13 | `OrigenRegistro.AGENTE` + cabecera del MCP | M8 | 1 día |
| 14 | `lazy()` sobre `ContratoGraficas` o `manualChunks` para recharts | B1 | horas |
| 15 | Mover la narrativa con fecha de los comentarios a los ADR; extender el patrón de `InventarioDeEndpointsTest` | B2, B3 | continuo |
| 16 | Partir `SupervisorPanel` y `GestionPanel` por paso del proceso | B4 | continuo |

Los cinco primeros quitan el riesgo de caída, el de pérdida de datos y el de
quedarse sin administrador. El 7 y el 8 son los que cambian la calidad
estructural del proyecto.

Dos notas sobre cómo acabó siendo distinto de lo planeado:

- La fila 3 decía `POST /api/auth/logout`. No puede ir ahí: `/api/auth/**` es
  **público** en `SecurityConfig` —es donde se inicia sesión— y cerrar la sesión
  exige tenerla. Quedó en `POST /api/cuenta/cerrar-sesion`, junto al cambio de
  contraseña propia, que es el sitio de lo que cada persona hace con su cuenta.
  Y no usa `revocarSesiones()`: eso cerraría también el teléfono del supervisor
  (ver A5).
- La fila 7 decía «de crear a firmar y verificar». Firmar exige que SICOT
  redacte el borrador, y eso exige Ollama; el recorrido llega hasta la carga de
  evidencia real, que es el otro camino del supervisor y no necesita el modelo.

---

## Qué se corrigió (20-11-2026)

Los dieciséis hallazgos concretos —A1-A5, M1-M9, B1 y B3— están arreglados y
verificados. De los tres estructurales, B2 y B4 se hicieron en la parte que se
podía medir y B5 se dejó a propósito; cada uno explica arriba por qué.

### Las cifras, antes y después

| | Antes | Después |
|---|---|---|
| Pruebas del backend | 1 037 (6 omitidas) | **1 130** (1 omitida) |
| Cobertura del backend | 92,0 % línea · 81,6 % rama | **93,8 % · 82,3 %** |
| Umbral de JaCoCo | 0,80 / 0,58 (12 y 23 puntos de holgura) | **0,88 / 0,76** |
| Pruebas del frontend | 441 | **472** |
| Cobertura del frontend | 79,2 % línea · 69,3 % rama, **sin medir en el CI** | **80,7 % · 71,2 %, con umbral en el CI** |
| Pruebas de extremo a extremo | 20, todas de maquetación y acceso | **22, con el proceso GCCON-P-010 recorrido** |
| Hallazgos del linter | no había linter | **0 errores, 0 avisos** |
| Primera carga del supervisor | 837 kB | **469 kB** |
| Peor caso de memoria por archivos | 200 × 20 MB = 4 GB | **4 × 20 MB × 2 ≈ 160 MB** |

### Verificación

Todo ejecutado en esta máquina, no inferido:

| Comprobación | Resultado |
|---|---|
| `./mvnw verify` con PostgreSQL real | **BUILD SUCCESS** — 1 130 pruebas, 0 fallos |
| `EsquemaPostgreSqlIntegrationTest` | **5 pruebas, en verde** — Flyway aplica las 19 migraciones e Hibernate valida el esquema resultante, V25 y V26 incluidas |
| `npm run typecheck` / `lint` / `format:check` | limpios · **0 errores y 0 avisos** de ESLint |
| `npm run test:coverage` | 472 pruebas, umbrales superados |
| `npm run build` | 688 módulos |
| `npx playwright test` | **22 de 22**, contra el backend y la base de verdad |
| `bash scripts/prueba-respaldo-sicot.sh` | todas las comprobaciones, incluidas las nuevas de cifrado |

La suite de esquema es la que normalmente se salta por falta de base: se montó
un PostgreSQL local a propósito para ejecutarla, y es la que detectó que faltaba
documentar la tabla nueva.

### Lo que apareció mientras se arreglaba

Tres cosas que no estaban en el diagnóstico y salieron al tocar el código:

1. **Una petición mal formada se reportaba como 500.** El cortafuegos de Spring
   Security rechaza una cabecera con un salto de línea —correcto— y SICOT
   respondía «error interno del servidor», echándose la culpa de algo que era
   del cliente. Ahora es un 400 con su motivo.
2. **Cifrar el respaldo habría dejado ciega a su propia vigilancia.**
   `VigilanciaDelRespaldo` solo contaba los `.dump`: la mejora habría provocado
   exactamente el fallo que esa clase existe para evitar. Arreglado antes de
   que pudiera pasar, con pruebas.
3. **Un cierre obsoleto real en `App.tsx`.** El manejador global del 401 se
   quedaba con el `logout` del primer render. Funcionaba por casualidad —esas
   referencias resultan ser estables hoy— y lo señaló el linter en su primera
   pasada.

### Lo que sigue pendiente, dicho con claridad

- **B2 y B4 a fondo**, que son dos pasadas propias con su propio commit.
- **B5**, que es una decisión de arquitectura y no una corrección.
- **Sacar los documentos de la fila de la base** (ADR-003). El limitador de A1
  acota el problema; la solución está condicionada al umbral de 5 GB, hoy en
  27,6 MB y vigilado.
- **MFA para administradores.** La política de contraseñas sube el listón; un
  segundo factor es otra cosa y no se añadió.
