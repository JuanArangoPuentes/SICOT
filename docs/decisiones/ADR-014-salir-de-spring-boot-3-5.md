# ADR-014 — Cuándo y cómo salir de Spring Boot 3.5

**Estado:** Propuesta · **Fecha:** 2 de octubre de 2026

## Contexto

El backend hereda de `spring-boot-starter-parent` **3.5.16** (`backend/pom.xml`).
Una investigación del 1 de octubre de 2026 señaló que esa línea ya no recibe
parches gratuitos. Este documento confirma las fechas en la fuente, mide qué se
rompería al migrar leyendo el código real y propone cuándo hacerlo. **No migra
nada**: la migración espera la aprobación de Juan.

### Las fechas, en la fuente oficial

Consultadas el 1 de octubre de 2026 en `https://api.spring.io/projects/<proyecto>/generations`
—la API de la que se alimenta la página de soporte de spring.io— y contrastadas
con `https://endoflife.date/api/v1/products/spring-boot`, que coincide día por día.

| Línea | Publicada | Fin del soporte gratuito (OSS) | Último parche OSS |
| --- | --- | --- | --- |
| Spring Boot 3.5.x | 31-may-2025 | **30-jun-2026** | 3.5.16 (25-jun-2026) — la de SICOT |
| Spring Boot 4.0.x | 30-nov-2025 | 31-dic-2026 | 4.0.8 |
| Spring Boot 4.1.x | 30-jun-2026 | **31-jul-2027** | 4.1.1 (20-ago-2026) |
| Spring Boot 4.2.x | prevista 30-nov-2026 | 31-dic-2027 | — |
| Spring Framework 6.2.x (va con Boot 3.5) | 30-nov-2024 | 30-jun-2026 | 6.2.19 |
| Spring Framework 7.0.x (va con Boot 4.0 y 4.1) | 30-nov-2025 | 31-jul-2027 | — |
| Spring Security 6.5.x (va con Boot 3.5) | 31-may-2025 | 30-jun-2026 | 6.5.11 |
| Spring Security 7.1.x (va con Boot 4.1) | 30-jun-2026 | 31-jul-2027 | 7.1.1 |

La línea 3.5 tiene soporte comercial hasta 2032, pero es de pago (Broadcom/Tanzu)
y la regla de que SICOT sea 100 % gratuito lo descarta.

Dos correcciones a la premisa de la investigación:

1. **El destino no es 4.0, es 4.1.** La línea 4.1 existe desde el 30 de junio de
   2026 y tiene soporte hasta el 31 de julio de 2027; la 4.0 termina el 31 de
   diciembre de 2026. Migrar a 4.0 compraría tres meses y obligaría a otro salto
   en diciembre. Además cuesta lo mismo: la ruptura grande —Framework 7, Jackson 3,
   starters modulares— está en 4.0, y lo que 4.1 retira (Derby, `layertools`,
   las APIs obsoletas en 4.0) SICOT no lo usa.
2. **Spring AI no aplica a SICOT.** No hay ninguna dependencia, import ni
   propiedad de Spring AI en el backend (búsqueda en `pom.xml`, código y
   propiedades: cero resultados). El Copiloto habla con Ollama directamente con
   `RestClient` (`OllamaClient`, `VerificacionDelModeloIa`). Que Spring AI 1.1
   haya perdido soporte no afecta, y Spring AI 2.0 no es un requisito.

### Lo que ya pasó desde que 3.5 quedó sin soporte

**Spring Security 6.5 ya tiene CVE sin arreglo gratuito.** El 20 de agosto de
2026 Spring publicó CVE-2026-41707 y CVE-2026-47841, las dos de severidad alta,
que afectan a Spring Security 6.5.0–6.5.11. La corrección gratuita sólo existe en
7.0.7 y 7.1.1; la 6.5.12 figura como *Enterprise Support Only*. Ninguna de las dos
es explotable en SICOT —son de DPoP y de WebAuthn, y aquí la autenticación es el
filtro JWT propio—, pero demuestran el mecanismo: los adelantos del `pom.xml`
funcionan para Tomcat y Jackson porque Apache y FasterXML siguen publicando
parches gratuitos de 10.1 y 2.21; **para Spring Framework 6.2, Spring Security
6.5 y Spring Boot 3.5 no existe una versión gratuita a la que subir**. La próxima
CVE puede caer en algo que SICOT sí usa: la cadena de filtros, `@PreAuthorize`,
BCrypt o Spring MVC.

