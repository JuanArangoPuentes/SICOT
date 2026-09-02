# ADR-005 — Dónde viven las credenciales y cómo se rotan

**Estado:** Aceptada · **Fecha:** 1 de septiembre de 2026

## Contexto

Todas las credenciales de SICOT —contraseña de PostgreSQL, secreto de firma JWT,
usuario y clave del correo saliente, credenciales del administrador inicial—
viven en un archivo `.env` en el host de despliegue.

Lo que **ya está bien** y no se toca: `.env` nunca se ha subido al repositorio
(verificado sobre todo el historial), `JWT_SECRET` no tiene valor de respaldo
fuera del perfil de desarrollo, y el archivo de producción exige que las
variables críticas existan o se niega a arrancar.

Lo que faltaba era la parte **operativa**: no había ningún procedimiento escrito
para rotar una credencial, ni una respuesta a "qué hacemos si esto se filtra".
Con un equipo pequeño y rotativo, un procedimiento que solo existe en la cabeza
de alguien es un procedimiento que no existe.

## Decisión

**Se mantiene `.env` como almacén de secretos** para el despliegue actual de un
solo host, y se documenta el procedimiento de rotación que faltaba.

Se descarta introducir un gestor de secretos dedicado (Vault, SOPS, secretos de
Docker Swarm) **por ahora**, por proporción: añaden un componente que instalar,
respaldar y aprender, para proteger un archivo que ya está fuera del repositorio
en una máquina a la que solo accede el equipo. Para tres personas y un host, el
gestor sería más superficie de fallo que protección.

Lo que sí se exige:

1. **Permisos del archivo**: `chmod 600 .env` — legible solo por su dueño.
2. **Procedimiento de rotación escrito**, en
   `docs/operacion/GESTION_DE_SECRETOS.md`, con el efecto de rotar cada
   credencial (rotar `JWT_SECRET` cierra todas las sesiones abiertas; rotar
   `DB_PASSWORD` exige cambiarla también dentro de PostgreSQL).
3. **Rotación obligatoria** al salir cualquier persona del equipo con acceso al
   host.

## Consecuencias

**Lo que se gana.** Un procedimiento que alguien nuevo puede ejecutar sin
adivinar, y una regla clara para el relevo de personas — que en este equipo
ocurre por diseño.

**Lo que se pierde.** No hay historial de acceso a los secretos ni rotación
automática. Con un solo host y un equipo pequeño, es un costo asumible.

**El disparador que cambia todo esto.** Un segundo entorno. En el momento en que
existan producción y preproducción a la vez, `.env` deja de escalar: dos copias
manuales del mismo secreto divergen, y nadie sabe cuál es la buena.

## Cuándo revisar

- Al aparecer un segundo entorno (preproducción, o un segundo centro).
- Si el equipo supera las cinco personas con acceso al host.
- Ante cualquier filtración, real o sospechada: eso obliga a rotar todo y a
  reabrir esta decisión.
