# Auditoría técnica de SICOT — 8 de septiembre de 2026

Revisión completa del repositorio en el estado de la rama
`feat/motor-de-automatizaciones`, inmediatamente después de incorporar el motor
de automatizaciones (ADR-008).

Cada hallazgo lleva la evidencia que lo sostiene: archivo, línea o comando
ejecutado. Lo que no se pudo comprobar se dice como tal.

---

## 1. Qué se midió

| Área | Medida | Fuente |
| --- | --- | --- |
| Backend (producción) | 162 archivos · 11 047 líneas | `find backend/src/main/java -name '*.java'` |
| Backend (pruebas) | 43 archivos · 7 646 líneas | `find backend/src/test/java -name '*.java'` |
| Frontend | 51 archivos · 8 236 líneas | `find frontend/src -name '*.ts*'` |
| Cobertura backend | **85,6 %** de líneas | JaCoCo (`mvn verify`) |
| Suite backend | **294 pruebas**, 0 fallos | `mvn test` |
| Esquema contra PostgreSQL 18.6 | **5 pruebas**, 0 fallos | `EsquemaPostgreSqlIntegrationTest` |
| Suite frontend | **28 pruebas**, 0 fallos | `npm run test:run` |
| Vulnerabilidades npm | **0** | `npm audit` |

**Deuda de comentarios: cero.** No hay un solo `TODO`, `FIXME` ni `HACK` en todo
el código de producción. No hay `System.out`, `printStackTrace` ni `console.log`.
No hay bloques `catch` vacíos. Esto es infrecuente y merece decirse.

---

## 2. Lo que está bien resuelto

No es cortesía: entender qué funciona evita romperlo al arreglar lo demás.

**La autorización tiene dos capas y las dos se ejercitan.** `SecurityConfig`
declara la matriz por ruta y método, y `SecurityUtils.verificarAccesoAlContrato`
acota lo que ve un SUPERVISOR a nivel de servicio. Devuelve 404 y no 403 para no
funcionar como oráculo de enumeración. `AislamientoEntreSupervisoresIntegrationTest`
lo verifica con 29 pruebas de acceso cruzado.

**La desactivación de una cuenta es inmediata.** `JwtAuthenticationFilter:49`
filtra por `Usuario::isActivo` en **cada** petición, no solo al emitir el token.
Es la diferencia entre revocar el acceso ahora y revocarlo cuando expire el JWT
—hasta ocho horas después—, y muchos sistemas con JWT se equivocan justo aquí.

**El esquema se verifica contra PostgreSQL de verdad.**
`RestriccionesDeEnumEnMigracionesTest` compara cada `CHECK` de las migraciones
con su enum de Java leyendo los `.sql`, y `EsquemaPostgreSqlIntegrationTest`
repite la comprobación contra una base real con `ddl-auto=validate`. Es la única
forma de detectar que una entidad y su migración dejaron de decir lo mismo, y H2
no puede hacerlo por construcción.

**El sistema falla honesto.** `OllamaClient` responde 503 en vez de inventar,
`EmailService` lanza en vez de fingir que envió, y `VerificacionDelModeloIa`
avisa al arrancar si falta el modelo. La misma línea se sostiene en el frontend:
`VistaAlertas` distingue «no hay alertas» de «no se pudieron consultar».

**El despliegue está pensado.** `docker-compose.prod.yml` cierra el puerto de
PostgreSQL, retira Adminer y **falla al arrancar** si faltan `DB_PASSWORD`,
`JWT_SECRET` o las credenciales del administrador inicial. Todos los servicios
tienen `healthcheck`, `restart: unless-stopped` y límites de memoria.

**La CI cubre las cuatro piezas.** Backend con PostgreSQL real, frontend
(tipos + pruebas + build), el servidor MCP, y un job propio de vulnerabilidades.

---

## 3. Hallazgos

Ordenados por severidad. La severidad mide consecuencia, no esfuerzo.

### A · CRÍTICO — El respaldo que sostiene el RPO de 24 h no lo ejecuta nadie

**Evidencia.** ADR-002 compromete un RPO de 24 h y lo justifica con «Respaldo
diario verificado (`scripts/respaldo-sicot.sh`, cron 02:00)». El script existe y
está probado. La instalación del cron **no aparece en ninguna parte del
repositorio**:

```
$ grep -rn "respaldo-sicot\|crontab\|cron" docker-compose*.yml .github/workflows/*.yml
docker-compose.ensayo.yml:15:#        ./scripts/respaldo-sicot.sh ./respaldos   ← comentado
```

**Por qué es crítico.** El RPO de 24 h es hoy una intención, no una capacidad. Si
nadie ejecutó ese `crontab -e` a mano en el servidor —y nada en el repositorio
dice que se hizo, ni hay forma de comprobarlo desde el sistema— la pérdida real
ante un fallo de disco es **todo lo que haya en la base**.

