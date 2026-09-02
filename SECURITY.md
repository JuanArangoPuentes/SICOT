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
