# ADR-011 — Arranque fail closed y qué escáner es compuerta en el CI

**Estado:** Aceptada · **Fecha:** 9 de septiembre de 2026

## Contexto

Una revisión de seguridad externa del 8 de septiembre de 2026 escaneó el
repositorio con Semgrep, Trivy y Gitleaks y produjo nueve hallazgos. Al
verificarlos uno por uno aparecieron dos problemas que el informe no separaba y
que son de naturaleza distinta.

**Uno: el perfil de desarrollo era el valor por defecto.**
`application.properties` declaraba `spring.profiles.active=${SPRING_PROFILES_ACTIVE:dev}`.
Un `java -jar sicot-backend.jar` o un `docker compose up` con el archivo base y
sin variables levantaba un backend que firma tokens con el secreto de
conveniencia **publicado en este repositorio** (`application-dev.properties`) y
que además siembra las cuentas demo con contraseñas conocidas
(`DataInitializer`). Nada fallaba: arrancaba perfectamente. Ésa es la forma
peligrosa de fallar — un despliegue descuidado no producía ninguna señal, y
quien lo hiciera no tenía forma de enterarse.

**Dos: ninguna compuerta de dependencias bloqueaba de verdad.** El CI tenía
`npm audit --audit-level=high` sobre el árbol completo y OWASP Dependency-Check
con `continue-on-error: true`. La consecuencia se puede medir: tres avisos
moderados en `hono` —una dependencia de producción del MCP— pasaban por debajo
del umbral, y tres CVE **críticas** en `tomcat-embed-core` no bloqueaban nada
porque el único análisis del backend estaba marcado para no fallar nunca.
Dependency-Check está así por un motivo real: depende de la NVD, un servicio
externo que se cae y limita por IP, así que como compuerta produce PRs rojos que
no dicen nada del código. Pero el resultado neto era que **el backend no tenía
compuerta**.

## Decisión

**Uno. El perfil por defecto pasa a ser `prod`.** Arrancar sin declarar perfil
ya no levanta desarrollo: se detiene, porque `prod` no trae respaldo para
`JWT_SECRET` y `JwtService` lo exige y lo explica.

Los caminos legítimos de desarrollo declaran su perfil explícitamente y no
cambian:

| Camino | Cómo declara el perfil |
| --- | --- |
| `./mvnw spring-boot:run` | `<profiles><profile>dev</profile></profiles>` en el `pom.xml` |
| `docker compose up` (archivo base) | `SPRING_PROFILES_ACTIVE: ${SPRING_PROFILES_ACTIVE:-dev}` |
| La suite de pruebas | `@ActiveProfiles("test")` en `PruebaDeIntegracion` |
| Producción | `SPRING_PROFILES_ACTIVE: prod` literal en `docker-compose.prod.yml` |

`JwtService` valida el secreto en el constructor —presente, Base64 válido, ≥ 32
bytes— y cada fallo dice qué falta y cómo generarlo (`openssl rand -base64 48`).
Un error de arranque que obliga a leer el código fuente termina en alguien
copiando el primer valor que encuentre, y el primer valor que se encuentra en
este repositorio es justo el que no debe usarse.

**Dos. Trivy es la compuerta de dependencias; Dependency-Check informa.** Trivy
trae su propia base de datos, no necesita clave, es gratuito y sin límite de uso
—regla del proyecto—, y cubre los tres ecosistemas a la vez: Maven, npm del
frontend y npm del MCP. Bloquea en `CRITICAL,HIGH` con `--ignore-unfixed`: si
todavía no existe versión corregida, bloquear el PR no protege de nada.
Dependency-Check se queda como segunda opinión sobre otra base de datos y como
informe HTML publicado, sin bloquear.

**Tres. `npm audit` pasa a tener dos umbrales.** `--omit=dev --audit-level=moderate`
para el árbol de producción y `--audit-level=high` para el completo. Un aviso
moderado en algo que se ejecuta delante del usuario merece más atención que uno
alto en una herramienta que sólo corre en la máquina de quien desarrolla; con un
solo umbral alto había que elegir entre perder el primero o ahogarse en el
segundo.

