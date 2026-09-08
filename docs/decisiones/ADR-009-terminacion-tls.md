# ADR-009 — Dónde termina TLS y por qué hoy no hay HTTPS

**Estado:** Aceptada · **Fecha:** 8 de septiembre de 2026

## Contexto

SICOT no sirve HTTPS en ningún entorno. El frontend se publica por HTTP en el
puerto 8443 —un número que sugiere TLS y que no lo tiene—, y
`docker-compose.prod.yml` no monta certificados ni termina TLS en ninguna parte.

Las consecuencias son concretas, no teóricas:

- El JWT vive en `localStorage` (`frontend/src/services/session.ts`) y viaja en
  cada petición. Sobre HTTP, cualquiera en la misma red del centro lo lee y
  suplanta a esa persona durante las ocho horas de validez del token.
- Las credenciales del formulario de acceso viajan igual.
- `SecurityConfig` emite `Strict-Transport-Security` con un año de validez. Un
  navegador **ignora esa cabecera cuando llega por HTTP**, así que hoy es
  trabajo hecho que no protege nada.

Esto estaba anotado como pendiente en `backend/README.md`, pero un pendiente no
es una decisión: hay tres formas razonables de resolverlo y ninguna se había
elegido, así que ninguna avanzaba.

| Opción | Dónde termina TLS | Qué implica |
| --- | --- | --- |
| **A. En el nginx del frontend** | En el mismo contenedor que ya sirve la SPA | Un certificado montado como volumen y su renovación a mano |
| **B. Proxy inverso delante del stack** | Un contenedor nuevo | Un servicio más; renovación automática con ACME |
| **C. En la infraestructura del SENA** | Fuera de SICOT | Cero cambios aquí; depende de un equipo que no es este |

## Decisión

Se adopta la **opción B: un proxy inverso delante del stack**, y se deja
**preparada** la opción C.

Concretamente:

1. El proxy inverso pasa a ser el único servicio que publica puertos al
   exterior. El frontend y el backend dejan de publicarlos y solo se alcanzan
   por la red interna de Docker, igual que ya hace PostgreSQL en el perfil de
   producción.
2. Se usa **Caddy**, por dos motivos: obtiene y renueva certificados de ACME sin
   configuración adicional, y es un binario único sin dependencias. Es software
   libre bajo Apache 2.0, dentro de la regla de herramientas gratuitas del
   proyecto.
3. Para un despliegue **interno sin dominio público** —el caso más probable en el
   centro— Caddy emite un certificado local. Cifra igual; el navegador pide
   confiar en él una vez.
4. La opción **C queda pre-aprobada**: si el SENA termina TLS en su propia
   infraestructura, se retira el proxy y se apunta el suyo al frontend. Nada más
   cambia.

**Por qué no A.** Meter la terminación TLS en el nginx que sirve la SPA mezcla
dos responsabilidades en un contenedor que existe por otra razón, y obliga a
renovar a mano. La primera vez que un certificado caduque en sábado, nadie lo va
a renovar hasta el lunes.

## Consecuencias

**Lo que se gana.** El token deja de viajar en claro. `Strict-Transport-Security`
empieza a hacer lo que dice. Y un despliegue accesible desde fuera del centro
deja de ser imprudente.

**Lo que se pierde.** Un contenedor más que operar y actualizar. Es el mismo
costo que el proyecto ya asumió por Adminer y por el propio PostgreSQL.

**Lo que hay que hacer al desplegar.** Fijar `SICOT_DOMINIO` en el `.env`. Con un
dominio público, Caddy resuelve el certificado solo; con un nombre interno, emite
uno local.

**Lo que queda prohibido.** Publicar el puerto del backend o del frontend
directamente al exterior una vez exista el proxy. Sería un camino paralelo sin
cifrar hacia los mismos datos, y ese camino nadie lo vigila.

## Cuándo revisar

- Cuando el SENA confirme si su infraestructura ya termina TLS. Eso activa la
  opción C y simplifica el despliegue.
- Si aparece un requisito de autenticación institucional (SSO): dónde termina TLS
  condiciona dónde se integra.