**La compuerta de Trivy está en rojo.** En la ejecución del CI del 1 de octubre
de 2026 (`36935500903`), el trabajo «Vulnerabilidades en dependencias» falla
sobre `backend/pom.xml` con cuatro CVE altas, publicadas el 30 de septiembre y el
1 de octubre, en Jackson 2.21.6 (CVE-2026-89407 y CVE-2026-89425 en
`jackson-core`; CVE-2026-91776 y CVE-2026-91777 en `jackson-databind`),
corregidas en 2.21.7, que está en Maven Central desde el 21 de septiembre. Ésta sí
tiene arreglo gratuito, pero ilustra el costo de vivir de adelantos: cada
publicación de Jackson o de Tomcat exige que alguien la note y suba el número a
mano. Tomcat 10.1.60, por ejemplo, salió el 9 de septiembre y el `pom.xml` sigue
en 10.1.59.

**Las mismas CVE alcanzan a Jackson 3 hasta 3.1.6.** Boot 4.1.1 gestiona Jackson
3.1.5, así que migrar a 4.1.1 tal cual tampoco pondría a Trivy en verde: hace
falta adelantar a 3.1.7 hasta que una 4.1.x la gestione. Migrar no elimina los
adelantos; los devuelve a ser la excepción en vez del único camino.

### Qué rompe la migración en SICOT

Inventario hecho leyendo el código en el commit `ef0b763` (171 clases de
producción, 59 archivos de prueba, 410 pruebas) contra la guía de migración de
Spring Boot 4.0, las notas de Spring Boot 4.1, las notas de Spring Framework 7.0,
la guía de migración de Spring Security 7, las guías de migración de Hibernate
7.0 a 7.4 y la lista de cambios de valores por defecto de Jackson 3 (JSTEP-2).
Las ubicaciones de clases se comprobaron en los JAR de 4.1.1 de Maven Central, no
de memoria.

Cada punto se clasifica por **cómo falla si se olvida**: *ruidoso* (no compila o
no arranca: imposible de pasar por alto) o *silencioso* (compila, arranca y se
comporta distinto).

