# ── Build stage ──────────────────────────────────────────────────────────────
# Imágenes base fijadas por versión exacta, no por rama ("22-alpine"). Una rama
# flotante cambia por debajo sin que nadie lo revise y, peor, puede dejar de
# moverse sin que nadie lo note: así quedó esta imagen en nginx 1.27, una rama
# cerrada que ya no recibía parches. Ahora una versión nueva llega como PR de
# Dependabot (ecosistema docker) y el CI escanea la imagen resultante con Trivy.
FROM node:22.23.4-alpine AS build

# Usuario no-root también aquí, no sólo en la imagen final. La etapa de build se
# descarta, así que el riesgo no es lo que quede publicado: es que este es el
# único punto de las dos imágenes donde se ejecuta código de terceros con
# permisos de escritura (los scripts `postinstall` de cualquier paquete del
# árbol de npm, que corren durante `npm ci`). Que eso pase como root da acceso
# de escritura a toda la imagen intermedia y al caché de capas de quien
# construya. Como usuario `node` no. Es también el hallazgo DS002 de Trivy.
#
# El directorio se crea y se cede ANTES de cambiar de usuario: `WORKDIR` lo
# crearía como root y después `npm ci` no podría escribir el node_modules.
RUN mkdir -p /build && chown node:node /build
WORKDIR /build
USER node

COPY --chown=node:node package.json package-lock.json ./
RUN npm ci

COPY --chown=node:node . .

# Vite inlines VITE_* variables at build time, so this has to be a build arg,
# not a runtime environment variable.
#
# VACÍO por omisión = mismo origen: la aplicación pide `/api/...` a la misma
# dirección por la que se abrió, y el nginx de esta imagen (o Caddy, en
# producción) la reenvía al backend (ADR-016). Antes el valor por omisión era
# `http://localhost:8080`: una imagen construida sin argumentos —un `docker
# build` a mano— le decía al navegador de OTRO equipo que la API estaba en su
# propia máquina, y la CSP `connect-src 'self'` la bloqueaba además, sin ningún
# error en el servidor.
ARG VITE_API_URL=
ENV VITE_API_URL=$VITE_API_URL
RUN npm run build

# ── Runtime stage ────────────────────────────────────────────────────────────
# Imagen "unprivileged": nginx corre como usuario no-root desde el arranque
# (mismo patrón que backend/Dockerfile). Funciona sin cambios porque ya
# escuchamos en el puerto 8443, no privilegiado.
#
# 1.30 es la rama estable mantenida. La 1.27 que había aquí se cerró en 2025 y
# su imagen dejó de reconstruirse, con nginx y Alpine incluidos: esta imagen
# sirve la SPA que guarda el JWT y seguía sin parches sin que nada lo señalara.
# 1.30.6 desde el 23-10-2026: la 1.30.5 traía nginx con tres CVE altas
# (CVE-2026-15176, desbordamiento de montículo; CVE-2026-93479, map con
# expresiones regulares; CVE-2026-97205, ngx_http_core_module) que el escaneo de
# imágenes del CI marcó, y la 1.30.6 las corrige.
#
# ── OJO CON EL NOMBRE DE LA IMAGEN (26-11-2026) ────────────────────────────
#
# Hasta hoy esto decía `nginxinc/nginx-unprivileged`. Ese repositorio de Docker
# Hub DEJÓ DE PUBLICAR el 19-10-2026: su última etiqueta estable es
# 1.30.6-alpine, la que estaba aquí. Las imágenes se movieron a
# `nginx/nginx-unprivileged`, que el 23-11-2026 ya publicaba 1.30.8 en estable
# y 1.31.10 en mainline.
#
# Eso es lo que nos dejó clavados en una imagen sin parches sin que nada lo
# dijera: Dependabot vigila la etiqueta del repositorio ANTIGUO, que ya no se
# mueve, así que nunca iba a proponer la 1.30.7. Un repositorio congelado y un
# repositorio al día se parecen mucho desde fuera — no hay aviso, solo deja de
# haber etiquetas nuevas.
#
# 1.30.8 desde el 26-11-2026, y con ella se cierran las NUEVE CVE de nginx que
# el escaneo del 20-11-2026 encontró en 1.30.6-r1, seis de ellas CRÍTICAS o
# altas: uso después de liberar en el proxy de correo (CVE-2026-113425), en
# HTTP/3 (-121810), en HTTP/2 (-123633) y en el resolutor (-123634); corrupción
# de memoria en el control de flujo de HTTP/2 (-123007); bucle infinito leyendo
# el cuerpo sobre TLS 1.3 (-118899); y -120967, -123640 y -125283. Las nueve
# tienen el mismo arreglo, y nginx.org/en/security_advisories.html dice en
# todas, literal: «Not vulnerable: 1.31.9+, 1.30.7+». Por eso la rama 1.31 NO
# era la salida: su última imagen publicada en `nginxinc` era 1.31.7, dentro
# del rango vulnerable («Vulnerable: …-1.31.8»).
#
# Se queda en la rama ESTABLE, que es lo que este servidor debe servir; la
# excepción fechada que estas CVE tenían en .trivyignore se fue con este
# cambio.
FROM nginx/nginx-unprivileged:1.30.8-alpine