**Cuatro. Semgrep y Gitleaks entran al CI como compuertas, y sus hallazgos
quedan en cero.** Un informe que siempre trae los mismos hallazgos deja de
leerse, y el día que aparezca el que importa nadie notará la diferencia. Para
que cero signifique algo:

- `.gitleaks.toml` permite los valores de laboratorio **por contenido, nunca por
  ruta**. Excluir `src/test/**` sería más corto y escondería una credencial real
  pegada por descuido en una prueba, que es uno de los sitios donde eso pasa.
- Los dos falsos positivos de Semgrep se silencian **por regla y en su línea**,
  con el motivo escrito al lado: el hash BCrypt señuelo de `AuthService` (que no
  es la contraseña de nadie y existe para igualar los tiempos de respuesta del
  login) y los actuadores `metrics`/`prometheus` (registrados, pero cerrados con
  JWT en `SecurityConfig`).

**Cinco. Las acciones del CI se fijan por SHA de commit.** `actions/checkout@v4`
es una etiqueta mutable: quien controle ese repositorio puede reapuntarla, y ese
código corre dentro del job con el árbol de SICOT en disco. Es la vía por la que
han entrado varios compromisos reales de cadena de suministro en GitHub Actions.
Dependabot mantiene los SHA al día con el ecosistema `github-actions`, y los
cuatro ecosistemas ganan un `cooldown` de siete días (catorce para saltos
mayores) para no adoptar una versión el mismo día que se publica.

## Consecuencias

**Lo que se gana.** Equivocarse al desplegar cuesta ahora un error al arrancar en
vez de una clave privada publicada firmando sesiones reales. Y hay una compuerta
que se puede señalar: el escaneo del 9 de septiembre pasó de 8 vulnerabilidades
(3 críticas) y 2 hallazgos de Dockerfile a **cero**, y Semgrep de 19 a **cero**,
con las cifras reproducibles por cualquiera con Docker.

**Lo que se pierde.** `java -jar` deja de funcionar sin configurar nada, que era
cómodo para una prueba rápida. Es el precio exacto de la decisión y es
deliberado. Y el CI tarda más: Trivy descarga su base de datos en cada corrida.

**Lo que queda prohibido.** Volver a poner `dev` como respaldo del perfil activo,
y añadir un valor por defecto para `JWT_SECRET` en cualquier archivo que no sea
`application-dev.properties` o los de prueba —incluido `docker-compose.yml`,
donde un `${JWT_SECRET:-}` bastaría para anular la protección, porque una
variable definida pero vacía también desactiva el respaldo de Spring—. Permitir
en `.gitleaks.toml` por ruta en vez de por contenido. Y silenciar un hallazgo de
Semgrep sin escribir al lado por qué no aplica.

## Cuándo revisar

- **Si el equipo crece o el despliegue deja de ser un solo host.** Con varios
  entornos, la protección deja de ser "el perfil por defecto" y pasa a ser la
  configuración del orquestador; conviene revisar si esta decisión sigue siendo
  el mecanismo adecuado o sólo una red de seguridad heredada.
- **Si Trivy empieza a fallar por causas ajenas al código** (su base de datos, un
  cambio de licencia de la imagen), habrá que elegir otra compuerta antes de
  quitarla: la lección de este ADR es que un análisis que no bloquea no es una
  compuerta, y quedarse sin ninguna fue el estado del que se venía.
- **Si alguna dependencia adelantada en el `pom.xml`** —`postgresql`, `tomcat`,
  `jackson-bom`, `commons-lang3`, `log4j2`— pasa a estar gestionada por el parent
  en esa versión o superior. Entonces esa línea sobra y debe retirarse, para que
  el `pom` no acumule anclas de versiones viejas.