| Área | Qué cambia | Dónde toca a SICOT | Si se olvida |
| --- | --- | --- | --- |
| Starters modulares | Flyway ya no se activa sólo con `flyway-core`: hace falta `spring-boot-starter-flyway`. `spring-boot-starter-web` pasa a llamarse `-webmvc` (el viejo sigue, obsoleto). La infraestructura de pruebas va por tecnología. | `pom.xml` | Flyway: **semisilencioso**. Contra una base vacía el arranque falla (`validate`); contra la base de producción ya migrada **arranca**, y una migración futura que sólo añada índices o datos —como V9 o V16— no se aplicaría nunca. El CI sí lo detecta: la prueba de esquema y el job extremo a extremo parten de una base vacía. |
| Anotaciones de prueba | `@AutoConfigureMockMvc` pasa a `org.springframework.boot.webmvc.test.autoconfigure`; `@DataJpaTest` a `org.springframework.boot.data.jpa.test.autoconfigure`; `@WithMockUser` exige `spring-boot-starter-security-test`. `@LocalServerPort` no cambia y `@MockitoBean` ya está adoptado. | `PruebaDeIntegracion`, `EtapaServiceTest`, `pom.xml` | Ruidoso. |
| Jackson 3 | El paquete `com.fasterxml.jackson` pasa a `tools.jackson` (las anotaciones no cambian). Boot configura un `JsonMapper` de Jackson 3 y deja de existir el bean `ObjectMapper` de Jackson 2. Las excepciones pasan a ser no comprobadas (`JacksonException`). | Producción: `PayloadJson`, `ExtraccionContratoService`, `ListaChequeoService` (`OllamaClient` y `VerificacionDelModeloIa` sólo usan anotaciones y no cambian). Pruebas: 24 archivos (21 inyectan `ObjectMapper`, 3 lo crean) y 4 usan `JsonNode` (7 llamadas a `asText()`, que pasa a `asString()`). | Ruidoso casi todo: errores de compilación, y si sobrevive una inyección de Jackson 2, el contexto no arranca por falta del bean. Hay dos excepciones, abajo. |
| springdoc | La línea 2.x se construye sobre Boot 3.5.16; la 3.1.1, sobre Boot 4.1.0. Las anotaciones `io.swagger` (91 imports) no cambian de paquete: swagger-core sigue en 2.2.x. | `pom.xml` | Ruidoso. |
| Spring Security 6.5 → 7.1 | Retiradas del DSL antiguo, de la Access API y de los *matchers* por Ant/MVC. `SecurityConfig` ya está escrita al estilo 7: DSL con lambdas, `authorizeHttpRequests`, *matchers* por texto con `**` sólo al final (compatibles con `PathPatternRequestMatcher`), `@EnableMethodSecurity` y sin Access API. Las 22 clases de Security que importa SICOT existen en 7.1.1 con el mismo paquete. Los 23 `@PreAuthorize` son `hasRole`/`hasAnyRole`, muy por debajo del nuevo tope de 10 000 operaciones de SpEL. | Ninguno previsto | — |
| Exclusión de autoconfiguración | `UserDetailsServiceAutoConfiguration` cambió de paquete. | `application.properties` | **Silencioso.** Ver abajo. |
| Spring Framework 7 | `HttpHeaders` deja de ser un `MultiValueMap` (SICOT sólo usa sus constantes, 3 usos). Se retiran opciones viejas de enrutado (no se usan). El `RestClient` armado a mano en `OllamaClient` elige Jackson 3 por su cuenta; las anotaciones se comparten y `OllamaClientTest` comprueba el JSON que se envía. JUnit 6 llega gestionado: SICOT sólo usa Jupiter y no tiene `@Nested`. El cambio de CORS en *preflight* no aplica: una configuración vacía ya detiene el arranque. | — | Ruidoso, si algo. |
| Hibernate 6.6 → 7.4 | La 7.0 retira la API nativa `Session#save/update/load`; la 7.4 infiere `NOT NULL` en las columnas de `@CreationTimestamp` y `@UpdateTimestamp` del DDL que genera Hibernate. SICOT usa repositorios de Spring Data, 8 consultas JPQL estándar (constructor, `CASE`, `UPDATE`/`DELETE` masivos), `@Version` en 7 entidades y dos `bytea` con `@JdbcTypeCode(VARBINARY)`; no usa la API nativa, ni convertidores, ni consultas nativas. El `NOT NULL` inferido sólo afecta al esquema H2 de las pruebas (`create-drop`); en producción el esquema lo pone Flyway. | Suite sobre H2 | Ruidoso (falla una prueba), si algo. |
| Adelantos del `pom.xml` | 4.1.1 ya gestiona `postgresql` 42.7.13 (la misma), `commons-lang3` 3.20.0 (mayor) y `log4j2` 2.25.5 (la misma): esos tres sobran. `tomcat.version=10.1.59` tiene que irse: Boot 4 exige Servlet 6.1, es decir Tomcat 11 (4.1.1 gestiona 11.0.24). Y `jackson-bom.version` **cambia de significado**: en Boot 4 gobierna `tools.jackson:jackson-bom` (Jackson 3), así que dejarlo en 2.21.6 pide una versión que no existe; el adelanto de Jackson 2 pasa a `jackson-2-bom.version`. | `pom.xml` | Ruidoso, salvo dejar adelantos que ya sobran, que sólo es ruido. |
| Propiedades | Las 50 claves estándar de los `application*.properties` se cruzaron con los registros oficiales de cambios de configuración de 4.0 y 4.1 (secciones de obsoletas y retiradas): ninguna aparece. El método se validó con una clave que sí se retiró. | — | Confirmarlo una vez en ejecución con `spring-boot-properties-migrator`. |
| Actuator | Las sondas de *liveness* y *readiness* pasan a estar activas por defecto; las rutas nuevas quedan detrás del JWT porque `SecurityConfig` sólo abre `/actuator/health`, que es la que usan el `Dockerfile` y el `docker-compose.yml`. | — | — |
| Construcción | Java 25, Maven 3.9.15 y las imágenes `maven:3.9-eclipse-temurin-25` y `eclipse-temurin:25-jre-alpine` son compatibles. No hay dependencias `<optional>`, ni scripts de lanzamiento embebidos, ni Undertow. | — | — |