**Lo más grave es que es una repetición.** ADR-006 ya dejó escrita esta misma
lección con estas palabras: *«un paso manual escrito en un documento no es un
control»*. Se aprendió con el modelo de IA y se resolvió bien
(`VerificacionDelModeloIa`). El respaldo tiene exactamente la misma forma y no
recibió el mismo tratamiento.

### B · ALTO — La auditoría atribuye al «Sistema» acciones de personas

**Evidencia.** `frontend/src/services/mappers.ts:86`

```ts
actor: r.usuarioNombre ?? 'Sistema',
```

Y en `V1__create_sicot_schema.sql:118`:

```sql
usuario_id BIGINT REFERENCES usuarios (id) ON DELETE SET NULL
```

**El fallo.** Borrar una cuenta pone a `NULL` el `usuario_id` de **todas** sus
entradas de auditoría. El frontend interpreta ese `NULL` como «lo hizo el
sistema». Resultado: las acciones de una persona que ya no está en la
organización aparecen atribuidas al sistema, en el registro que existe
precisamente para saber quién hizo qué.

En contratación pública eso no es un defecto de presentación: es un expediente
que afirma algo falso sobre quién tomó una decisión.

**Estado.** El dato para arreglarlo ya existe: la columna `registros.origen`
(`USUARIO` | `SISTEMA`) que introdujo V15. El frontend todavía no la usa.

### C · ALTO — No hay HTTPS en ningún despliegue

**Evidencia.** `frontend/nginx.conf` sirve HTTP en el 8443 (el número sugiere
TLS; no lo hay). `docker-compose.prod.yml` no monta certificados ni termina TLS.
`SecurityConfig` sí emite `Strict-Transport-Security`, una cabecera que un
navegador **ignora sobre HTTP**.

**Consecuencia.** El JWT vive en `localStorage` (`services/session.ts:6`) y viaja
en cada petición por la red del centro en texto claro, junto con las
credenciales del formulario de acceso. Cualquiera en la misma red los lee.

Está reconocido como pendiente en `backend/README.md §10`, pero mientras no
exista, «listo para despliegue empresarial» no es exacto.

### D · MEDIO — Listados sin paginación en tablas de crecimiento libre

**Evidencia.**

| Repositorio | Método | Tope |
| --- | --- | --- |
| `ContratoRepository:27` | `findAll()` | ninguno |
| `ContratoRepository:30,33,36` | `findBySupervisorId`, `findByEstado`… | ninguno |
| `AlertaRepository:11` | `findByContratoIdOrderByFechaCreacionDesc` | ninguno |
| `RegistroRepository:11` | `findByContratoIdOrderByFechaDesc` | ninguno |

Los listados **globales** sí están acotados (`MAX_ALERTAS_LISTADO = 500`,
`MAX_REGISTROS_LISTADO = 500`). Los **por contrato** no.

**Por qué importa ahora y no antes.** Con un horizonte de 3-4 años, `GET
/api/contratos` devuelve cada contrato que haya existido, en cada carga del panel
de GESTIÓN. Y el motor de automatizaciones recién incorporado **empieza a
escribir en `alertas`**, que hasta ahora no crecía nunca: la lista por contrato
pasa de estar siempre vacía a crecer sola.

### E · MEDIO — La contraseña temporal no caduca ni obliga a cambiarse

**Evidencia.** `EmailService.enviarCredenciales` envía la contraseña temporal con
el texto «Por seguridad, cambiala la primera vez que ingreses». No hay ningún
mecanismo que lo exija:

```
$ grep -rn "debeCambiar\|primerIngreso\|cambiarPassword" backend/src/main frontend/src
(sin resultados)
```

La política de contraseñas es solo de longitud (`@Size(min = 8, max = 100)`): sin
requisito de complejidad y sin comprobación contra el propio correo.

**Consecuencia.** Una contraseña generada por el administrador y enviada por
correo puede seguir siendo válida indefinidamente, y queda en el buzón de la
persona y en el del servidor de correo.

### F · MEDIO — El módulo de automatizaciones bajó la cobertura del proyecto

**Evidencia.** JaCoCo, antes y después del módulo:

| | Antes | Después |
| --- | --- | --- |
| Total del proyecto | 90,4 % | **85,6 %** |
| `automatizacion.acciones` | — | **35,3 %** |
| `automatizacion` | — | 69,3 % |
| `automatizacion.reglas` | — | 85,6 % |

**Dónde está el hueco.** Las reglas están bien probadas (once pruebas unitarias
puras). Las **acciones** no: `RedaccionDeResumenIa` no tiene ninguna prueba —ni
la construcción del prompt, ni el carril de concurrencia, ni el descarte por
periodo sin actividad— y de `EnvioDeCorreo` solo se ejercita el camino de
descarte, nunca el de envío correcto.