# Los COPY necesitan escribir en rutas del sistema, así que se hacen como root y
# después se vuelve al usuario sin privilegios (101, el mismo que trae la imagen
# base) para que el contenedor NO arranque como root. Sin este segundo `USER`,
# el `USER root` de arriba sería el último y la imagen final correría como root
# —justo lo que la variante "unprivileged" viene a evitar.
USER root

# La imagen base trae libtiff 4.7.1-r0, con CVE-2026-4775 (HIGH): ejecución de
# código o caída por un desbordamiento de entero con signo. Alpine ya publicó
# 4.7.2-r0, así que este arreglo es gratis — no es el caso de Spring, donde la
# versión corregida está detrás de una versión mayor de pago en esfuerzo. Lo
# que no se puede es esperar a que nginx reconstruya su imagen: el escaneo de
# imágenes es un chequeo obligatorio y hasta entonces no entra ningún PR.
#
# Se sube SOLO ese paquete y no `apk upgrade` entero, a propósito: así el resto
# de la imagen sigue siendo exactamente la que publica nginx, que es lo que la
# etiqueta fijada promete, y el día que falle se sabrá por qué. Cuando la rama
# 1.30 traiga 4.7.2-r0 o posterior, esta línea sobra y se quita.
#
# libexpat, por lo mismo (CI del 18-10-2026): la base trae 2.8.5-r0, con
# CVE-2026-77214 (HIGH, lectura fuera del búfer al parsear XML), y Alpine ya
# publicó 2.9.0-r0. El backend lleva la misma línea. Y libx11, del mismo día:
# 1.8.13-r0 trae CVE-2026-88806 (HIGH, desbordamiento de montículo con un
# servidor X malicioso) y 1.8.14-r0 la corrige. Y brotli-libs (CI del
# 23-10-2026): 1.2.0-r1 trae CVE-2026-111497 (HIGH, escritura fuera de límites
# en el decodificador) y 1.2.1-r0 la corrige; el backend lleva la misma línea.
#
# Y curl (CI del 27-10-2026): la base trae 8.22.0-r0, con CVE-2026-92392 (HIGH,
# uso después de liberar al subir por HTTP/2), y Alpine ya publicó 8.23.0-r0.
# `libcurl` viene del mismo origen que `curl`, así que subir el paquete arregla
# los dos. Aquí sí y en el backend no: su base ya va por Alpine 3.24.3, que lo
# trae corregido; la de nginx sigue en 3.24.2.
#
# libexpat otra vez (CI del 05-11-2026): 2.9.0-r0 trae tres HIGH de denegación
# de servicio (CVE-2026-113351, -117610 y -118370) y 2.10.0-r0 las corrige. El
# escaneo se detuvo en el backend antes de llegar aquí; esta base fija la misma
# condición y quedaba igual de expuesta.
#
# Y musl y zlib (CI del 07-11-2026, PR #149): la base trae musl 1.2.6-r2, con
# CVE-2026-107891 (HIGH, ejecución de código por un desbordamiento de entero en
# regcomp), y zlib 1.3.2-r0, con CVE-2026-85091 (HIGH, desbordamiento de
# montículo en gz_vacate). Alpine ya publicó 1.2.6-r3 y 1.3.2-r1. `musl-utils`
# sale del mismo paquete fuente que `musl` y tiene que subir con él. El backend
# no las necesita: su base ya va por Alpine 3.24.3, que las trae corregidas.
#
# libxslt, del CI del 10-11-2026: la base trae 1.1.43-r3, con CVE-2026-87636
# (HIGH, confusión de tipos al procesar XML) y CVE-2026-9947 (HIGH, uso después
# de liberar), y Alpine ya publicó 1.1.43-r4. El escaneo no lo encontró en el
# backend, así que la línea va solo aquí.
#
# libxml2, del CI del 20-11-2026: la base traía 2.13.9-r2, con CVE-2026-86144
# (HIGH, divulgación de información, SSRF o denegación de servicio por un
# manejo indebido), y Alpine publicó 2.13.9-r3 en la rama v3.24.
#
# Y VUELVE A SUBIR, del CI del 27-11-2026. Con el mínimo en r3 la imagen se
# quedaba en r3, y ahí aparecieron SEIS CVE altas más: ejecución de código
# arbitrario por desbordamiento de búfer en el montículo (CVE-2026-86138), en
# la pila al formatear elementos (-86140) y en xmlXPtrEval (-86142);
# desbordamiento de entero en xmlURIEscapeStr (-86139); problemas de integridad
# por desbordamiento de entero en las retrollamadas de escritura (-86143); y
# desbordamiento de búfer en la utilidad xmlcatalog (CVE-2026-11979). Alpine
# corrige cinco en 2.13.9-r4 y la de xmlcatalog en r5, así que el mínimo va a
# r5 — comprobado contra el APKINDEX de v3.24/main, que hoy publica 2.13.9-r5.
#
# Y subir el mínimo es imprescindible, no basta con volver a construir: `apk
# add` con una condición que lo instalado YA CUMPLE no actualiza nada, que es
# exactamente por lo que r3 seguía ahí después del cambio de imagen base.
#
# El backend no la necesita: el bucle de Trivy recorre su imagen primero y la
# pasó, así que lo que tenga instalado ya está corregido.
#
# El `nginx` de esta imagen NO se parchea desde aquí, y ya no hace falta: las
# nueve CVE que tenía en 1.30.6-r1 se cerraron subiendo la imagen base a
# 1.30.8-alpine (el bloque del FROM, más arriba, cuenta por qué no se
# encontraba). Lo que sigue valiendo es que fijar `nginx>=…` por apk sería un
# error: traería el nginx de ALPINE en lugar del que compila la imagen oficial,
# con otro juego de módulos y otro nginx.conf, y esta imagen depende del
# entrypoint de nginx (/etc/nginx/templates, /docker-entrypoint.d/, el usuario
# 101). Sustituir piezas que la etiqueta fijada promete es justo lo que el
# párrafo de arriba dice que no se hace.
RUN apk add --no-cache "tiff>=4.7.2-r0" "libexpat>=2.10.0-r0" "libx11>=1.8.14-r0" "brotli-libs>=1.2.1-r0" \
    "curl>=8.23.0-r0" "musl>=1.2.6-r3" "musl-utils>=1.2.6-r3" "zlib>=1.3.2-r1" "libxslt>=1.1.43-r4" \
    "libxml2>=2.13.9-r5"