### Los tres puntos que la red de pruebas no ve

1. **La exclusión de `UserDetailsServiceAutoConfiguration` se ignoraría sin
   aviso.** `application.properties` excluye
   `org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration`.
   En Boot 4 esa clase vive en `org.springframework.boot.security.autoconfigure`
   (comprobado en `spring-boot-security-4.1.1.jar`) y no hay tabla de reemplazos
   que traduzca el nombre viejo. `AutoConfigurationImportSelector#checkExcludedClasses`
   sólo protesta por exclusiones de clases que *existen*; un nombre que ya no
   existe se descarta en silencio. Resultado: Boot vuelve a crear el usuario en
   memoria con contraseña aleatoria y a escribir en el log de producción el
   «Using generated security password…» que esa línea existe para evitar. No es
   explotable —no hay `formLogin` ni `httpBasic`—, pero ninguna prueba lo detecta.
2. **`ListaChequeoService` perdería el nombre del archivo en el error.** El
   `catch (IOException e)` alrededor de `mapper.readValue(entrada, …)` sigue
   compilando, porque `getInputStream()` lanza `IOException`. Pero en Jackson 3 un
   JSON mal formado o con una propiedad desconocida lanza `JacksonException`, que
   no es `IOException` y se escapa de ese `catch`: el arranque sigue fallando, pero
   sin decir cuál de los archivos de `listas-chequeo/` está roto. Ninguna prueba
   cubre ese camino.
3. **En `ExtraccionContratoService` el riesgo está en cómo se arregle el error de
   compilación.** Su `catch (IOException e)` deja de compilar, porque ya nada
   lanza `IOException` dentro del `try`. El arreglo tentador —borrar el `catch`—
   eliminaría el respaldo que devuelve la extracción determinista cuando el modelo
   responde un JSON inválido, y lo convertiría en un error 500. El arreglo
   correcto es `catch (JacksonException e)`. Aquí sí hay una prueba que protege:
   `unaRespuestaQueNoEsElJsonEsperadoNoTumbaElAnalisisDeLosDemas`.

### Los valores por defecto de Jackson 3, contra SICOT

| Cambio de Jackson 3 | Efecto medido en SICOT |
| --- | --- |
| `SORT_PROPERTIES_ALPHABETICALLY` pasa a `true` | Cambia el orden de los campos en las respuestas. El frontend lee por nombre; nada compara el orden. Sin impacto. |
| `FAIL_ON_NULL_FOR_PRIMITIVES` pasa a `true` | Ninguno de los 14 cuerpos de petición declara campos primitivos; los primitivos sólo están en respuestas y en cargas internas que escribe la propia aplicación. Sin impacto. |
| `READ_`/`WRITE_ENUMS_USING_TO_STRING` pasan a `true` | Ninguno de los 16 enums redefine `toString()`, así que la salida es la misma. |
| Fechas y duraciones como texto ISO; UTC como `Z` | Boot 3 ya escribía las fechas en ISO; no hay `Duration`, `OffsetDateTime` ni `ZonedDateTime` en los DTO, y un `Instant` siempre sale con `Z`. |
| `FAIL_ON_UNKNOWN_PROPERTIES` pasa a `false` | En la API ya era `false` con Boot 3; `ListaChequeoService` lo activa a propósito y se conserva. |
| `DEFAULT_VIEW_INCLUSION` pasa a `false` | No se usa `@JsonView`. |

