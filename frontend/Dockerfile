# ── Build stage ──────────────────────────────────────────────────────────────
FROM node:22-alpine AS build

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
ARG VITE_API_URL=http://localhost:8080
ENV VITE_API_URL=$VITE_API_URL
RUN npm run build

# ── Runtime stage ────────────────────────────────────────────────────────────
# Imagen "unprivileged": nginx corre como usuario no-root desde el arranque
# (mismo patrón que backend/Dockerfile). Funciona sin cambios porque ya
# escuchamos en el puerto 8443, no privilegiado.
FROM nginxinc/nginx-unprivileged:1.27-alpine

# Los COPY necesitan escribir en rutas del sistema, así que se hacen como root y
# después se vuelve al usuario sin privilegios (101, el mismo que trae la imagen
# base) para que el contenedor NO arranque como root. Sin este segundo `USER`,
# el `USER root` de arriba sería el último y la imagen final correría como root
# —justo lo que la variante "unprivileged" viene a evitar.
USER root
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