COPY --from=build /build/dist /usr/share/nginx/html
# A /etc/nginx/templates y NO a conf.d: el entrypoint de la imagen pasa por
# `envsubst` todo lo que haya en templates/ y deja el resultado en conf.d. Es lo
# que permite que la CSP cambie entre desarrollo y producción sin duplicar el
# archivo — ver la cabecera de nginx.conf.template.
COPY nginx.conf.template /etc/nginx/templates/default.conf.template
USER 101

# Orígenes que se AÑADEN a `connect-src 'self'` en la CSP.
#
# Vacío por defecto, que es el valor de producción: allí el proxy sirve la SPA y
# la API en el mismo origen (ADR-009) y no hace falta ninguna excepción. Es
# desarrollo quien la pide, desde docker-compose.yml.
#
# La variable tiene que estar DEFINIDA aunque esté vacía: `envsubst` solo
# sustituye las variables presentes en el entorno, y si faltara dejaría el texto
# literal "${CSP_CONNECT_SRC_EXTRA}" dentro de la cabecera CSP.
ENV CSP_CONNECT_SRC_EXTRA=""

# Limita la sustitución a las variables que empiezan por CSP_. Sin este filtro,
# `envsubst` recibiría la lista COMPLETA del entorno del contenedor y bastaría
# una variable llamada como una de nginx ($uri, $csp, $args…) para vaciarla en
# la configuración generada y romper el arranque de formas difíciles de rastrear.
ENV NGINX_ENVSUBST_FILTER="^CSP_"

EXPOSE 8443

# El healthcheck vive también aquí, no sólo en docker-compose.yml: así la imagen
# se comporta igual cuando se despliega con otra herramienta (docker run, Swarm,
# Kubernetes) que no lea ese archivo. Mismo criterio que backend/Dockerfile, y
# es el hallazgo DS026 de Trivy.
HEALTHCHECK --interval=15s --timeout=5s --start-period=10s --retries=5 \
    CMD wget -qO- http://127.0.0.1:8443/ || exit 1