Con este impacto no hay motivo para activar `spring.jackson.use-jackson2-defaults`:
se adoptan los valores de Jackson 3.

### Por qué no el puente `spring-boot-jackson2`

Boot 4 ofrece el módulo `spring-boot-jackson2` (obsoleto desde 4.0, todavía
presente en 4.1.1) para seguir usando Jackson 2 como *mapper* de la aplicación.
Las notas de Spring Framework 7.0 dicen que la detección automática de Jackson 2
se desactiva en Framework 7.1 y se retira en 7.2, y Framework 7.1 está previsto
para el 30 de noviembre de 2026, el mismo día que Boot 4.2. Usar el puente
significa migrar Jackson dos veces, la segunda con prisa.

### Jackson 2 se queda en el classpath de todos modos

springdoc 3.1.1 trae swagger-core 2.2.55, que depende de `jackson-databind` 2, y
`jjwt-jackson` 0.13.0 —la última versión de jjwt, de agosto de 2025; no existe un
módulo para Jackson 3— serializa los JWT con Jackson 2. `JwtService` sólo usa
`jjwt-api`, así que no cambia. Mientras esas dos dependencias sigan en Jackson 2,
su adelanto de versión sigue haciendo falta: Boot 4.1.1 gestiona 2.21.5.

### La red de seguridad que ya existe

410 pruebas sobre H2; `EsquemaPostgreSqlIntegrationTest` en el CI contra
PostgreSQL 18 (Flyway, `validate` y comprobaciones del catálogo); el job
extremo a extremo con Playwright, que arranca el JAR real contra PostgreSQL real;
y la compuerta de Trivy. Cubre todos los puntos ruidosos y el de Flyway. No cubre
los puntos 1 y 2 de la sección anterior.

## Decisión

**Se propone migrar el backend a Spring Boot 4.1.x —no a 4.0— adoptando Jackson 3
en el mismo cambio, durante octubre de 2026, en un PR dedicado.** Hasta que Juan
lo apruebe no se ejecuta nada de esto.

**Por qué ahora.** Los arreglos de seguridad de Spring ya no son gratuitos en 3.5,
y eso ya pasó una vez. La línea 4.1 tiene soporte hasta el 31 de julio de 2027:
empezar en octubre deja unos nueve meses de parches por delante, y el siguiente
salto (4.1 → 4.2) es menor. Esperar acorta ese margen y suma código escrito
contra Jackson 2. Y el código está en buen punto para hacerlo: ya usa
`@MockitoBean`, el DSL de seguridad moderno y Java 25, no depende de Spring AI y
no tiene ninguna propiedad obsoleta. El costo medido es bajo, y sólo puede subir.

**Por qué 4.1 y no 4.0.** Cuesta lo mismo y da siete meses más de soporte.

**El plan, con su esfuerzo:**

| Paso | Contenido | Esfuerzo |
| --- | --- | --- |
| 0. Preparación, sobre 3.5.16 | Dos pruebas que vigilen los puntos silenciosos: que el contexto no contenga ningún `UserDetailsService`, y que un archivo de lista de chequeo roto produzca un error que nombre el archivo. Pasan hoy; después de migrar, son la guardia. | ½ día |
| 1. PR de migración | `pom.xml`: parent 4.1.x; `spring-boot-starter-webmvc`; `spring-boot-starter-flyway` (se conserva `flyway-database-postgresql`); los starters de prueba `webmvc-test`, `data-jpa-test` y `security-test`; springdoc 3.1.x; retirar los adelantos de `postgresql`, `commons-lang3`, `log4j2` y `tomcat`; `jackson-bom.version` a 3.1.7 y `jackson-2-bom.version` a 2.21.7 mientras Boot no los gestione. Código: las 3 clases de producción y los 24 archivos de prueba a `tools.jackson`, `catch (JacksonException e)` en los dos servicios, los 2 imports de anotaciones de prueba y el nombre nuevo en la exclusión. Una pasada con `spring-boot-properties-migrator`, que luego se quita. Compuerta: `./mvnw verify` y el CI completo (esquema en PostgreSQL, extremo a extremo, Trivy). | 2–3 días |
| 2. Ensayo con datos reales | `docker-compose.ensayo.yml` con un contrato completo: inicio de sesión con los tres roles, crear y editar un contrato, extracción con IA sobre PDF institucionales reales con Ollama, generar y firmar un documento, fotos con EXIF, el motor de automatizaciones, `/actuator/prometheus` con JWT, Swagger en desarrollo, y un log de arranque sin «generated security password». | 1 día |

