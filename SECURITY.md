# Política de seguridad

SICOT gestiona expedientes de contratación pública del Centro Tecnológico del
Mobiliario. Un fallo de seguridad aquí no expone datos de prueba: expone
información contractual real de una entidad del Estado.

## Reportar una vulnerabilidad

**No abra una incidencia pública.** Una incidencia en GitHub es visible para
cualquiera, incluido quien podría aprovechar el fallo antes de que exista la
corrección.

Escriba directamente a **jarangop8@soy.sena.edu.co** con:

- Qué encontró y qué permite hacer.
- Los pasos exactos para reproducirlo.
- La versión o el commit sobre el que lo probó.

**Compromiso de respuesta:** acuse de recibo en 5 días hábiles y una primera
valoración —si es válido, qué tan grave y cuándo se corrige— en 15 días
hábiles. Este proyecto lo sostiene un equipo muy pequeño; el plazo es realista,
no aspiracional.

## Alcance

Interesa especialmente cualquier fallo que permita:

- Ver o modificar contratos ajenos (un supervisor solo debe acceder al suyo).
- Alterar un documento ya firmado sin que la verificación de integridad lo
  detecte.
- Saltarse la autenticación o escalar privilegios entre los roles
  ADMINISTRADOR, GESTION y SUPERVISOR.
- Extraer datos a través del Copiloto de IA mediante inyección de instrucciones.

## Fuera de alcance

- **Las contraseñas de desarrollo** (`Admin123*` y similares) están publicadas
  a propósito en `backend/README.md`. Solo existen con el perfil `dev` o
  `test`: en cualquier otro perfil `DataInitializer` no siembra nada y
  `AdministradorInicial` exige credenciales por variable de entorno. No son una
  vulnerabilidad.
- Ataques de denegación por fuerza bruta contra la propia máquina de quien
  prueba.
- Vulnerabilidades de dependencias que ya reporta el análisis del CI y para las
  que aún no hay versión corregida publicada.

## Cómo se protege hoy

Documentado en detalle en [`docs/decisiones/`](docs/decisiones/):
autenticación JWT, autorización en dos capas, límite de intentos por cuenta y
por red, huella SHA-256 que ata cada firma a su documento, cabeceras de
seguridad y aislamiento entre supervisores con pruebas dedicadas.

## Qué comprueba el CI en cada PR

Cuatro compuertas, todas con herramientas libres, sin cuenta ni clave y sin
límite de uso. La decisión de cuál bloquea y cuál sólo informa está razonada en
[ADR-011](docs/decisiones/ADR-011-arranque-fail-closed-y-compuertas-de-seguridad.md).

| Herramienta | Qué mira | Bloquea |
| --- | --- | --- |
| **Trivy** | CVE en las dependencias de los tres ecosistemas (Maven, npm del frontend, npm del MCP) | Sí, en `CRITICAL` y `HIGH` con corrección publicada |
| **Semgrep** | Fallos de seguridad en el código propio (`security-audit`, `secrets`, `owasp-top-ten`) | Sí, cualquier hallazgo |
| **Gitleaks** | Credenciales en el árbol y en **todo el historial** de Git | Sí, cualquier hallazgo |
| **npm audit** | Dos umbrales: `moderate` en el árbol de producción, `high` en el completo | Sí |
| OWASP Dependency-Check | Segunda opinión sobre el backend con la base de la NVD | No — informa y publica el HTML |

Los tres primeros salen en **cero** hoy. Es el punto: un informe que siempre
trae los mismos hallazgos deja de leerse, y el día que aparezca el que importa
nadie notará la diferencia. Si añade una excepción, escriba al lado por qué no
aplica — en `.gitleaks.toml` se permite por contenido y nunca por ruta, y en
Semgrep se silencia por regla y en su línea, nunca por archivo.

## Alertas de Dependabot descartadas

Una alerta se descarta solo cuando se comprobó que no alcanza a nada de lo que
SICOT distribuye, y el motivo queda aquí con la condición que obliga a
reabrirla. Una alerta abierta sin acción posible enseña a ignorar las demás.

### Alerta 11 — `glib` 0.18.5 (GHSA-wrw7-89jp-8q8g), descartada el 23 de septiembre de 2026

**Qué dice.** Los iteradores de `glib::VariantStrIter` son inseguros en
`glib` < 0.20.0. Gravedad media.

**Por qué no se corrige.** `glib` entra solo por GTK, que Tauri usa para la
ventana en Linux. Ninguna versión de Tauri 2 permite subirlo: la última
comprobada (2.11.6) y sus piezas (`tao` 0.37, `wry` 0.57, `muda` 0.20,
`tray-icon` 0.25) piden `gtk` ^0.18, y `webkit2gtk` 2.0.2 pide `glib` ^0.18.
Forzar la 0.20 con un `[patch]` de Cargo rompería la compilación para Linux.
Dependabot, mientras tanto, intentaba cada día un arreglo que no existe y
dejaba un run rojo sobre `master`.

**Por qué no afecta a SICOT.**

- `glib` no entra en ninguno de los dos artefactos que se publican: el
  instalador de Windows (`.exe` y `.msi`) y el APK de Android.
  `cargo tree -i glib` no encuentra nada para `x86_64-pc-windows-msvc` ni para
  `aarch64-linux-android`; solo aparece para `x86_64-unknown-linux-gnu`.
- Ningún flujo del CI ni ninguna versión publicada trae una compilación para
  Linux.
- El código propio de la app (`frontend/src-tauri/src`) no usa `glib`.

**Cuándo hay que reabrirla.**

- Si SICOT empieza a distribuir una versión de escritorio para Linux.
- O si Tauri pasa a GTK 0.20 o posterior. En ese caso se sube Tauri y la
  alerta se cierra por corrección, que es lo que debió pasar desde el
  principio.

Para comprobarlo de nuevo, desde `frontend/src-tauri`:

```bash
cargo tree --target x86_64-pc-windows-msvc -i glib   # debe decir «nothing to print»
cargo tree --target aarch64-linux-android -i glib    # debe decir «nothing to print»
```

## Reproducir las compuertas en local

Gitleaks y Trivy se reproducen en local con sólo Docker:

```bash
docker run --rm -v "$PWD:/repo" zricethezav/gitleaks:v8.30.1 git /repo --redact
docker run --rm -v "$PWD:/proyecto" -v "$HOME/.m2:/root/.m2" aquasec/trivy:0.74.0 \
  fs --scanners vuln --severity CRITICAL,HIGH --skip-dirs node_modules --skip-dirs target /proyecto
```