Es deuda que se introdujo en este mismo cambio y hay que saldarla aquí.

### G · MEDIO — La cola de automatizaciones crece sin purga

**Evidencia.** `V15__motor_de_automatizaciones.sql` crea `tareas_automatizadas`
sin ninguna política de retención. Una tarea `COMPLETADA` se queda para siempre.

**Consecuencia.** Con seis reglas evaluándose a diario sobre los contratos
activos, la tabla crece de forma monótona. No es urgente por volumen —son filas
pequeñas— pero sí lo es por coherencia: ADR-003 ya estableció que el tamaño de la
base es lo que determina si la restauración cabe en el RTO de 4 h de ADR-002.
Añadir una tabla que crece sin techo y sin vigilancia contradice esa decisión.

### H · MEDIO — El frontend está poco probado en proporción a su tamaño

**Evidencia.** 8 236 líneas de TypeScript y **28 pruebas** en 5 archivos. El
backend tiene 7 646 líneas de pruebas para 11 047 de producción (razón 0,69); el
frontend no llega a una décima parte de esa proporción.

`SupervisorPanel.tsx` tiene **948 líneas** y concentra el estado de todo el panel
—etapas, documentos, alertas, copiloto, semáforo—. Es el archivo más grande del
repositorio y el que más lógica de negocio de presentación acumula.

Además, la suite emite avisos de React que no se están atendiendo:

```
An update to SupervisorPanel inside a test was not wrapped in act(...)
```

Un aviso ignorado enseña a ignorar los avisos.

### I · BAJO — Accesibilidad sin cobertura sistemática

**Evidencia.** 24 elementos `<input>` en el frontend frente a 11 apariciones de
`aria-label` o `htmlFor` en total. Solo 7 de 51 archivos contienen algún
atributo `aria-*` o `role`.

**Contexto.** SICOT es un sistema del SENA, una entidad pública colombiana
sujeta a la Resolución 1519 de 2020 (criterios de accesibilidad web para sitios
del Estado). No es una mejora opcional: es un requisito normativo del cliente
final, y hoy no hay ninguna verificación automática que lo mida.

### J · INFORMATIVO — El aviso de integridad identifica el documento por nombre, no por id

**Evidencia.** `AvisoDeIntegridadComprometida` compone su mensaje con
`evento.descripcion()`, y `DocumentoService:230` ya escribe ahí el **nombre** del
documento afectado.

**Corrección de esta auditoría.** Una primera lectura anotó esto como un hallazgo
de severidad baja —«no se sabe cuál documento»— y era inexacto: el nombre sí
viaja. Lo que no viaja es el identificador, así que la alerta no puede enlazar
directamente al documento. Es una mejora de comodidad, no un defecto: quien
investigue tiene el nombre y el contrato, que basta para encontrarlo.

Se deja anotado en vez de borrado porque una auditoría que solo muestra los
hallazgos que sobrevivieron oculta cuánto de lo que afirma se comprobó de
verdad.

---

## 4. Lo que se comprobó ejecutando, no leyendo

| Comprobación | Resultado |
| --- | --- |
| `mvn test` (294 pruebas) | verde |
| `mvn verify` + JaCoCo | verde · 85,6 % |
| V15 sobre PostgreSQL 18.6 real + `ddl-auto=validate` | 8 migraciones aplicadas, esquema validado |
| `npm run test:run` (28 pruebas) | verde |
| `npm audit` | 0 vulnerabilidades |
| Stack completo `docker compose up -d --build` | 4 contenedores sanos |
| Contrato a 12 días de vencer → evaluación → cola → alertas | **3 alertas reales creadas en la base** |
| Asignación de supervisor → regla de evento | 2 tareas encoladas al instante |
| Tres evaluaciones consecutivas del calendario | 0 tareas nuevas (idempotencia confirmada) |

---

## 5. Lo que esta auditoría **no** cubre

Decirlo importa tanto como lo anterior:

- **Pruebas de carga.** No se midió el comportamiento con cientos de contratos ni
  con documentos `BYTEA` de cientos de megabytes. ADR-002 ya lo dejó como
  pendiente explícito y sigue pendiente.
- **Calidad de la salida del modelo de IA.** No hay un conjunto de evaluación con
  documentos reales del SENA, así que no se puede afirmar nada medible sobre si
  lo que redacta el copiloto es bueno. ADR-006 lo reconoce.
- **Revisión de la migración A de ADR-001** (trabajo sin conexión). Está fuera de
  alcance por decisión, no por olvido.
- **Penetración real.** Se revisó la configuración de seguridad y el aislamiento
  por rol leyendo el código y ejecutando la suite; no se ejecutó ninguna
  herramienta de intrusión.