**Total: de 3,5 a 4,5 días-persona; una o dos semanas de calendario contando la
revisión.** El PR no se mezcla con trabajo funcional, para que cualquier
regresión apunte al salto de versión y a nada más.

**Mientras no se apruebe (mitigación).** Se mantienen los adelantos del
`pom.xml`. El urgente al 2 de octubre es subir Jackson 2 a 2.21.7 para desbloquear
Trivy, y no depende de este ADR. La mitigación tiene un límite que no se puede
mover: no cubre Spring Framework, Spring Security ni Spring Boot. Si aparece una
CVE en un componente que SICOT usa —la cadena de filtros, la seguridad de
métodos, BCrypt, Spring MVC— sin corrección gratuita en 6.2, 6.5 o 3.5, la
migración deja de ser planificada y pasa a ser urgente, porque no habrá adelanto
posible.

## Consecuencias

**Lo que se gana.** Parches gratuitos de Boot, Framework y Security hasta el 31 de
julio de 2027. El `pom.xml` pierde cuatro de sus cinco adelantos (`postgresql`,
`commons-lang3`, `log4j2`, `tomcat`); el de Jackson se queda, partido en dos
—uno por generación— hasta que el parent alcance las versiones corregidas. La ruta caliente —deserializar los cuerpos
de petición de la API— pasa a la línea de Jackson que se mantiene. Y el proyecto
queda alineado con Spring 7 para los saltos menores siguientes.

**Lo que se pierde.** De 3,5 a 4,5 días de trabajo. Dos generaciones de Jackson
conviven en el JAR mientras springdoc y jjwt dependan de Jackson 2, así que hay
dos familias de avisos que vigilar. El orden de los campos en las respuestas pasa
a ser alfabético: inofensivo, pero visible para quien compare respuestas a ojo.

**Lo que queda prohibido.**

- Elegir 4.0.x como destino.
- Usar `spring-boot-jackson2` como solución permanente, o activar
  `use-jackson2-defaults` sin un motivo medido.
- Arreglar el error de compilación de `ExtraccionContratoService` borrando el
  `catch`.
- Dejar `jackson-bom.version` con un valor 2.x o `tomcat.version` en 10.1.x bajo
  Boot 4.
- Contratar soporte comercial como mitigación: choca con la regla de gratuidad.
- Mezclar la migración con trabajo funcional en el mismo PR.

## Cuándo revisar

- **Cuando Juan apruebe o rechace la propuesta.** Si la aprueba, el estado pasa a
  *Aceptada*; si la rechaza, se escribe el motivo y la fecha en que se vuelve a
  mirar.
- **Si aparece una CVE en Spring Framework, Spring Security o Spring Boot** que
  afecte a algo que SICOT usa y no tenga corrección gratuita en 6.2, 6.5 o 3.5: la
  migración pasa por delante de cualquier otro trabajo.
- **Si el 30 de noviembre de 2026 (salida de Boot 4.2) la migración no ha
  empezado:** evaluar ir directo a 4.2. Con Framework 7.1 desactivando la
  detección de Jackson 2, el argumento contra el puente se vuelve más fuerte.
- **Cuando una 4.1.x gestione Jackson 3 ≥ 3.1.7 y Jackson 2 ≥ 2.21.7:** retirar
  esos adelantos, igual que pide ADR-011 para los actuales.
- **Si springdoc y jjwt dejan de depender de Jackson 2:** evaluar si Jackson 2 puede
  salir del classpath.
- **Antes del 30 de abril de 2027:** planear el salto siguiente, con tres meses de
  margen antes de que termine el soporte de 4.1 (31 de julio de 2027).
